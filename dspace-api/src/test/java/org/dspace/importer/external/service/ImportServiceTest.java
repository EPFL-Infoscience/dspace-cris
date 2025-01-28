/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.dspace.AbstractDSpaceIntegrationTest;
import org.dspace.importer.external.datamodel.ImportRecord;
import org.dspace.importer.external.service.components.FileSource;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.submit.extraction.GrobidImportMetadataSourceServiceImpl;
import org.dspace.utils.DSpace;
import org.junit.Before;
import org.junit.Test;

/**
 * Test class for {@link ImportService }
 * 
 * @author Daniele Ninfo (daniele.ninfo at 4science.com)
 *
 */
public class ImportServiceTest extends AbstractDSpaceIntegrationTest {

    private static final String SIMPLE_ARTICLE = "simple-article.pdf";

    protected ConfigurationService configurationService;

    protected ImportService importService;

    protected File simpleArticle;

    protected long maxFileLength;

    @Before
    public void setUp() {
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        importService = new DSpace().getServiceManager().getApplicationContext()
                .getBean("org.dspace.importer.external.service.ImportService", ImportService.class);
        simpleArticle = new File(getClass().getResource(SIMPLE_ARTICLE).getFile()); // it's about 100k
    }

    @Test
    public void testGetRecord_FileSizeAccepted() throws Exception {
        maxFileLength = configurationService.getLongProperty("grobid.max-file-size-accepted", 10 * 1024 * 1024); // 10Mb
        try {
            configurationService.setProperty("grobid.max-file-size-accepted", 100 * 1024 * 1024L); // 100Mb

            ImportRecord mockRecord = mock(ImportRecord.class);
            FileSource fileSource = spy(new GrobidImportMetadataSourceServiceImpl());
            when(fileSource.isValidSourceForFile(anyString())).thenReturn(true);
            doReturn(mockRecord).when(fileSource).getRecord(any(InputStream.class));

            ImportService spyImportService = spy(importService);
            doReturn(fileSource).when(spyImportService).getFileSource(any(InputStream.class), anyString());

            ImportRecord result = spyImportService.getRecord(simpleArticle, SIMPLE_ARTICLE);
            assertNotNull(result);

            FileSource actualFileSource =
                    importService.getFileSource(new FileInputStream(simpleArticle), SIMPLE_ARTICLE);
            assertTrue(actualFileSource.isFileSizeAccepted(simpleArticle.length()));

        } finally {
            configurationService.setProperty("grobid.max-file-size-accepted", maxFileLength);
        }
    }

    @Test
    public void testGetRecord_FileSizeNotAccepted() throws Exception {
        maxFileLength = configurationService.getLongProperty("grobid.max-file-size-accepted", 10 * 1024 * 1024); // 10Mb
        try {
            configurationService.setProperty("grobid.max-file-size-accepted", 1L); // 1b
            ImportRecord result = importService.getRecord(simpleArticle, SIMPLE_ARTICLE);
            assertNull(result);

            FileSource actualFileSource =
                    importService.getFileSource(new FileInputStream(simpleArticle), SIMPLE_ARTICLE);
            assertFalse(actualFileSource.isFileSizeAccepted(simpleArticle.length()));
        } finally {
            configurationService.setProperty("grobid.max-file-size-accepted", maxFileLength);
        }
    }
}
