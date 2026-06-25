/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.dspace.content.Bitstream;
import org.dspace.core.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link BitstreamStorageServiceImpl} presigned URL delegation.
 *
 * Covers Duration-passthrough through the service layer to the underlying
 * BitStoreService, null bitstream guard, null return from store, and
 * store-not-found NPE.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
@RunWith(MockitoJUnitRunner.class)
public class BitstreamStorageServiceImplTest {

    @Mock
    private BitStoreService bitStoreService;

    @Mock
    private Context context;

    @Mock
    private Bitstream bitstream;

    private BitstreamStorageServiceImpl storageService;
    private Map<Integer, BitStoreService> stores;

    @Before
    public void setUp() {
        storageService = new BitstreamStorageServiceImpl();
        stores = new HashMap<>();
        stores.put(0, bitStoreService);
        ReflectionTestUtils.setField(storageService, "stores", stores);
    }

    @Test
    public void testGetPresignedUrl_WithDuration_Success() throws Exception {
        when(bitstream.getStoreNumber()).thenReturn(0);
        when(bitStoreService.isInitialized()).thenReturn(true);
        when(bitStoreService.getPresignedUrl(eq(bitstream), any(Duration.class)))
            .thenReturn("https://example.com/presigned");

        String result = storageService.getPresignedUrl(context, bitstream, Duration.ofSeconds(60));

        assertEquals("https://example.com/presigned", result);
        verify(bitStoreService).getPresignedUrl(eq(bitstream), any(Duration.class));
    }

    @Test
    public void testGetPresignedUrl_WithDuration_NullReturn() throws Exception {
        when(bitstream.getStoreNumber()).thenReturn(0);
        when(bitStoreService.isInitialized()).thenReturn(true);
        when(bitStoreService.getPresignedUrl(eq(bitstream), any(Duration.class)))
            .thenReturn(null);

        String result = storageService.getPresignedUrl(context, bitstream, Duration.ofSeconds(30));

        assertNull(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPresignedUrl_WithDuration_NullBitstream() throws Exception {
        storageService.getPresignedUrl(context, null, Duration.ofSeconds(30));
    }

    @Test
    public void testGetPresignedUrl_WithDuration_DelegationChain() throws Exception {
        when(bitstream.getStoreNumber()).thenReturn(0);
        when(bitstream.getID()).thenReturn(UUID.randomUUID());
        when(bitStoreService.isInitialized()).thenReturn(true);

        Duration customDuration = Duration.ofMinutes(15);
        when(bitStoreService.getPresignedUrl(eq(bitstream), eq(customDuration)))
            .thenReturn("https://s3.example.com/presigned");

        String result = storageService.getPresignedUrl(context, bitstream, customDuration);

        assertEquals("https://s3.example.com/presigned", result);
        verify(bitStoreService).getPresignedUrl(eq(bitstream), eq(customDuration));
    }

    @Test(expected = NullPointerException.class)
    public void testGetPresignedUrl_WithDuration_StoreNotFound() throws Exception {
        when(bitstream.getStoreNumber()).thenReturn(99);

        storageService.getPresignedUrl(context, bitstream, Duration.ofSeconds(30));
    }
}
