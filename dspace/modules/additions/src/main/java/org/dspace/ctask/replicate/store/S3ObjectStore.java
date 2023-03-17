/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate.store;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.io.IOException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.ctask.replicate.ObjectStore;
import org.dspace.curate.Utils;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Implementation of {@link ObjectStore} with Amazon S3.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class S3ObjectStore implements ObjectStore {

    private static final Logger log = LogManager.getLogger(S3ObjectStore.class);

    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    private AmazonS3 s3Service = null;

    private TransferManager transferManager = null;

    private String bucketName;

    @Override
    public void init() throws IOException {

        s3Service = initializeAmazonS3();

        transferManager = TransferManagerBuilder.standard()
            .withAlwaysCalculateMultipartMd5(true)
            .withS3Client(s3Service)
            .build();

        bucketName = configurationService.getProperty("replicate.s3.bucket-name");

        if (StringUtils.isBlank(bucketName)) {
            throw new IllegalStateException("No S3 bucket configured");
        }

        if (!s3Service.doesBucketExistV2(bucketName)) {
            s3Service.createBucket(bucketName);
        }

    }

    @Override
    public boolean objectExists(String group, String id) throws IOException {
        return s3Service.doesObjectExist(bucketName, getKey(id, group));
    }

    @Override
    public String objectAttribute(String group, String id, String attrName) throws IOException {

        if (StringUtils.isBlank(attrName) || !objectExists(group, id)) {
            return null;
        }

        if ("checksum".equals(attrName)) {
            return calculateChecksum(group, id);
        }

        if (!"sizebytes".equals(attrName)) {
            return null;
        }

        ObjectMetadata objectMetadata = s3Service.getObjectMetadata(bucketName, getKey(id, group));
        if (objectMetadata == null) {
            return null;
        }

        return String.valueOf(objectMetadata.getContentLength());

    }

    @Override
    public long fetchObject(String group, String id, File file) throws IOException {

        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, getKey(id, group));

        Download download = transferManager.download(getObjectRequest, file);
        try {
            download.waitForCompletion();
        } catch (AmazonClientException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        return file.length();
    }

    @Override
    public long transferObject(String group, File file) throws IOException {
        String fileName = file.getName();
        String key = isNotBlank(group) ? group + File.pathSeparator + fileName : fileName;
        transferManager.upload(bucketName, key, file);
        return file.length();
    }

    @Override
    public long removeObject(String group, String id) throws IOException {
        long size = getFileSize(group, id);
        s3Service.deleteObject(bucketName, getKey(id, group));
        return size;
    }

    @Override
    public long moveObject(String srcgroup, String destGroup, String id) throws IOException {

        long fileSize = getFileSize(srcgroup, id);

        s3Service.copyObject(bucketName, getKey(id, srcgroup), bucketName, getKey(id, destGroup));

        return fileSize;
    }

    private String calculateChecksum(String group, String id) throws IOException {

        File tempFile = File.createTempFile("s3-checksum-calculation-", "temp");
        tempFile.deleteOnExit();

        try {
            fetchObject(group, id, tempFile);
            return Utils.checksum(tempFile, "MD5");
        } finally {
            tempFile.delete();
        }

    }

    private long getFileSize(String group, String id) throws IOException {
        String size = objectAttribute(group, id, "sizebytes");
        try {
            return size != null ? Long.valueOf(size) : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String getKey(String id, String group) {
        return isNotBlank(group) ? group + File.pathSeparator + id : id;
    }

    private AmazonS3 initializeAmazonS3() {

        AmazonS3 amazonS3 = null;

        String awsAccessKey = configurationService.getProperty("replicate.s3.access-key");
        String awsSecretKey = configurationService.getProperty("replicate.s3.secret-key");
        String awsRegionName = configurationService.getProperty("replicate.s3.region-name");

        if (StringUtils.isNotBlank(awsAccessKey) && StringUtils.isNotBlank(awsSecretKey)) {

            Regions regions = Regions.DEFAULT_REGION;
            if (StringUtils.isNotBlank(awsRegionName)) {
                try {
                    regions = Regions.fromName(awsRegionName);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid aws_region: " + awsRegionName);
                }
            }

            amazonS3 = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsAccessKey, awsSecretKey)))
                .withRegion(regions)
                .build();

        } else {
            amazonS3 = AmazonS3ClientBuilder.defaultClient();
        }

        return amazonS3;
    }

}
