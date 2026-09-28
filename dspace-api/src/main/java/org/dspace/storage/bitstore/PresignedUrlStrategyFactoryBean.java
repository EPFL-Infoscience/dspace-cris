/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.FactoryBean;

/**
 * Factory bean that creates the appropriate {@link PresignedUrlStrategy} based on configuration.
 * If the CloudFront distribution domain is configured, a {@link CloudFrontPresignedUrlStrategy}
 * is created; otherwise, falls back to {@link S3PresignedUrlStrategy}.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public class PresignedUrlStrategyFactoryBean implements FactoryBean<PresignedUrlStrategy> {

    private static final Logger log = LogManager.getLogger(PresignedUrlStrategyFactoryBean.class);

    private String distributionDomain;
    private String keyPairId;
    private String privateKeyPath;
    private AWSS3ClientBuilder s3ClientBuilder;

    @Override
    public PresignedUrlStrategy getObject() throws Exception {
        if (StringUtils.isNotBlank(distributionDomain)) {
            log.info("CloudFront distribution domain configured, using CloudFrontPresignedUrlStrategy");
            return new CloudFrontPresignedUrlStrategy(keyPairId, privateKeyPath, distributionDomain);
        }
        log.info("CloudFront distribution domain not configured, using S3PresignedUrlStrategy as fallback");
        return new S3PresignedUrlStrategy(s3ClientBuilder);
    }

    @Override
    public Class<?> getObjectType() {
        return PresignedUrlStrategy.class;
    }


    public void setDistributionDomain(String distributionDomain) {
        this.distributionDomain = distributionDomain;
    }

    public void setKeyPairId(String keyPairId) {
        this.keyPairId = keyPairId;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public void setS3ClientBuilder(AWSS3ClientBuilder s3ClientBuilder) {
        this.s3ClientBuilder = s3ClientBuilder;
    }
}
