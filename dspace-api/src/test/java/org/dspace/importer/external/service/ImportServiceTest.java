/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;

import org.dspace.AbstractDSpaceIntegrationTest;
import org.dspace.importer.external.datamodel.ImportRecord;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
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
            ImportRecord result = importService.getRecord(simpleArticle, SIMPLE_ARTICLE);
            assertNotNull(result);
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
        } finally {
            configurationService.setProperty("grobid.max-file-size-accepted", maxFileLength);
        }
    }
}
