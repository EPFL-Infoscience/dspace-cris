/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

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
                .andExpect(jsonPath("$._embedded.itemexportformats.length()", Matchers.equalTo(14)));
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
}