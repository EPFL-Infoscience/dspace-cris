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

    // ---------- Duration overload tests ----------

    @Test
    public void testGetPresignedUrl_WithDuration_DelegatesToStrategy() throws Exception {
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);
        s3BitStoreService.setBucketName("test-bucket");

        Bitstream bitstream = mock(Bitstream.class);
        when(bitstream.getInternalId()).thenReturn("abc-123");
        when(bitstream.getName()).thenReturn("document.pdf");

        String expectedUrl = "https://s3.amazonaws.com/bucket/abc-123?dur=30";
        when(presignedUrlStrategy.generatePresignedUrl(any(), any(), any(Duration.class), any(), any()))
            .thenReturn(expectedUrl);

        String result = s3BitStoreService.getPresignedUrl(bitstream, Duration.ofSeconds(30));

        assertEquals(expectedUrl, result);
        verify(presignedUrlStrategy).generatePresignedUrl(
            eq("test-bucket"), eq("abc-123"), any(Duration.class), any(), any());
    }

    @Test
    public void testGetPresignedUrl_WithDuration_VerifiesExactDuration() throws Exception {
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);
        s3BitStoreService.setBucketName("test-bucket");

        Bitstream bitstream = mock(Bitstream.class);
        when(bitstream.getInternalId()).thenReturn("xyz-789");
        when(bitstream.getName()).thenReturn("file.txt");

        Duration requestedDuration = Duration.ofSeconds(90);
        when(presignedUrlStrategy.generatePresignedUrl(any(), any(), any(Duration.class), any(), any()))
            .thenReturn("https://example.com");

        s3BitStoreService.getPresignedUrl(bitstream, requestedDuration);

        verify(presignedUrlStrategy).generatePresignedUrl(
            eq("test-bucket"), eq("xyz-789"), eq(requestedDuration), any(), any());
    }

    @Test
    public void testGetPresignedUrl_WithDuration_RegisteredBitstream() throws Exception {
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);
        s3BitStoreService.setBucketName("test-bucket");

        Bitstream bitstream = mock(Bitstream.class);
        when(bitstream.getInternalId()).thenReturn("-Rreg/asset/data.bin");
        when(bitstream.getName()).thenReturn("data.bin");

        when(presignedUrlStrategy.generatePresignedUrl(any(), any(), any(Duration.class), any(), any()))
            .thenReturn("https://example.com");

        s3BitStoreService.getPresignedUrl(bitstream, Duration.ofSeconds(60));

        verify(presignedUrlStrategy).generatePresignedUrl(
            eq("test-bucket"), eq("reg/asset/data.bin"), any(Duration.class), any(), any());
    }

    @Test
    public void testGetPresignedUrl_WithDuration_ContentTypeAndDisposition() throws Exception {
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);
        s3BitStoreService.setBucketName("test-bucket");

        Bitstream bitstream = mock(Bitstream.class);
        when(bitstream.getInternalId()).thenReturn("file-001");
        when(bitstream.getName()).thenReturn("report.pdf");

        String expectedUrl = "https://s3.amazonaws.com/bucket/file-001?dur=15";
        when(presignedUrlStrategy.generatePresignedUrl(any(), any(), any(Duration.class), isNull(),
                                                         eq("attachment; filename=\"report.pdf\"")))
            .thenReturn(expectedUrl);

        String result = s3BitStoreService.getPresignedUrl(bitstream, Duration.ofSeconds(15));

        assertEquals(expectedUrl, result);
    }

    @Test
    public void testGetPresignedUrl_WithDuration_ZeroDuration() throws Exception {
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);
        s3BitStoreService.setBucketName("test-bucket");

        Bitstream bitstream = mock(Bitstream.class);
        when(bitstream.getInternalId()).thenReturn("zero-id");
        when(bitstream.getName()).thenReturn("zero.txt");

        when(presignedUrlStrategy.generatePresignedUrl(any(), any(), any(Duration.class), any(), any()))
            .thenReturn("https://example.com/zero");

        s3BitStoreService.getPresignedUrl(bitstream, Duration.ZERO);

        verify(presignedUrlStrategy).generatePresignedUrl(
            eq("test-bucket"), eq("zero-id"), eq(Duration.ZERO), any(), any());
    }

    @Test
    public void testGetPresignedUrl_WithDuration_LongDuration() throws Exception {
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);
        s3BitStoreService.setBucketName("test-bucket");

        Bitstream bitstream = mock(Bitstream.class);
        when(bitstream.getInternalId()).thenReturn("long-id");
        when(bitstream.getName()).thenReturn("large.dat");

        Duration longDur = Duration.ofHours(24);
        when(presignedUrlStrategy.generatePresignedUrl(any(), any(), any(Duration.class), any(), any()))
            .thenReturn("https://example.com/long");

        s3BitStoreService.getPresignedUrl(bitstream, longDur);

        verify(presignedUrlStrategy).generatePresignedUrl(
            eq("test-bucket"), eq("long-id"), eq(longDur), any(), any());
    }

    @Test
    public void testGetPresignedUrl_WithDuration_NotInitialized() {
        try {
            s3BitStoreService.getPresignedUrl(mock(Bitstream.class), Duration.ofSeconds(30));
            fail("Expected IOException");
        } catch (IOException e) {
            assertEquals("S3BitStoreService not initialized", e.getMessage());
        }
    }

    @Test
    public void testGetPresignedUrl_WithDuration_NullStrategy() {
        ReflectionTestUtils.setField(s3BitStoreService, "presignedUrlStrategy", null);
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);

        try {
            s3BitStoreService.getPresignedUrl(mock(Bitstream.class), Duration.ofSeconds(30));
            fail("Expected IOException");
        } catch (IOException e) {
            assertEquals("S3BitStoreService presigned URL strategy not initialized", e.getMessage());
        }
    }

    @Test(expected = NullPointerException.class)
    public void testGetPresignedUrl_WithDuration_NullBitstream() throws Exception {
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);
        // presignedUrlStrategy is already set via setUp() — must not be null to reach NPE on getInternalId()

        s3BitStoreService.getPresignedUrl(null, Duration.ofSeconds(30));
    }

    @Test
    public void testGetPresignedUrl_NoDuration_DelegatesToWithDuration() throws Exception {
        ReflectionTestUtils.setField(s3BitStoreService, "initialized", true);
        s3BitStoreService.setBucketName("test-bucket");

        Bitstream bitstream = mock(Bitstream.class);
        when(bitstream.getInternalId()).thenReturn("delegate-test");
        when(bitstream.getName()).thenReturn("delegate.pdf");

        when(presignedUrlStrategy.generatePresignedUrl(any(), any(), any(Duration.class), any(), any()))
            .thenReturn("https://example.com/delegate");

        String result = s3BitStoreService.getPresignedUrl(bitstream);

        assertEquals("https://example.com/delegate", result);
        // The no-Duration overload delegates to getPresignedUrl(bitstream, presignDuration())
        // which calls the strategy with whatever presignDuration() returns (mocked as 60s via @BeforeClass)
    }

}
