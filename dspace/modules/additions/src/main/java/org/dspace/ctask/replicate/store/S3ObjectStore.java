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

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
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

    private S3Client s3Service = null;

    private String bucketName;

    @Override
    public void init() throws IOException {
        s3Service = initializeS3Client();

        bucketName = configurationService.getProperty("replicate.s3.bucket-name");

        if (StringUtils.isBlank(bucketName)) {
            throw new IllegalStateException("No S3 bucket configured");
        }

        if (!bucketExists(bucketName)) {
            s3Service.createBucket(r -> r.bucket(bucketName));
        }
    }

    private boolean bucketExists(String bucketName) {
        try {
            s3Service.headBucket(r -> r.bucket(bucketName));
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    @Override
    public boolean objectExists(String group, String id) throws IOException {
        try {
            s3Service.headObject(HeadObjectRequest.builder()
                .bucket(bucketName).key(getKey(id, group)).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
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

        long contentLength = s3Service.headObject(HeadObjectRequest.builder()
            .bucket(bucketName).key(getKey(id, group)).build())
            .contentLength();

        return String.valueOf(contentLength);
    }

    @Override
    public long fetchObject(String group, String id, File file) throws IOException {
        s3Service.getObject(
            GetObjectRequest.builder()
                .bucket(bucketName).key(getKey(id, group)).build(),
            ResponseTransformer.toFile(file.toPath())
        );
        return file.length();
    }

    @Override
    public long transferObject(String group, File file) throws IOException {
        String fileName = file.getName();
        String key = isNotBlank(group) ? group + File.pathSeparator + fileName : fileName;
        s3Service.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName).key(key).build(),
            file.toPath()
        );
        return file.length();
    }

    @Override
    public long removeObject(String group, String id) throws IOException {
        long size = getFileSize(group, id);
        s3Service.deleteObject(r -> r.bucket(bucketName).key(getKey(id, group)));
        return size;
    }

    @Override
    public long moveObject(String srcgroup, String destGroup, String id) throws IOException {
        long fileSize = getFileSize(srcgroup, id);
        String key = getKey(id, srcgroup);
        String destKey = getKey(id, destGroup);
        s3Service.copyObject(r -> r.sourceBucket(bucketName).sourceKey(key)
            .destinationBucket(bucketName).destinationKey(destKey));
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

    private S3Client initializeS3Client() {
        String awsAccessKey = configurationService.getProperty("replicate.s3.access-key");
        String awsSecretKey = configurationService.getProperty("replicate.s3.secret-key");
        String awsRegionName = configurationService.getProperty("replicate.s3.region-name");

        if (StringUtils.isNotBlank(awsAccessKey) && StringUtils.isNotBlank(awsSecretKey)) {
            AwsCredentialsProvider credentialsProvider =
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(awsAccessKey, awsSecretKey)
                );

            Region region = Region.US_EAST_1;
            if (StringUtils.isNotBlank(awsRegionName)) {
                try {
                    region = Region.of(awsRegionName);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid aws_region: " + awsRegionName);
                }
            }

            return S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .build();
        }

        return S3Client.create();
    }

}
