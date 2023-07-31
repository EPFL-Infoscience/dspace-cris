/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.PostConstruct;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.util.IOUtils;
import org.dspace.epfl.script.service.BitstreamUploadS3Service;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

public class BitstreamUploadS3ServiceImpl implements BitstreamUploadS3Service {

    private static final Logger LOGGER = LogManager.getLogger(BitstreamUploadS3ServiceImpl.class);

    @Autowired
    private ConfigurationService configurationService;

    private TransferManager transferManager = null;

    @PostConstruct
    private void setup() {

        BasicAWSCredentials credentials = new BasicAWSCredentials(getAwsAccessKey(), getAwsSecretKey());

        AmazonS3 s3Service = AmazonS3ClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .withRegion(getAwsRegion())
            .build();

        transferManager = TransferManagerBuilder.standard()
            .withAlwaysCalculateMultipartMd5(true)
            .withS3Client(s3Service)
            .build();

    }

    @Override
    public void upload(InputStream source, String name) {

        String key = getAwsDirectory() + File.separator + name;
        String bucketName = getBucketName();

        if (isObjectAlreadyPresent(key, bucketName)) {
            return;
        }

        File scratchFile = createTempFile(name, source);

        try {

            Upload upload = transferManager.upload(bucketName, key, scratchFile);
            upload.waitForUploadResult();

        } catch (AmazonClientException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            scratchFile.delete();
        }

    }

    private boolean isObjectAlreadyPresent(String key, String bucketName) {
        return transferManager.getAmazonS3Client().doesObjectExist(bucketName, key);
    }

    private File createTempFile(String name, InputStream source) {
        try {
            File scratchFile = File.createTempFile(name, "s3");
            scratchFile.deleteOnExit();
            IOUtils.copy(source, scratchFile);
            return scratchFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Regions getAwsRegion() {
        Regions regions = Regions.DEFAULT_REGION;
        String awsRegionName = getAwsAccessRegion();
        if (StringUtils.isNotBlank(awsRegionName)) {
            try {
                regions = Regions.fromName(awsRegionName);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid aws_region: " + awsRegionName);
            }
        }
        return regions;
    }

    private String getBucketName() {
        return configurationService.getProperty("epfl.items-import.upload-aws.bucket");
    }

    private String getAwsAccessKey() {
        return configurationService.getProperty("epfl.items-import.upload-aws.key");
    }

    private String getAwsAccessRegion() {
        return configurationService.getProperty("epfl.items-import.upload-aws.region");
    }

    private String getAwsSecretKey() {
        return configurationService.getProperty("epfl.items-import.upload-aws.secret-key");
    }

    private String getAwsDirectory() {
        return configurationService.getProperty("epfl.items-import.upload-aws.directory");
    }

}
