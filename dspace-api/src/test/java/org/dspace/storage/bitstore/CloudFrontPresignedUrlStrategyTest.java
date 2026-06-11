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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;
import software.amazon.awssdk.services.cloudfront.url.SignedUrl;

/**
 * Unit tests for {@link CloudFrontPresignedUrlStrategy}.
 *
 * Tests cover successful signed URL generation, correct request parameters,
 * and error handling for CloudFront failures.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
@RunWith(MockitoJUnitRunner.class)
public class CloudFrontPresignedUrlStrategyTest {

    private static final String TEST_KEY_PAIR_ID = "TESTKEYPAIRID";
    private static final String TEST_DOMAIN = "https://cdn.example.com";
    private static final String TEST_TENANT_DOMAIN = "https://cdn.example.com/my--custom--tenant";
    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();
    private static String privateKeyPath;
    @Mock
    private CloudFrontUtilities cloudFrontUtilities;

    @Mock
    private SignedUrl signedUrl;

    private static MockedStatic<CloudFrontUtilities> cfMock;

    private CloudFrontPresignedUrlStrategy strategy;

    private static void writePemPrivateKey(File file) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair pair = keyGen.generateKeyPair();

        byte[] encoded = pair.getPrivate().getEncoded();
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file),
                                                                StandardCharsets.UTF_8)) {
            writer.write("-----BEGIN PRIVATE KEY-----\n");
            writer.write(b64);
            if (!b64.endsWith("\n")) {
                writer.write("\n");
            }
            writer.write("-----END PRIVATE KEY-----\n");
        }
    }

    @BeforeClass
    public static void initClass() throws Exception {
        File privateKeyFile = tempFolder.newFile("private-key.pem");
        writePemPrivateKey(privateKeyFile);
        privateKeyPath = privateKeyFile.getAbsolutePath();

        cfMock = mockStatic(CloudFrontUtilities.class);
    }

    @AfterClass
    public static void destroyClass() {
        if (cfMock != null) {
            cfMock.close();
        }
    }

    @Before
    public void setUp() throws Exception {
        cfMock.when(CloudFrontUtilities::create).thenReturn(cloudFrontUtilities);
        strategy = new CloudFrontPresignedUrlStrategy(
            TEST_KEY_PAIR_ID,
            privateKeyPath,
            TEST_DOMAIN
        );
    }

    @Test
    public void testGeneratePresignedUrl() throws Exception {
        String expectedUrl = "https://cdn.example.com/my-file.pdf?Policy=...";
        when(signedUrl.url()).thenReturn(expectedUrl);
        when(cloudFrontUtilities.getSignedUrlWithCannedPolicy(any(CannedSignerRequest.class)))
            .thenReturn(signedUrl);

        String result = strategy.generatePresignedUrl("bucket", "my-file.pdf", Duration.ofMinutes(10),
                                                       null, null);

        assertEquals(expectedUrl, result);
        verify(cloudFrontUtilities).getSignedUrlWithCannedPolicy(any(CannedSignerRequest.class));
    }

    @Test(expected = IOException.class)
    public void testGeneratePresignedUrl_SdkClientException() throws Exception {
        when(cloudFrontUtilities.getSignedUrlWithCannedPolicy(any(CannedSignerRequest.class)))
            .thenThrow(SdkClientException.create("CloudFront error"));

        strategy.generatePresignedUrl("bucket", "my-file.pdf", Duration.ofMinutes(10), null, null);
    }

    @Test
    public void testGeneratePresignedUrl_MultipleCalls() throws Exception {
        String expectedUrl = "https://cdn.example.com/file.txt?sig=1";
        when(signedUrl.url()).thenReturn(expectedUrl);
        when(cloudFrontUtilities.getSignedUrlWithCannedPolicy(any(CannedSignerRequest.class)))
            .thenReturn(signedUrl);

        strategy.generatePresignedUrl("bucket", "file1.txt", Duration.ofMinutes(1), null, null);
        strategy.generatePresignedUrl("bucket", "file2.txt", Duration.ofMinutes(2), null, null);

        verify(cloudFrontUtilities, times(2))
            .getSignedUrlWithCannedPolicy(any(CannedSignerRequest.class));
    }

    @Test(expected = IOException.class)
    public void testGeneratePresignedUrl_InvalidPrivateKeyPath() throws Exception {
        CloudFrontPresignedUrlStrategy invalidStrategy = new CloudFrontPresignedUrlStrategy(
            TEST_KEY_PAIR_ID,
            "/nonexistent/path/to/key.pem",
            TEST_DOMAIN
        );

        invalidStrategy.generatePresignedUrl("bucket", "test.txt", Duration.ofMinutes(5), null, null);
    }

    @Test
    public void testGeneratePresignedUrl_WithContentTypeAndDisposition() throws Exception {
        String expectedUrl = "https://cdn.example.com/my-file.pdf?response-content-type=application%2Fpdf"
            + "&response-content-disposition=attachment%3B+filename%3D%22my-file.pdf%22&Policy=...";
        when(signedUrl.url()).thenReturn(expectedUrl);
        when(cloudFrontUtilities.getSignedUrlWithCannedPolicy(any(CannedSignerRequest.class)))
            .thenReturn(signedUrl);

        String result = strategy.generatePresignedUrl("bucket", "my-file.pdf", Duration.ofMinutes(10),
                                                       "application/pdf",
                                                       "attachment; filename=\"my-file.pdf\"");

        assertEquals(expectedUrl, result);
        verify(cloudFrontUtilities).getSignedUrlWithCannedPolicy(any(CannedSignerRequest.class));
    }

    @Test
    public void testGeneratePresignedUrlWithTenant() throws Exception {
        String expectedUrl = TEST_TENANT_DOMAIN + "/my-file.pdf?Policy=...";

        strategy = new CloudFrontPresignedUrlStrategy(
            TEST_KEY_PAIR_ID,
            privateKeyPath,
            TEST_TENANT_DOMAIN
        );

        when(signedUrl.url()).thenReturn(expectedUrl);
        when(cloudFrontUtilities.getSignedUrlWithCannedPolicy(any(CannedSignerRequest.class)))
            .thenReturn(signedUrl);

        String result = strategy.generatePresignedUrl("bucket", "my-file.pdf", Duration.ofMinutes(10),
                                                       null, null);

        assertEquals(expectedUrl, result);
        verify(cloudFrontUtilities).getSignedUrlWithCannedPolicy(any(CannedSignerRequest.class));
    }

    @Test
    public void testGeneratePresignedUrlWithTenantEndingSlash() throws Exception {
        String expectedUrl = TEST_TENANT_DOMAIN + "/my-file.pdf?Policy=...";

        strategy = new CloudFrontPresignedUrlStrategy(
            TEST_KEY_PAIR_ID,
            privateKeyPath,
            TEST_TENANT_DOMAIN + "/"
        );

        when(signedUrl.url()).thenReturn(expectedUrl);
        when(cloudFrontUtilities.getSignedUrlWithCannedPolicy(any(CannedSignerRequest.class)))
            .thenReturn(signedUrl);

        String result = strategy.generatePresignedUrl("bucket", "my-file.pdf", Duration.ofMinutes(10),
                                                       null, null);

        assertEquals(expectedUrl, result);
        verify(cloudFrontUtilities).getSignedUrlWithCannedPolicy(any(CannedSignerRequest.class));
    }
}
