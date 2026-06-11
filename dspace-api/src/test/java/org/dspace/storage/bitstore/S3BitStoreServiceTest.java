/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;

import org.dspace.content.Bitstream;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link S3BitStoreService} presigned URL delegation.
 *
 * Tests cover strategy delegation, registered bitstream key stripping,
 * initialization guards, null strategy guards, and content-type/disposition
 * resolution passed through to the strategy.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
@RunWith(MockitoJUnitRunner.class)
public class S3BitStoreServiceTest {

    private static MockedStatic<DSpaceServicesFactory> dspaceServicesFactoryMock;

    @Mock
    private PresignedUrlStrategy presignedUrlStrategy;

    private S3BitStoreService s3BitStoreService;

    @BeforeClass
    public static void initClass() {
        ConfigurationService configService = mock(ConfigurationService.class);
        lenient().when(configService.getLongProperty(anyString(), anyLong())).thenReturn(60L);

        DSpaceServicesFactory factory = mock(DSpaceServicesFactory.class);
        lenient().when(factory.getConfigurationService()).thenReturn(configService);

        dspaceServicesFactoryMock = mockStatic(DSpaceServicesFactory.class);
        when(DSpaceServicesFactory.getInstance()).thenReturn(factory);
    }

    @AfterClass
    public static void destroyClass() {
        if (dspaceServicesFactoryMock != null) {
            dspaceServicesFactoryMock.close();
        }
    }

    @Before
    public void setUp() {
        s3BitStoreService = new S3BitStoreService();
        s3BitStoreService.setPresignedUrlStrategy(presignedUrlStrategy);
    }

    @Test
    public void testGetPresignedUrl_DelegatesToStrategy() throws Exception {
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);
        s3BitStoreService.setBucketName("test-bucket");

        Bitstream bitstream = mock(Bitstream.class);
        when(bitstream.getInternalId()).thenReturn("123456789");
        when(bitstream.getName()).thenReturn("test-file.pdf");

        String expectedUrl = "https://s3.amazonaws.com/bucket/123456789?sig=abc";
        when(presignedUrlStrategy.generatePresignedUrl(any(), any(), any(Duration.class), any(), any()))
            .thenReturn(expectedUrl);

        String result = s3BitStoreService.getPresignedUrl(bitstream);

        assertEquals(expectedUrl, result);
        verify(presignedUrlStrategy).generatePresignedUrl(
            eq("test-bucket"), eq("123456789"), any(Duration.class), any(), any());
    }

    @Test
    public void testGetPresignedUrl_RegisteredBitstream() throws Exception {
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);
        s3BitStoreService.setBucketName("test-bucket");

        Bitstream bitstream = mock(Bitstream.class);
        when(bitstream.getInternalId()).thenReturn("-Rcustom/path/file.txt");
        when(bitstream.getName()).thenReturn("file.txt");

        when(presignedUrlStrategy.generatePresignedUrl(any(), any(), any(Duration.class), any(), any()))
            .thenReturn("https://example.com");

        s3BitStoreService.getPresignedUrl(bitstream);

        verify(presignedUrlStrategy).generatePresignedUrl(
            eq("test-bucket"), eq("custom/path/file.txt"), any(Duration.class), any(), any());
    }

    @Test
    public void testGetPresignedUrl_ContentTypeAndDispositionResolved() throws Exception {
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);
        s3BitStoreService.setBucketName("test-bucket");

        Bitstream bitstream = mock(Bitstream.class);
        when(bitstream.getInternalId()).thenReturn("abc123");
        when(bitstream.getName()).thenReturn("report.pdf");
        // getFormat(null) returns null → contentType will be null; disposition uses the name

        String expectedUrl = "https://s3.amazonaws.com/bucket/abc123?sig=xyz";
        when(presignedUrlStrategy.generatePresignedUrl(any(), any(), any(Duration.class), isNull(),
                                                        eq("attachment; filename=\"report.pdf\"")))
            .thenReturn(expectedUrl);

        String result = s3BitStoreService.getPresignedUrl(bitstream);

        assertEquals(expectedUrl, result);
    }

    @Test
    public void testGetPresignedUrl_NotInitialized() {
        try {
            s3BitStoreService.getPresignedUrl(mock(Bitstream.class));
            fail("Expected IOException");
        } catch (IOException e) {
            assertEquals("S3BitStoreService not initialized", e.getMessage());
        }
    }

    @Test
    public void testGetPresignedUrl_NullStrategy() {
        ReflectionTestUtils.setField(s3BitStoreService, "presignedUrlStrategy", null);
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);

        try {
            s3BitStoreService.getPresignedUrl(mock(Bitstream.class));
            fail("Expected IOException");
        } catch (IOException e) {
            assertEquals("S3BitStoreService presigned URL strategy not initialized", e.getMessage());
        }
    }

}
