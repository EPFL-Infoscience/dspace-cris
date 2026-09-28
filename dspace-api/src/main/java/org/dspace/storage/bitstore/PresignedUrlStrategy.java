/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import java.io.IOException;
import java.time.Duration;

/**
 * Strategy interface for generating presigned URLs for bitstream access.
 * Implementations may use different mechanisms (e.g., direct S3 presigned URLs
 * or CloudFront signed URLs).
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public interface PresignedUrlStrategy {

    /**
     * Generate a presigned URL for accessing the given object key.
     *
     * @param objectKey          the S3 object key
     * @param expiration         the duration until the URL expires
     * @param contentType        the content type override (nullable,
     *                           e.g. &quot;application/pdf&quot;)
     * @param contentDisposition the content disposition override (nullable,
     *                           e.g. &quot;attachment; filename=\&quot;file.pdf\&quot;&quot;)
     * @return the presigned URL as a string
     * @throws IOException if URL generation fails
     */
    String generatePresignedUrl(String bucketName, String objectKey, Duration expiration,
                                String contentType, String contentDisposition) throws IOException;
}
