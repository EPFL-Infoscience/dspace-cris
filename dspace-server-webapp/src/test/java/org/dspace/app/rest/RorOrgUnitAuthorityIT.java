/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.matcher.ItemAuthorityMatcher.matchItemAuthorityProperties;
import static org.dspace.app.rest.matcher.ItemAuthorityMatcher.matchItemAuthorityWithOtherInformations;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

public class RorOrgUnitAuthorityIT extends AbstractControllerIntegrationTest {

    private Collection collection;

    @Before
    public void setup() {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        collection = CollectionBuilder.createCollection(context, parentCommunity)
                                      .withName("Test collection")
                                      .build();

        context.restoreAuthSystemState();
    }

    @Test
    public void testWithoutLocalItems() throws Exception {
        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/submission/vocabularies/OrgUnitAuthority/entries")
                                     .param("filter", "windEurope"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$._embedded.entries", containsInAnyOrder(
                                rorOrgUnitEntry("WindEurope", "ROR-ID:", "https://ror.org/00qkeey15"))))
                        .andExpect(jsonPath("$.page.size", Matchers.is(20)))
                        .andExpect(jsonPath("$.page.totalPages", Matchers.is(1)))
                        .andExpect(jsonPath("$.page.totalElements", Matchers.is(1)));
    }

    @Test
    public void testWithLocalItems() throws Exception {

        context.turnOffAuthorisationSystem();

        Item firstOrgUnit = buildOrgUnit("OrgUnit 1");
        Item secondOrgUnit = buildOrgUnit("OrgUnit 2");
        Item thirdOrgUnit = buildOrgUnit("OrgUnit 3");
        Item orgUnit = buildOrgUnit("OrgUnit");

        context.restoreAuthSystemState();

        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/submission/vocabularies/OrgUnitAuthority/entries")
                                     .param("filter", "OrgUnit"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$._embedded.entries", containsInAnyOrder(
                                localEntry("OrgUnit", orgUnit),
                                localEntry("OrgUnit 1", firstOrgUnit),
                                localEntry("OrgUnit 2", secondOrgUnit),
                                localEntry("OrgUnit 3", thirdOrgUnit))))
                        .andExpect(jsonPath("$.page.size", Matchers.is(20)))
                        .andExpect(jsonPath("$.page.totalPages", Matchers.is(1)))
                        .andExpect(jsonPath("$.page.totalElements", Matchers.is(4)));
    }

    private Item buildOrgUnit(String title) {
        return ItemBuilder.createItem(context, collection)
                          .withTitle(title)
                          .withEntityType("OrgUnit")
                          .build();
    }

    private Matcher<? super Object> localEntry(String title, Item OrgUnit) {
        return matchItemAuthorityProperties(OrgUnit.getID().toString(), title, title, "vocabularyEntry");
    }

    private Matcher<? super Object> rorOrgUnitEntry(String title, String authorityPrefix, String rorId) {
        String authority = authorityPrefix + rorId;
        return matchItemAuthorityWithOtherInformations(authority, title, title, "vocabularyEntry", Map.of());
    }
}
