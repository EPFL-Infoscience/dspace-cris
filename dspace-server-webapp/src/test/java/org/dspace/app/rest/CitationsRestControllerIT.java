/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.hasItem;
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
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.eperson.Group;
import org.junit.Test;
import org.springframework.http.MediaType;

/**
 * Integration test for {@link CitationsRestController}.
 *
 * @author  Daniele Ninfo (daniele.ninfo at 4science.com)
 */
public class CitationsRestControllerIT extends AbstractControllerIntegrationTest {

    @Test
    public void getCitationsReturnsCitationForVisibleItem() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("Journal Article")
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

        //result.getResponse().getContentAsString()
        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.groupBy").isArray())
                .andExpect(jsonPath("$.groupBy").isEmpty())
                .andExpect(jsonPath("$.style").value("apa"))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results").isNotEmpty())
                .andExpect(jsonPath("$.results[0].uuid").value(item.getID().toString()))
                .andExpect(jsonPath("$.results[0].handle").value(item.getHandle()))
                .andExpect(jsonPath("$.results[0].type").value("Journal Article"))
                .andExpect(jsonPath("$.results[0].collection").value("Test Collection"))
                .andExpect(jsonPath("$.results[0].year").value("2021"))
                .andExpect(jsonPath("$.results[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results[0].cslItem").isNotEmpty());
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
}


