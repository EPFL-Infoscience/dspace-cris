/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.client;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.PostConstruct;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

public class EpflItemsClientImpl implements EpflItemsClient {

    private static final Logger LOGGER = LogManager.getLogger(EpflItemsClientImpl.class);

    @Autowired
    private ConfigurationService configurationService;

    private AmazonS3 s3Service = null;

    @PostConstruct
    private void setup() {

        BasicAWSCredentials credentials = new BasicAWSCredentials(getAwsAccessKey(), getAwsSecretKey());

        s3Service = AmazonS3ClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .withRegion(getAwsRegion())
            .build();

    }

    @Override
    public Iterator<S3ObjectSummary> iterateObjects() {
        return S3Objects.inBucket(s3Service, getBucketName()).iterator();
    }

    @Override
    public List<S3ObjectSummary> getObjects(Integer limit, String startAfter) {

        Assert.notNull(limit, "The limit is mandatory");

        String bucketName = getBucketName();

        List<S3ObjectSummary> objects = new ArrayList<S3ObjectSummary>();

        String continuationToken = null;

        do {

            ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request();
            listObjectsV2Request.setContinuationToken(continuationToken);
            listObjectsV2Request.setBucketName(bucketName);
            listObjectsV2Request.setMaxKeys(limit - objects.size());

            if (StringUtils.isNotBlank(startAfter)) {
                listObjectsV2Request.setStartAfter(startAfter);
            }

            ListObjectsV2Result result = s3Service.listObjectsV2(listObjectsV2Request);

            objects.addAll(result.getObjectSummaries());

            continuationToken = result.getNextContinuationToken();

        } while (isNotBlank(continuationToken) && objects.size() < limit);

        return objects;
    }

    @Override
    public InputStream get(String key) {
        S3Object s3Object = s3Service.getObject(getBucketName(), key);
        return s3Object.getObjectContent().getDelegateStream();
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
