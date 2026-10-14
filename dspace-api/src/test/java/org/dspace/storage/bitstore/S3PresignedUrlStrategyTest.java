 /**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * Unit tests for {@link S3PresignedUrlStrategy}.
 *
 * Tests cover successful URL generation, error handling, and multiple call scenarios.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
@RunWith(MockitoJUnitRunner.class)
public class S3PresignedUrlStrategyTest {

    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_KEY = "test/key";

    @Mock
    private S3Presigner presigner;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    private S3PresignedUrlStrategy strategy;

    @Before
    public void setUp() {
        strategy = spy(new S3PresignedUrlStrategy(AWSS3ClientBuilder.builder()));
        doReturn(presigner).when(strategy).presigner(any(AWSS3ClientBuilder.class));
    }

    @Test
    public void testGeneratePresignedUrl() throws Exception {
        URL expectedUrl = new URL("https://s3.amazonaws.com/test-bucket/test/key?signature=abc123");
        when(presignedGetObjectRequest.url()).thenReturn(expectedUrl);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenReturn(presignedGetObjectRequest);

        String result = strategy.generatePresignedUrl(TEST_BUCKET, TEST_KEY, Duration.ofMinutes(5),
                                                       null, null);

        assertEquals(expectedUrl.toString(), result);
        verify(presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test(expected = IOException.class)
    public void testGeneratePresignedUrl_SdkClientException() throws Exception {
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenThrow(SdkClientException.create("Access Denied"));

        strategy.generatePresignedUrl(TEST_BUCKET, TEST_KEY, Duration.ofMinutes(5), null, null);
    }

    @Test
    public void testGeneratePresignedUrl_MultipleCalls() throws Exception {
        URL expectedUrl = new URL("https://s3.amazonaws.com/test-bucket/key?sig=1");
        when(presignedGetObjectRequest.url()).thenReturn(expectedUrl);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenReturn(presignedGetObjectRequest);

        strategy.generatePresignedUrl(TEST_BUCKET, "key1", Duration.ofMinutes(1), null, null);
        strategy.generatePresignedUrl(TEST_BUCKET, "key2", Duration.ofMinutes(2), null, null);

        verify(presigner, times(2)).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    public void testGeneratePresignedUrl_WithContentTypeAndDisposition() throws Exception {
        URL expectedUrl = new URL(
            "https://s3.amazonaws.com/test-bucket/test/key"
            + "?response-content-type=application%2Fpdf"
            + "&response-content-disposition=attachment%3B+filename%3D%22file.pdf%22"
            + "&signature=abc123");
        when(presignedGetObjectRequest.url()).thenReturn(expectedUrl);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenReturn(presignedGetObjectRequest);

        String result = strategy.generatePresignedUrl(TEST_BUCKET, TEST_KEY, Duration.ofMinutes(5),
                                                       "application/pdf",
                                                       "attachment; filename=\"file.pdf\"");

        assertEquals(expectedUrl.toString(), result);
        verify(presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }
}
