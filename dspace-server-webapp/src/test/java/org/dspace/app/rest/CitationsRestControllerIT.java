/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.GroupBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.InstallItemService;
import org.dspace.eperson.Group;
import org.junit.Test;
import org.springframework.http.MediaType;

/**
 * Integration test for {@link CitationsRestController}.
 *
 * @author  Daniele Ninfo (daniele.ninfo at 4science.com)
 */
public class CitationsRestControllerIT extends AbstractControllerIntegrationTest {

    private final InstallItemService installItemService = ContentServiceFactory.getInstance().getInstallItemService();

    @Test
    public void getCitationsReturnsCitationForVisibleItem() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::journal::journal article")
                .withTitle("A Test Publication")
                .withAuthor("Doe, John")
                .withIssueDate("2021-05-20")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String body = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.style").value("apa"))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results").isNotEmpty())
                .andExpect(jsonPath("$.results[0].uuid").value(item.getID().toString()))
                .andExpect(jsonPath("$.results[0].handle").value(item.getHandle()))
                .andExpect(jsonPath("$.results[0].type").value("article-journal"))
                .andExpect(jsonPath("$.results[0].collection").value("Test Collection"))
                .andExpect(jsonPath("$.results[0].year").value("2021"))
                .andExpect(jsonPath("$.results[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results[0].cslItem").isNotEmpty())
                .andExpect(jsonPath("$.results[0].cslItem.items[0].title").value("A Test Publication"));
    }

    @Test
    public void getCitationsOmitsStyleAndGroupByWhenFormatIsLight() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("A Light Test Publication")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String body = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"light\"," +
                "\"groupBy\":\"both\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(1)))
                .andExpect(jsonPath("$.style").doesNotExist())
                .andExpect(jsonPath("$.groupBy").doesNotExist())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results").isNotEmpty())
                .andExpect(jsonPath("$.results[0].*", hasSize(2)))
                .andExpect(jsonPath("$.results[0].uuid").value(item.getID().toString()))
                .andExpect(jsonPath("$.results[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results[0].handle").doesNotExist())
                .andExpect(jsonPath("$.results[0].type").doesNotExist())
                .andExpect(jsonPath("$.results[0].collection").doesNotExist())
                .andExpect(jsonPath("$.results[0].year").doesNotExist())
                .andExpect(jsonPath("$.results[0].cslItem").doesNotExist());
    }

    @Test
    public void getCitationsHidesRestrictedItem() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Group restrictedGroup = GroupBuilder.createGroup(context).withName("Restricted readers").build();

        Item visibleItem = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Visible Publication")
                .withAuthor("Doe, Jane")
                .inArchive()
                .build();

        Item hiddenItem = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Hidden Publication")
                .withAuthor("Roe, Jane")
                .withReaderGroup(restrictedGroup)
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String body = "{" +
                "\"uuids\":[\"" + visibleItem.getID() + "\",\"" + hiddenItem.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].uuid", hasItem(visibleItem.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(hiddenItem.getID().toString()))));
    }

    @Test
    public void getCitationsReturnsBadRequestWhenStyleIsMissing() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("A Test Publication")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String body = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getCitationsCombinesUuidsAndQueryWithAndSemantics() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        Item matchingItem1 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Combo Match Token One")
                .inArchive()
                .build();

        Item matchingItem2 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Combo Match Token Two")
                .inArchive()
                .build();

        Item notMatchingItem = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Combo Excluded Token")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String uuidAndQueryBody = "{" +
                "\"uuids\":[\"" + matchingItem1.getID() + "\",\"" + matchingItem2.getID() + "\"]," +
                "\"query\":\"dc.title:Combo Match Token One\"," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uuidAndQueryBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(matchingItem1.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(matchingItem2.getID().toString()))))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(notMatchingItem.getID().toString()))));

        String uuidOnlyBody = "{" +
                "\"uuids\":[\"" + matchingItem1.getID() + "\",\"" + notMatchingItem.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uuidOnlyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(matchingItem1.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(notMatchingItem.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(matchingItem2.getID().toString()))));

        String queryOnlyBody = "{" +
                "\"query\":\"dc.title:Combo Match Token\"," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queryOnlyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(matchingItem1.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(matchingItem2.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(notMatchingItem.getID().toString()))));

        String noUuidNoQueryBody = "{" +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noUuidNoQueryBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getCitationsWithConfiguration() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection publicationCollection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Publications")
                .withEntityType("Publication")
                .build();
        Collection personCollection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("People")
                .withEntityType("Person")
                .build();

        Item publication = ItemBuilder.createItem(context, publicationCollection)
                .withEntityType("Publication")
                .withType("text::journal::journal article")
                .withTitle("Configuration Scope Match")
                .withIssueDate("2017-10-17")
                .withAuthor("Doe, John")
                .withSubject("ExtraEntry")
                .inArchive()
                .build();

        Item person = ItemBuilder.createItem(context, personCollection)
                .withEntityType("Person")
                .withTitle("John Doe")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String onlyResearchOutputsBody = "{" +
                "\"configuration\":\"researchoutputs\"," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(onlyResearchOutputsBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(person.getID().toString()))));
    }

    @Test
    public void getCitationsWithConfigurationScopeQuery() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection researchOutputsCollection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Research Outputs Collection")
                .build();
        Collection otherCollection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Other Collection")
                .build();

        Item matchingResearchOutput = ItemBuilder.createItem(context, researchOutputsCollection)
                .withEntityType("Publication")
                .withTitle("Configuration Scope Match")
                .inArchive()
                .build();

        Item matchingResearchOutputInOtherScope = ItemBuilder.createItem(context, otherCollection)
                .withEntityType("Publication")
                .withTitle("Configuration Scope Match")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String researchOutputsBody = "{" +
                "\"configuration\":\"researchoutputs\"," +
                "\"scope\":\"" + researchOutputsCollection.getID() + "\"," +
                "\"query\":\"dc.title:Configuration Scope Match\"," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(researchOutputsBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].uuid").value(matchingResearchOutput.getID().toString()))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(matchingResearchOutput.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid",
                        not(hasItem(matchingResearchOutputInOtherScope.getID().toString()))));

        String wrongScopeBody = "{" +
                "\"configuration\":\"researchoutputs\"," +
                "\"scope\":\"" + otherCollection.getID() + "\"," +
                "\"query\":\"dc.title:Configuration Scope Match\"," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongScopeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].uuid").value(matchingResearchOutputInOtherScope.getID().toString()))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(matchingResearchOutputInOtherScope.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid",
                        not(hasItem(matchingResearchOutput.getID().toString()))));

        String researcherProfilesBody = "{" +
                "\"configuration\":\"person\"," +
                "\"scope\":\"" + researchOutputsCollection.getID() + "\"," +
                "\"query\":\"dc.title:Configuration Scope Match\"," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(researcherProfilesBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    public void getCitationsAppliesSortInSolrQuery() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        Item zebraItem = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Zebra Title")
                .withIssueDate("2022-06-15")
                .inArchive()
                .build();

        Item alphaItem = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Alpha Title")
                .withIssueDate("2020-01-10")
                .inArchive()
                .build();

        Item middleItem = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Middle Title")
                .withIssueDate("2021-03-20")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String uuidsJson = "\"" + zebraItem.getID() + "\",\"" + alphaItem.getID() + "\",\""
                + middleItem.getID() + "\"";

        String titleSortBody = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"title\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(titleSortBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].uuid").value(alphaItem.getID().toString()))
                .andExpect(jsonPath("$.results[1].uuid").value(middleItem.getID().toString()))
                .andExpect(jsonPath("$.results[2].uuid").value(zebraItem.getID().toString()));

        String dateSortBody = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"date\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dateSortBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].uuid").value(alphaItem.getID().toString()))
                .andExpect(jsonPath("$.results[1].uuid").value(middleItem.getID().toString()))
                .andExpect(jsonPath("$.results[2].uuid").value(zebraItem.getID().toString()));

        String yearSortBody = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"year\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yearSortBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].uuid").value(alphaItem.getID().toString()))
                .andExpect(jsonPath("$.results[1].uuid").value(middleItem.getID().toString()))
                .andExpect(jsonPath("$.results[2].uuid").value(zebraItem.getID().toString()));
    }
}

