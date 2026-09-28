/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Generates presigned URLs using direct S3 presigned URL functionality.
 * This is the default strategy and does not require any additional
 * CloudFront configuration.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public class S3PresignedUrlStrategy implements PresignedUrlStrategy {

    private static final Logger log = LogManager.getLogger(S3PresignedUrlStrategy.class);

    final AWSS3ClientBuilder builder;

    public S3PresignedUrlStrategy(AWSS3ClientBuilder builder) {
        this.builder = builder;
    }

    protected S3Presigner presigner(AWSS3ClientBuilder builder) {
        var presignerBuilder = S3Presigner.builder()
                          .credentialsProvider(builder.credentialsProvider.get())
                          .region(builder.region);
        if (StringUtils.isNotBlank(builder.endpoint)) {
            presignerBuilder.endpointOverride(URI.create(builder.endpoint));
            presignerBuilder.serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(true).build()
            );
        }
        return presignerBuilder.build();
    }

    @Override
    public String generatePresignedUrl(String bucketName, String objectKey, Duration expiration,
                                        String contentType, String contentDisposition) throws IOException {
        try (S3Presigner presigner = presigner(builder)) {
            if (presigner == null) {
                throw new IllegalStateException("S3Presigner supplier returned null");
            }
            if (bucketName == null || bucketName.isEmpty()) {
                throw new IllegalStateException("Bucket name supplier returned null or empty");
            }

            try {
                GetObjectRequest.Builder getObjectBuilder = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey);

                if (StringUtils.isNotBlank(contentType)) {
                    getObjectBuilder.responseContentType(contentType);
                }
                if (StringUtils.isNotBlank(contentDisposition)) {
                    getObjectBuilder.responseContentDisposition(contentDisposition);
                }

                GetObjectPresignRequest presignRequest =
                    GetObjectPresignRequest.builder()
                                           .signatureDuration(expiration)
                                           .getObjectRequest(getObjectBuilder.build())
                                           .build();

                String presignedUrl = presigner.presignGetObject(presignRequest).url().toString();

                if (log.isDebugEnabled()) {
                    log.debug("Generated S3 presigned URL for key {}: {}", objectKey, presignedUrl);
                }

                return presignedUrl;
            } catch (S3Exception | SdkClientException e) {
                log.error("Error generating S3 presigned URL for key: {}", objectKey, e);
                throw new IOException("Failed to generate S3 presigned URL", e);
            }
        }
    }
}
