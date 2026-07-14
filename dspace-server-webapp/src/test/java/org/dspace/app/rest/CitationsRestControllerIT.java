/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.apache.commons.codec.CharEncoding.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EntityTypeBuilder;
import org.dspace.builder.GroupBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.eperson.Group;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;

/**
 * Integration test for {@link CitationsRestController}.
 *
 * @author  Daniele Ninfo (daniele.ninfo at 4science.com)
 */
public class CitationsRestControllerIT extends AbstractControllerIntegrationTest {

    private String loggedInToken;
    private String adminToken;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();
        loggedInToken = getAuthToken(eperson.getEmail(), password);
        adminToken = getAuthToken(admin.getEmail(), password);
        context.restoreAuthSystemState();
    }

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
    public void getCitationsWithGrouping() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        Item journalArticle1 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Journal article of 2020")
                .withIssueDate("2020-10-10")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        Item journalArticle2 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Journal article of 2021")
                .withIssueDate("2021-10-10")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        Item thesis1 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Thesis of 2021")
                .withIssueDate("2021-10-10")
                .withType("text::thesis")
                .inArchive()
                .build();

        Item thesis2 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Thesis of 2022")
                .withIssueDate("2022-10-10")
                .withType("text::thesis")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        // Group by type

        String bodyGroupByType = "{" +
                "\"uuids\":[\"" + journalArticle1.getID() + "\", \"" + journalArticle2.getID() + "\", \""
                + thesis1.getID() + "\", \"" + thesis2.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"light\"," +
                "\"groupBy\":\"type\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style").doesNotExist())
                .andExpect(jsonPath("$.groupBy").exists())
                .andExpect(jsonPath("$.groupBy").value("type"))
                .andExpect(jsonPath("$.results").exists())
                .andExpect(jsonPath("$.results.thesis").exists())
                .andExpect(jsonPath("$.results.thesis").isArray())
                .andExpect(jsonPath("$.results.thesis", hasSize(2)))
                .andExpect(jsonPath("$.results.thesis[0].uuid").value(thesis1.getID().toString()))
                .andExpect(jsonPath("$.results.thesis[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.thesis[1].uuid").value(thesis2.getID().toString()))
                .andExpect(jsonPath("$.results.thesis[1].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.article-journal").exists())
                .andExpect(jsonPath("$.results.article-journal").isArray())
                .andExpect(jsonPath("$.results.article-journal", hasSize(2)))
                .andExpect(jsonPath("$.results.article-journal[0].uuid").value(journalArticle1.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.article-journal[1].uuid").value(journalArticle2.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[1].citation").isNotEmpty());

        // Group by year

        String bodyGroupByYear = "{" +
                "\"uuids\":[\"" + journalArticle1.getID() + "\", \"" + journalArticle2.getID() + "\", \""
                + thesis1.getID() + "\", \"" + thesis2.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"light\"," +
                "\"groupBy\":\"year\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByYear))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style").doesNotExist())
                .andExpect(jsonPath("$.groupBy").exists())
                .andExpect(jsonPath("$.groupBy").value("year"))
                .andExpect(jsonPath("$.results").exists())
                .andExpect(jsonPath("$.results.2020").exists())
                .andExpect(jsonPath("$.results.2020").isArray())
                .andExpect(jsonPath("$.results.2020", hasSize(1)))
                .andExpect(jsonPath("$.results.2020[0].uuid").value(journalArticle1.getID().toString()))
                .andExpect(jsonPath("$.results.2020[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.2021").exists())
                .andExpect(jsonPath("$.results.2021").isArray())
                .andExpect(jsonPath("$.results.2021", hasSize(2)))
                .andExpect(jsonPath("$.results.2021[0].uuid").value(journalArticle2.getID().toString()))
                .andExpect(jsonPath("$.results.2021[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.2021[1].uuid").value(thesis1.getID().toString()))
                .andExpect(jsonPath("$.results.2021[1].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.2022").exists())
                .andExpect(jsonPath("$.results.2022").isArray())
                .andExpect(jsonPath("$.results.2022", hasSize(1)))
                .andExpect(jsonPath("$.results.2022[0].uuid").value(thesis2.getID().toString()))
                .andExpect(jsonPath("$.results.2022[0].citation").isNotEmpty());

        // Group by type and year

        String bodyGroupByTypeYear = "{" +
                "\"uuids\":[\"" + journalArticle1.getID() + "\", \"" + journalArticle2.getID() + "\", \""
                + thesis1.getID() + "\", \"" + thesis2.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"light\"," +
                "\"groupBy\":\"type,year\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByTypeYear))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style").doesNotExist())
                .andExpect(jsonPath("$.groupBy").exists())
                .andExpect(jsonPath("$.groupBy").value("type,year"))
                .andExpect(jsonPath("$.results").exists())
                .andExpect(jsonPath("$.results.thesis").exists())
                .andExpect(jsonPath("$.results.thesis.2021").exists())
                .andExpect(jsonPath("$.results.thesis.2021").isArray())
                .andExpect(jsonPath("$.results.thesis.2021", hasSize(1)))
                .andExpect(jsonPath("$.results.thesis.2021[0].uuid").value(thesis1.getID().toString()))
                .andExpect(jsonPath("$.results.thesis.2021[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.thesis.2022").exists())
                .andExpect(jsonPath("$.results.thesis.2022").isArray())
                .andExpect(jsonPath("$.results.thesis.2022", hasSize(1)))
                .andExpect(jsonPath("$.results.thesis.2022[0].uuid").value(thesis2.getID().toString()))
                .andExpect(jsonPath("$.results.thesis.2022[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.article-journal").exists())
                .andExpect(jsonPath("$.results.article-journal.2021").exists())
                .andExpect(jsonPath("$.results.article-journal.2021").isArray())
                .andExpect(jsonPath("$.results.article-journal.2021", hasSize(1)))
                .andExpect(jsonPath("$.results.article-journal.2021[0].uuid").value(journalArticle2.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal.2021[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.article-journal.2020").exists())
                .andExpect(jsonPath("$.results.article-journal.2020").isArray())
                .andExpect(jsonPath("$.results.article-journal.2020", hasSize(1)))
                .andExpect(jsonPath("$.results.article-journal.2020[0].uuid").value(journalArticle1.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal.2020[0].citation").isNotEmpty());

        // Group by year and type

        String bodyGroupByYearType = "{" +
                "\"uuids\":[\"" + journalArticle1.getID() + "\", \"" + journalArticle2.getID() + "\", \""
                + thesis1.getID() + "\", \"" + thesis2.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"light\"," +
                "\"groupBy\":\"year,type\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByYearType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style").doesNotExist())
                .andExpect(jsonPath("$.groupBy").exists())
                .andExpect(jsonPath("$.groupBy").value("year,type"))
                .andExpect(jsonPath("$.results").exists())
                .andExpect(jsonPath("$.results.2021").exists())
                .andExpect(jsonPath("$.results.2021.thesis").exists())
                .andExpect(jsonPath("$.results.2021.thesis").isArray())
                .andExpect(jsonPath("$.results.2021.thesis", hasSize(1)))
                .andExpect(jsonPath("$.results.2021.thesis[0].uuid").value(thesis1.getID().toString()))
                .andExpect(jsonPath("$.results.2021.thesis[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.2022.thesis").exists())
                .andExpect(jsonPath("$.results.2022.thesis").isArray())
                .andExpect(jsonPath("$.results.2022.thesis", hasSize(1)))
                .andExpect(jsonPath("$.results.2022.thesis[0].uuid").value(thesis2.getID().toString()))
                .andExpect(jsonPath("$.results.2022.thesis[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.2021").exists())
                .andExpect(jsonPath("$.results.2021.article-journal").exists())
                .andExpect(jsonPath("$.results.2021.article-journal").isArray())
                .andExpect(jsonPath("$.results.2021.article-journal", hasSize(1)))
                .andExpect(jsonPath("$.results.2021.article-journal[0].uuid").value(journalArticle2.getID().toString()))
                .andExpect(jsonPath("$.results.2021.article-journal[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results.2020.article-journal").exists())
                .andExpect(jsonPath("$.results.2020.article-journal").isArray())
                .andExpect(jsonPath("$.results.2020.article-journal", hasSize(1)))
                .andExpect(jsonPath("$.results.2020.article-journal[0].uuid").value(journalArticle1.getID().toString()))
                .andExpect(jsonPath("$.results.2020.article-journal[0].citation").isNotEmpty());
    }

    @Test
    public void getCitationsWithGroupingAndSorting() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        Item journalArticle1 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("A")
                .withIssueDate("2020-10-10")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        Item journalArticle2 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("B")
                .withIssueDate("2021-10-10")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        Item journalArticle3 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("C")
                .withIssueDate("2019-10-10")
                .withType("text::journal::journal article")
                .inArchive()
                .build();


        Item thesis1 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("D")
                .withIssueDate("2021-10-10")
                .withType("text::thesis")
                .inArchive()
                .build();

        Item thesis2 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("E")
                .withIssueDate("2022-10-10")
                .withType("text::thesis")
                .inArchive()
                .build();

        Item thesis3 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("F")
                .withIssueDate("2022-10-11")
                .withType("text::thesis")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        // Group by type, sort by year (default)

        String bodyGroupByTypeSortByYear = "{" +
                "\"uuids\":[\"" + journalArticle1.getID() +
                "\", \"" + journalArticle2.getID() +
                "\", \"" + journalArticle3.getID() +
                "\", \"" + thesis1.getID() +
                "\", \"" + thesis2.getID() +
                "\", \"" + thesis3.getID() +
                "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"groupBy\":\"type\"," +
                "\"sort\":\"date\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByTypeSortByYear))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").exists())
                .andExpect(jsonPath("$.results.thesis").exists())
                .andExpect(jsonPath("$.results.thesis").isArray())
                .andExpect(jsonPath("$.results.thesis", hasSize(3)))
                .andExpect(jsonPath("$.results.thesis[0].uuid").value(thesis1.getID().toString()))
                .andExpect(jsonPath("$.results.thesis[1].uuid").value(thesis2.getID().toString()))
                .andExpect(jsonPath("$.results.thesis[2].uuid").value(thesis3.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal").exists())
                .andExpect(jsonPath("$.results.article-journal").isArray())
                .andExpect(jsonPath("$.results.article-journal", hasSize(3)))
                .andExpect(jsonPath("$.results.article-journal[0].uuid").value(journalArticle3.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[1].uuid").value(journalArticle1.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[2].uuid").value(journalArticle2.getID().toString()))
                ;


        // Group by type, sort by title (default)

        String bodyGroupByTypeSortByTitle = "{" +
                "\"uuids\":[\"" + journalArticle1.getID() +
                "\", \"" + journalArticle2.getID() +
                "\", \"" + journalArticle3.getID() +
                "\", \"" + thesis1.getID() +
                "\", \"" + thesis2.getID() +
                "\", \"" + thesis3.getID() +
                "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"groupBy\":\"type\"," +
                "\"sort\":\"title\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByTypeSortByTitle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").exists())
                .andExpect(jsonPath("$.results.thesis").exists())
                .andExpect(jsonPath("$.results.thesis").isArray())
                .andExpect(jsonPath("$.results.thesis", hasSize(3)))
                .andExpect(jsonPath("$.results.thesis[0].uuid").value(thesis1.getID().toString()))
                .andExpect(jsonPath("$.results.thesis[1].uuid").value(thesis2.getID().toString()))
                .andExpect(jsonPath("$.results.thesis[2].uuid").value(thesis3.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal").exists())
                .andExpect(jsonPath("$.results.article-journal").isArray())
                .andExpect(jsonPath("$.results.article-journal", hasSize(3)))
                .andExpect(jsonPath("$.results.article-journal[0].uuid").value(journalArticle1.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[1].uuid").value(journalArticle2.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[2].uuid").value(journalArticle3.getID().toString()))
        ;

        // Group by year, sort by title (default)

        String bodyGroupByYearSortByTitle = "{" +
                "\"uuids\":[\"" + journalArticle1.getID() +
                "\", \"" + journalArticle2.getID() +
                "\", \"" + journalArticle3.getID() +
                "\", \"" + thesis1.getID() +
                "\", \"" + thesis2.getID() +
                "\", \"" + thesis3.getID() +
                "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"groupBy\":\"year\"," +
                "\"sort\":\"title\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByYearSortByTitle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").exists())
                .andExpect(jsonPath("$.results.2019").exists())
                .andExpect(jsonPath("$.results.2019").isArray())
                .andExpect(jsonPath("$.results.2019", hasSize(1)))
                .andExpect(jsonPath("$.results.2019[0].uuid").value(journalArticle3.getID().toString()))
                .andExpect(jsonPath("$.results.2020").exists())
                .andExpect(jsonPath("$.results.2020").isArray())
                .andExpect(jsonPath("$.results.2020", hasSize(1)))
                .andExpect(jsonPath("$.results.2020[0].uuid").value(journalArticle1.getID().toString()))
                .andExpect(jsonPath("$.results.2021").exists())
                .andExpect(jsonPath("$.results.2021").isArray())
                .andExpect(jsonPath("$.results.2021", hasSize(2)))
                .andExpect(jsonPath("$.results.2021[0].uuid").value(journalArticle2.getID().toString()))
                .andExpect(jsonPath("$.results.2021[1].uuid").value(thesis1.getID().toString()))
                .andExpect(jsonPath("$.results.2022").exists())
                .andExpect(jsonPath("$.results.2022").isArray())
                .andExpect(jsonPath("$.results.2022", hasSize(2)))
                .andExpect(jsonPath("$.results.2022[0].uuid").value(thesis2.getID().toString()))
                .andExpect(jsonPath("$.results.2022[1].uuid").value(thesis3.getID().toString()))
        ;


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
        Collection publicationCollection2 = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Publications2")
                .withEntityType("Publication")
                .build();
        Collection personCollection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("People")
                .withEntityType("Person")
                .build();

        // The following two go in publicationCollection
        Item publication1 = ItemBuilder.createItem(context, publicationCollection)
                .withType("text::journal::journal article")
                .withTitle("Test Journal Article")
                .withIssueDate("2017-10-17")
                .withAuthor("Doe, John")
                .withSubject("ExtraEntry")
                .build();

        Item publication2 = ItemBuilder.createItem(context, publicationCollection)
                .withType("text::journal::journal article")
                .withTitle("Another test Journal Article")
                .withIssueDate("2018-10-17")
                .withAuthor("Doe, John")
                .withSubject("ExtraEntry2")
                .build();

        // This one goes in publicationCollection2
        Item publication3 = ItemBuilder.createItem(context, publicationCollection2)
                .withType("text::journal::journal article")
                .withTitle("Another test Journal Article")
                .withIssueDate("2018-10-17")
                .withAuthor("Doe, John")
                .withSubject("ExtraEntry2")
                .build();

        // This item isn't related to publications
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
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication1.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication2.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication3.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(person.getID().toString()))));

        String searchWithQueryBody = "{" +
                "\"query\":\"dc.subject:ExtraEntry\"," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchWithQueryBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication1.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication2.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication3.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(person.getID().toString()))));

        String onlyResearchOutputsWithScopeBody = "{" +
                "\"configuration\":\"researchoutputs\"," +
                "\"scope\":\"" + publicationCollection.getID() + "\"," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(onlyResearchOutputsWithScopeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication1.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication2.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(publication3.getID().toString()))))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(person.getID().toString()))));

        String onlyResearchOutputsWithScopeAndQueryBody = "{" +
                "\"configuration\":\"researchoutputs\"," +
                "\"scope\":\"" + publicationCollection.getID() + "\"," +
                "\"query\":\"dc.title:Another test\"," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(onlyResearchOutputsWithScopeAndQueryBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(publication1.getID().toString()))))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication2.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(publication3.getID().toString()))))
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
                .andExpect(jsonPath("$.results[*].uuid",
                        hasItem(matchingResearchOutputInOtherScope.getID().toString())))
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
