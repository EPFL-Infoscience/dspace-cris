/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;

/**
 * Generates presigned URLs using CloudFront signed URL functionality.
 * Requires a CloudFront distribution, key pair ID, and private key.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public class CloudFrontPresignedUrlStrategy implements PresignedUrlStrategy {

    private static final Logger log = LogManager.getLogger(CloudFrontPresignedUrlStrategy.class);

    private final String keyPairId;
    private final String privateKeyPath;
    private final String distributionDomain;

    /**
     * Constructs a new CloudFrontPresignedUrlStrategy.
     *
     * @param keyPairId          the CloudFront key pair ID
     * @param privateKeyPath     the path to the CloudFront private key file
     * @param distributionDomain the CloudFront distribution domain
     */
    public CloudFrontPresignedUrlStrategy(String keyPairId,
                                          String privateKeyPath,
                                          String distributionDomain) {
        if (StringUtils.isBlank(keyPairId)) {
            throw new IllegalArgumentException("keyPairId must not be blank");
        }
        if (StringUtils.isBlank(privateKeyPath)) {
            throw new IllegalArgumentException("privateKeyPath must not be blank");
        }
        if (StringUtils.isBlank(distributionDomain)) {
            throw new IllegalArgumentException("distributionDomain must not be blank");
        }
        try {
            URI.create(distributionDomain).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid distributionDomain URL: " + distributionDomain, e);
        }
        this.keyPairId = keyPairId;
        this.privateKeyPath = privateKeyPath;
        this.distributionDomain = distributionDomain;
    }

    @Override
    public String generatePresignedUrl(String bucketName, String objectKey, Duration expiration,
                                        String contentType, String contentDisposition) throws IOException {
        CloudFrontUtilities cloudFrontUtilities = CloudFrontUtilities.create();
        try {
            String resourceUrl = cloudfrontResource(objectKey, contentType, contentDisposition);

            CannedSignerRequest request =
                CannedSignerRequest.builder()
                                   .resourceUrl(resourceUrl)
                                   .privateKey(Paths.get(privateKeyPath))
                                   .keyPairId(keyPairId)
                                   .expirationDate(Instant.now().plus(expiration))
                                   .build();

            String presignedUrl = cloudFrontUtilities.getSignedUrlWithCannedPolicy(request).url();

            if (log.isDebugEnabled()) {
                log.debug("Generated CloudFront presigned URL for key {}: {}", objectKey, presignedUrl);
            }

            return presignedUrl;
        } catch (SdkClientException e) {
            log.error("Error generating CloudFront presigned URL for key: {}", objectKey, e);
            throw new IOException("Failed to generate CloudFront presigned URL", e);
        } catch (Exception e) {
            log.error("Error processing CloudFront signed URL for key: {}", objectKey, e);
            throw new IOException("Failed to generate CloudFront presigned URL", e);
        }
    }

    /**
     * Builds the CloudFront resource URL, optionally appending S3 response override
     * query parameters for content-type and content-disposition. These query params
     * are signed as part of the URL so that when CloudFront forwards the request to
     * the S3 origin, the correct response headers are returned to the client.
     *
     * @param key                 the S3 object key
     * @param contentType         content-type override (nullable)
     * @param contentDisposition  content-disposition override (nullable)
     * @return the resource URL with optional query parameters
     */
    private String cloudfrontResource(String key, String contentType, String contentDisposition) {
        try {
            URI uri;
            if (distributionDomain.endsWith("/")) {
                uri = URI.create(distributionDomain);
            } else {
                uri = URI.create(distributionDomain + "/");
            }
            String baseUrl = uri.resolve(key).toURL().toString();

            // Append S3 response override query parameters that CloudFront will
            // forward to the S3 origin. These become part of the CloudFront signature.
            StringBuilder queryBuilder = new StringBuilder();
            if (StringUtils.isNotBlank(contentType)) {
                queryBuilder.append("response-content-type=")
                            .append(URLEncoder.encode(contentType, "UTF-8"));
            }
            if (StringUtils.isNotBlank(contentDisposition)) {
                if (queryBuilder.length() > 0) {
                    queryBuilder.append("&");
                }
                queryBuilder.append("response-content-disposition=")
                            .append(URLEncoder.encode(contentDisposition, "UTF-8"));
            }

            if (queryBuilder.length() > 0) {
                return baseUrl + "?" + queryBuilder.toString();
            }
            return baseUrl;
        } catch (MalformedURLException | UnsupportedEncodingException e) {
            log.error("Malformed distribution domain URL: {}", distributionDomain, e);
            throw new IllegalStateException("Invalid distribution domain URL", e);
        }
    }
}
