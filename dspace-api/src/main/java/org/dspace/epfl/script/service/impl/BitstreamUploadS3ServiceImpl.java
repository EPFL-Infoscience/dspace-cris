/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.epfl.script.service.BitstreamUploadS3Service;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class BitstreamUploadS3ServiceImpl implements BitstreamUploadS3Service {

    private static final Logger LOGGER = LogManager.getLogger(BitstreamUploadS3ServiceImpl.class);

    @Autowired
    private ConfigurationService configurationService;

    private S3Client s3Service = null;

    @PostConstruct
    private void setup() {
        var builder = S3Client.builder()
            .region(getAwsRegion());

        String accessKey = getAwsAccessKey();
        String secretKey = getAwsSecretKey();

        if (StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey)) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                )
            );
        }

        s3Service = builder.build();
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
            s3Service.putObject(
                PutObjectRequest.builder().bucket(bucketName).key(key).build(),
                scratchFile.toPath()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            scratchFile.delete();
        }
    }

    private boolean isObjectAlreadyPresent(String key, String bucketName) {
        try {
            s3Service.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private File createTempFile(String name, InputStream source) {
        try {
            File scratchFile = File.createTempFile(name, "s3");
            scratchFile.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(scratchFile)) {
                IOUtils.copy(source, fos);
            }
            return scratchFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Region getAwsRegion() {
        Region region = Region.US_EAST_1;
        String awsRegionName = getAwsAccessRegion();
        if (StringUtils.isNotBlank(awsRegionName)) {
            try {
                region = Region.of(awsRegionName);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid aws_region: " + awsRegionName);
            }
        }
        return region;
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
