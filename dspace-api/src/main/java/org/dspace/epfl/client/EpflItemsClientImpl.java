/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.client;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.services.ConfigurationService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

public class EpflItemsClientImpl implements EpflItemsClient {

    private static final Logger LOGGER = LogManager.getLogger(EpflItemsClientImpl.class);

    @Autowired
    private ConfigurationService configurationService;

    private S3Client s3Service = null;

    @PostConstruct
    private void setup() {
        var builder = S3Client.builder()
            .region(getAwsRegion());

        String accessKey = getAwsAccessKey();
        String secretKey = getAwsSecretKey();

        if (isNotBlank(accessKey) && isNotBlank(secretKey)) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                )
            );
        }

        s3Service = builder.build();
    }

    @Override
    public Iterator<S3Object> iterateObjects() {
        return s3Service.listObjectsV2Paginator(r -> r.bucket(getBucketName()))
            .contents()
            .iterator();
    }

    @Override
    public List<S3Object> getObjects(Integer limit, String startAfter) {
        if (limit == null) {
            limit = 200000;
        }

        String bucketName = getBucketName();
        List<S3Object> objects = new ArrayList<>();
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .maxKeys(limit - objects.size())
                .continuationToken(continuationToken);

            if (StringUtils.isNotBlank(startAfter)) {
                requestBuilder.startAfter(startAfter);
            }

            ListObjectsV2Response result = s3Service.listObjectsV2(requestBuilder.build());

            objects.addAll(result.contents());
            continuationToken = result.nextContinuationToken();

        } while (isNotBlank(continuationToken) && objects.size() < limit);

        return objects;
    }

    @Override
    public File get(String key) {
        try {
            File tempFile = Files.createTempFile(key, ".temp").toFile();
            s3Service.getObject(
                GetObjectRequest.builder().bucket(getBucketName()).key(key).build(),
                ResponseTransformer.toFile(tempFile.toPath())
            );
            return tempFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getCreationDate(String id) {
        String key = getCreationDateDirectory() + File.separator + id + ".json";
        return getCreationDateByKey(key);
    }

    @Override
    public String getCreationDateByKey(String key) {
        try (InputStream content = getCreationDateObject(getCreationDateBucketName(), key)) {
            JSONArray json = parseJson(content);
            return ((JSONObject) json.get(0)).getString(getCreationDateField());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Iterator<S3Object> iterateCreationDate() {
        return s3Service.listObjectsV2Paginator(r -> r.bucket(getCreationDateBucketName()))
            .contents()
            .iterator();
    }

    private InputStream getCreationDateObject(String bucketName, String key) {
        try {
            return s3Service.getObject(
                GetObjectRequest.builder().bucket(bucketName).key(key).build()
            );
        } catch (NoSuchKeyException ex) {
            throw new IllegalArgumentException("No creation date object found by key " + key);
        }
    }

    private JSONArray parseJson(InputStream inputStream) {
        try {
            return new JSONArray(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
        } catch (JSONException | IOException e) {
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

    private String getCreationDateBucketName() {
        return configurationService.getProperty("epfl.items-import.creation-date-aws.bucket");
    }

    private String getCreationDateDirectory() {
        return configurationService.getProperty("epfl.items-import.creation-date-aws.directory");
    }

    private String getCreationDateField() {
        return configurationService.getProperty("epfl.items-import.creation-date-aws.field");
    }

    private String getBucketName() {
        return configurationService.getProperty("epfl.items-import.source-aws.bucket");
    }

    private String getAwsAccessKey() {
        return configurationService.getProperty("epfl.items-import.source-aws.key");
    }

    private String getAwsAccessRegion() {
        return configurationService.getProperty("epfl.items-import.source-aws.region");
    }

    private String getAwsSecretKey() {
        return configurationService.getProperty("epfl.items-import.source-aws.secret-key");
    }
}
