/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.orcid.model;

import static org.junit.Assert.assertEquals;

import org.dspace.AbstractUnitTest;
import org.dspace.services.factory.DSpaceServicesFactoryImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link OrcidWorkFieldMapping}
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class OrcidWorkFieldMappingTest extends AbstractUnitTest {

    private OrcidWorkFieldMapping orcidPublicationDataProviderFieldMapping;

    @Before
    public void setup() {
        orcidPublicationDataProviderFieldMapping = DSpaceServicesFactoryImpl.getInstance().getServiceManager()
            .getServiceByName("orcidPublicationDataProviderFieldMapping", OrcidWorkFieldMapping.class);
    }

    @Test
    public void testConvertType() {
        assertConvertedType("annotation", "text::annotation");
        assertConvertedType("website", "interactive resource::website");
        assertConvertedType("data-set", "dataset");
        assertConvertedType("book", "text::book/monograph");
        assertConvertedType("book-chapter", "text::book/monograph::book part or chapter");
        assertConvertedType("conference-paper", "text::conference output::conference proceedings::conference paper");
        assertConvertedType("conference-poster", "text::conference output::conference proceedings::conference poster");
        assertConvertedType("journal-article", "text::journal::journal article");
        assertConvertedType("newspaper-article", "text::journal::journal article");
        assertConvertedType("magazine-article", "text::journal::journal article");
        assertConvertedType("lecture-speech", "text::lecture");
        assertConvertedType("patent", "text::patent");
        assertConvertedType("report", "text::report");
        assertConvertedType("book-review", "text::review::book review");
        assertConvertedType("preprint", "text::preprint");
        assertConvertedType("technical-documentation", "text::technical documentation or standard");
        assertConvertedType("working-paper", "text::working paper");
        assertConvertedType("other", "other");
        assertConvertedType("invalid-type-to-be-mapped-to-other", "other");
    }

    private void assertConvertedType(String key, String expectedValue) {
        assertEquals(expectedValue, orcidPublicationDataProviderFieldMapping.convertType(key));
    }
}
