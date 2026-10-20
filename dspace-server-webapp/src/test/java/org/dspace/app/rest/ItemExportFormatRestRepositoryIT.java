/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.content.crosswalk.CrosswalkMode.MULTIPLE;
import static org.dspace.content.crosswalk.CrosswalkMode.SINGLE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.Test;


public class ItemExportFormatRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Test
    public void testExportFormatForTypeAll() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                .param("size", "100")
                .param("molteplicity", SINGLE.name())
                .param("entityTypeId", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats.length()", Matchers.equalTo(27)));
    }

    @Test
    public void testExportFormatForTypePublication() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", SINGLE.name())
                        .param("entityTypeId", "Publication"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats.length()", Matchers.equalTo(15)));
    }

    @Test
    public void testExportFormatWithoutType() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", SINGLE.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats.length()",
                        Matchers.greaterThanOrEqualTo(27)));
    }

    /**
     * Requirement: Patents must be exportable as BibTeX from search results (bulk export).
     * Input: search-level export formats query for entity type Patent in MULTIPLE mode.
     * Expected output: the available formats include the Patent BibTeX format (patent-bibtex).
     */
    @Test
    public void testBibtexAvailableAtSearchLevelForPatent() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", MULTIPLE.name())
                        .param("entityTypeId", "Patent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("patent-bibtex")));
    }

    /**
     * Requirement: Products must be exportable as BibTeX from search results (bulk export).
     * Input: search-level export formats query for entity type Product in MULTIPLE mode.
     * Expected output: the available formats include the Product BibTeX format (product-bibtex).
     */
    @Test
    public void testBibtexAvailableAtSearchLevelForProduct() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", MULTIPLE.name())
                        .param("entityTypeId", "Product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("product-bibtex")));
    }

    /**
     * Requirement: Patents must be exportable as RIS (EndNote) from search results (bulk export).
     * Input: search-level export formats query for entity type Patent in MULTIPLE mode.
     * Expected output: the available formats include the Patent RIS (EndNote) format
     * ("RIS  (Endnote)").
     */
    @Test
    public void testRisAvailableAtSearchLevelForPatent() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", MULTIPLE.name())
                        .param("entityTypeId", "Patent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("RIS  (Endnote)")));
    }

    /**
     * Requirement: Products must be exportable as RIS (EndNote) from search results (bulk export).
     * Input: search-level export formats query for entity type Product in MULTIPLE mode.
     * Expected output: the available formats include the Product RIS (EndNote) format
     * ("RIS(Endnote)").
     */
    @Test
    public void testRisAvailableAtSearchLevelForProduct() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", MULTIPLE.name())
                        .param("entityTypeId", "Product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("RIS(Endnote)")));
    }

    /**
     * Requirement: Publications must be exportable as JSON from the researchoutputs search page
     * (bulk export).
     * Input: search-level export formats query for the researchoutputs search configuration
     * in MULTIPLE mode.
     * Expected output: the available formats include the Publication JSON format (publication-json).
     */
    @Test
    public void testJsonAvailableAtSearchLevelForPublication() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byConfigurationAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", MULTIPLE.name())
                        .param("configuration", "researchoutputs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("publication-json")));
    }

    /**
     * Requirement: a single Publication must be exportable as XLS (the format must be available in
     * SINGLE mode, which is what the single-item export dropdown queries).
     * Input: export formats query for entity type Publication in SINGLE mode.
     * Expected output: the available formats include the XLS format (publication-xls).
     */
    @Test
    public void testXlsAvailableInSingleModeForPublication() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", SINGLE.name())
                        .param("entityTypeId", "Publication"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("publication-xls")));
    }

    /**
     * Requirement: a single Patent must be exportable as XLS (the format must be available in
     * SINGLE mode, which is what the single-item export dropdown queries).
     * Input: export formats query for entity type Patent in SINGLE mode.
     * Expected output: the available formats include the XLS format (patent-xls).
     */
    @Test
    public void testXlsAvailableInSingleModeForPatent() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", SINGLE.name())
                        .param("entityTypeId", "Patent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("patent-xls")));
    }

    /**
     * Requirement: a single Product must be exportable as XLS (the format must be available in
     * SINGLE mode, which is what the single-item export dropdown queries).
     * Input: export formats query for entity type Product in SINGLE mode.
     * Expected output: the available formats include the XLS format (product-xls).
     */
    @Test
    public void testXlsAvailableInSingleModeForProduct() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", SINGLE.name())
                        .param("entityTypeId", "Product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("product-xls")));
    }

    /**
     * Requirement: a single Person must be exportable as XLS and CSV (the formats must be available
     * in SINGLE mode, which is what the single-item export dropdown queries).
     * Input: export formats query for entity type Person in SINGLE mode.
     * Expected output: the available formats include both the XLS (person-xls) and CSV (person-csv)
     * formats.
     */
    @Test
    public void testXlsAndCsvAvailableInSingleModeForPerson() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", SINGLE.name())
                        .param("entityTypeId", "Person"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("person-xls")))
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("person-csv")));
    }

    /**
     * Requirement: a single OrgUnit must be exportable as XLS and CSV (the formats must be available
     * in SINGLE mode, which is what the single-item export dropdown queries).
     * Input: export formats query for entity type OrgUnit in SINGLE mode.
     * Expected output: the available formats include both the XLS (orgUnit-xls) and CSV
     * (orgUnit-csv) formats.
     */
    @Test
    public void testXlsAndCsvAvailableInSingleModeForOrgUnit() throws Exception {
        getClient().perform(get("/api/integration/itemexportformats/search/byEntityTypeAndMolteplicity")
                        .param("size", "100")
                        .param("molteplicity", SINGLE.name())
                        .param("entityTypeId", "OrgUnit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("orgUnit-xls")))
                .andExpect(jsonPath("$._embedded.itemexportformats..id", Matchers.hasItem("orgUnit-csv")));
    }
}