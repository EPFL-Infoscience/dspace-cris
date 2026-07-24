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

import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.GroupBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.eperson.Group;
import org.dspace.services.ConfigurationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Integration test for {@link CitationsRestController}.
 *
 * @author  Daniele Ninfo (daniele.ninfo at 4science.com)
 */
public class CitationsRestControllerIT extends AbstractControllerIntegrationTest {

    @Autowired
    private ConfigurationService configurationService;

    private String loggedInToken;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();
        loggedInToken = getAuthToken(eperson.getEmail(), password);
        context.restoreAuthSystemState();
        // Force on-the-fly generation for these tests (no pre-computed metadata)
        configurationService.setProperty("citation-rest.generate", true);
    }

    @After
    @Override
    public void destroy() throws Exception {
        configurationService.setProperty("citation-rest.generate", false);
        super.destroy();
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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
    public void getCitationsReturnsUnauthorizedWhenTokenIsMissing() throws Exception {
        String body = "{" +
                "\"uuids\":[\"00000000-0000-0000-0000-000000000000\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient().perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Unauthorized. Please provide a valid JWT token in the Authorization header"));
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByTypeSortByYear))
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
                "\"sort\":\"title:asc\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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
                .andExpect(jsonPath("$.results.2021[0].uuid").value(thesis1.getID().toString()))
                .andExpect(jsonPath("$.results.2021[1].uuid").value(journalArticle2.getID().toString()))
                .andExpect(jsonPath("$.results.2022").exists())
                .andExpect(jsonPath("$.results.2022").isArray())
                .andExpect(jsonPath("$.results.2022", hasSize(2)))
                .andExpect(jsonPath("$.results.2022[0].uuid").value(thesis3.getID().toString()))
                .andExpect(jsonPath("$.results.2022[1].uuid").value(thesis2.getID().toString()))
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchWithQueryBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication1.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication2.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication3.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(person.getID().toString()))));

        String searchWithScopeBody = "{" +
                "\"scope\":\"" + publicationCollection2.getID() + "\"," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchWithScopeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(publication1.getID().toString()))))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(publication2.getID().toString()))))
                .andExpect(jsonPath("$.results[*].uuid", hasItem(publication3.getID().toString())))
                .andExpect(jsonPath("$.results[*].uuid", not(hasItem(person.getID().toString()))));

        String onlyResearchOutputsWithScopeBody = "{" +
                "\"configuration\":\"researchoutputs\"," +
                "\"scope\":\"" + publicationCollection.getID() + "\"," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(titleSortBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].uuid").value(zebraItem.getID().toString()))
                .andExpect(jsonPath("$.results[1].uuid").value(middleItem.getID().toString()))
                .andExpect(jsonPath("$.results[2].uuid").value(alphaItem.getID().toString()));

        String titleSortAscBody = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"title:asc\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(titleSortAscBody))
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
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

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yearSortBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].uuid").value(alphaItem.getID().toString()))
                .andExpect(jsonPath("$.results[1].uuid").value(middleItem.getID().toString()))
                .andExpect(jsonPath("$.results[2].uuid").value(zebraItem.getID().toString()));
    }

    @Test
    public void getCitationsAppliesMultiSortWithoutGrouping() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        // Two items share the same date but differ in title
        Item itemA = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Alpha")
                .withIssueDate("2021-06-01")
                .inArchive()
                .build();

        Item itemB = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Beta")
                .withIssueDate("2021-06-01")
                .inArchive()
                .build();

        Item itemC = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Gamma")
                .withIssueDate("2020-01-01")
                .inArchive()
                .build();

        Item itemD = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Delta")
                .withIssueDate("2022-12-01")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String uuidsJson = "\"" + itemA.getID() + "\",\"" + itemB.getID() + "\",\""
                + itemC.getID() + "\",\"" + itemD.getID() + "\"";

        // Multi-sort: date ascending first, then title ascending as tie-breaker
        String multiSortBody = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"date:asc,title:asc\"" +
                "}";

        // Expected order: itemC (2020), then itemA (2021, Alpha) before itemB (2021, Beta), then itemD (2022)
        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(multiSortBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(4)))
                .andExpect(jsonPath("$.results[0].uuid").value(itemC.getID().toString()))
                .andExpect(jsonPath("$.results[1].uuid").value(itemA.getID().toString()))
                .andExpect(jsonPath("$.results[2].uuid").value(itemB.getID().toString()))
                .andExpect(jsonPath("$.results[3].uuid").value(itemD.getID().toString()));

        // Multi-sort: date descending first, then title descending as tie-breaker
        String multiSortDescBody = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"date:desc,title:desc\"" +
                "}";

        // Expected order: itemD (2022), then itemB (2021, Beta) before itemA (2021, Alpha), then itemC (2020)
        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(multiSortDescBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(4)))
                .andExpect(jsonPath("$.results[0].uuid").value(itemD.getID().toString()))
                .andExpect(jsonPath("$.results[1].uuid").value(itemB.getID().toString()))
                .andExpect(jsonPath("$.results[2].uuid").value(itemA.getID().toString()))
                .andExpect(jsonPath("$.results[3].uuid").value(itemC.getID().toString()));

        // Multi-sort: title ascending first, then date descending as tie-breaker
        String multiSortTitleDateBody = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"title:asc,date:desc\"" +
                "}";

        // Expected order: Alpha, Beta, Delta, Gamma (all titles unique, date is irrelevant tie-breaker)
        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(multiSortTitleDateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(4)))
                .andExpect(jsonPath("$.results[0].uuid").value(itemA.getID().toString()))
                .andExpect(jsonPath("$.results[1].uuid").value(itemB.getID().toString()))
                .andExpect(jsonPath("$.results[2].uuid").value(itemD.getID().toString()))
                .andExpect(jsonPath("$.results[3].uuid").value(itemC.getID().toString()));
    }

    @Test
    public void getCitationsAppliesMultiSortWithSingleLevelGrouping() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        // Articles with same date but different titles
        Item article1 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Zulu Article")
                .withIssueDate("2021-05-01")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        Item article2 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Alpha Article")
                .withIssueDate("2021-05-01")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        Item article3 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Middle Article")
                .withIssueDate("2020-01-01")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        // Theses
        Item thesis1 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Beta Thesis")
                .withIssueDate("2022-03-01")
                .withType("text::thesis")
                .inArchive()
                .build();

        Item thesis2 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Alpha Thesis")
                .withIssueDate("2022-03-01")
                .withType("text::thesis")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String uuidsJson = "\"" + article1.getID() + "\",\"" + article2.getID() + "\",\""
                + article3.getID() + "\",\"" + thesis1.getID() + "\",\"" + thesis2.getID() + "\"";

        // Group by type, multi-sort: date asc, then title asc
        String body = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"groupBy\":\"type\"," +
                "\"sort\":\"date:asc,title:asc\"" +
                "}";

        // article-journal group: article3 (2020), then article2 (2021, Alpha) before article1 (2021, Zulu)
        // thesis group: thesis2 (2022, Alpha Thesis) before thesis1 (2022, Beta Thesis)
        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("type"))
                .andExpect(jsonPath("$.results.article-journal", hasSize(3)))
                .andExpect(jsonPath("$.results.article-journal[0].uuid").value(article3.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[1].uuid").value(article2.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[2].uuid").value(article1.getID().toString()))
                .andExpect(jsonPath("$.results.thesis", hasSize(2)))
                .andExpect(jsonPath("$.results.thesis[0].uuid").value(thesis2.getID().toString()))
                .andExpect(jsonPath("$.results.thesis[1].uuid").value(thesis1.getID().toString()));

        // Group by year, multi-sort: title desc, then date asc (date is tie-breaker within same year)
        String bodyGroupByYear = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"groupBy\":\"year\"," +
                "\"sort\":\"title:desc,date:asc\"" +
                "}";

        // 2020 group: article3 (only one)
        // 2021 group: article1 (Zulu) before article2 (Alpha) — title desc
        // 2022 group: thesis1 (Beta) before thesis2 (Alpha) — title desc
        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByYear))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("year"))
                .andExpect(jsonPath("$.results.2020", hasSize(1)))
                .andExpect(jsonPath("$.results.2020[0].uuid").value(article3.getID().toString()))
                .andExpect(jsonPath("$.results.2021", hasSize(2)))
                .andExpect(jsonPath("$.results.2021[0].uuid").value(article1.getID().toString()))
                .andExpect(jsonPath("$.results.2021[1].uuid").value(article2.getID().toString()))
                .andExpect(jsonPath("$.results.2022", hasSize(2)))
                .andExpect(jsonPath("$.results.2022[0].uuid").value(thesis1.getID().toString()))
                .andExpect(jsonPath("$.results.2022[1].uuid").value(thesis2.getID().toString()));
    }

    @Test
    public void getCitationsAppliesMultiSortWithTwoLevelGrouping() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        Item article2021A = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Zebra Article 2021")
                .withIssueDate("2021-06-01")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        Item article2021B = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Alpha Article 2021")
                .withIssueDate("2021-03-01")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        Item thesis2021 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Only Thesis 2021")
                .withIssueDate("2021-09-01")
                .withType("text::thesis")
                .inArchive()
                .build();

        Item article2022 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Lonely Article 2022")
                .withIssueDate("2022-01-01")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        Item thesis2022A = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Beta Thesis 2022")
                .withIssueDate("2022-11-01")
                .withType("text::thesis")
                .inArchive()
                .build();

        Item thesis2022B = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Alpha Thesis 2022")
                .withIssueDate("2022-02-01")
                .withType("text::thesis")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String uuidsJson = "\"" + article2021A.getID() + "\",\"" + article2021B.getID() + "\",\""
                + thesis2021.getID() + "\",\"" + article2022.getID() + "\",\""
                + thesis2022A.getID() + "\",\"" + thesis2022B.getID() + "\"";

        // Group by type,year with multi-sort: date:asc,title:asc
        String bodyTypeYear = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"groupBy\":\"type,year\"," +
                "\"sort\":\"date:asc,title:asc\"" +
                "}";

        // article-journal.2021: article2021B (2021-03, Alpha) before article2021A (2021-06, Zebra)
        // article-journal.2022: article2022 (only one)
        // thesis.2021: thesis2021 (only one)
        // thesis.2022: thesis2022B (2022-02, Alpha) before thesis2022A (2022-11, Beta)
        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyTypeYear))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("type,year"))
                .andExpect(jsonPath("$.results.article-journal.2021", hasSize(2)))
                .andExpect(jsonPath("$.results.article-journal.2021[0].uuid")
                        .value(article2021B.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal.2021[1].uuid")
                        .value(article2021A.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal.2022", hasSize(1)))
                .andExpect(jsonPath("$.results.article-journal.2022[0].uuid")
                        .value(article2022.getID().toString()))
                .andExpect(jsonPath("$.results.thesis.2021", hasSize(1)))
                .andExpect(jsonPath("$.results.thesis.2021[0].uuid")
                        .value(thesis2021.getID().toString()))
                .andExpect(jsonPath("$.results.thesis.2022", hasSize(2)))
                .andExpect(jsonPath("$.results.thesis.2022[0].uuid")
                        .value(thesis2022B.getID().toString()))
                .andExpect(jsonPath("$.results.thesis.2022[1].uuid")
                        .value(thesis2022A.getID().toString()));

        // Group by year,type with multi-sort: title:asc,date:desc
        String bodyYearType = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"groupBy\":\"year,type\"," +
                "\"sort\":\"title:asc,date:desc\"" +
                "}";

        // 2021.article-journal: article2021B (Alpha) before article2021A (Zebra)
        // 2021.thesis: thesis2021 (only one)
        // 2022.article-journal: article2022 (only one)
        // 2022.thesis: thesis2022B (Alpha) before thesis2022A (Beta)
        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyYearType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("year,type"))
                .andExpect(jsonPath("$.results.2021.article-journal", hasSize(2)))
                .andExpect(jsonPath("$.results.2021.article-journal[0].uuid")
                        .value(article2021B.getID().toString()))
                .andExpect(jsonPath("$.results.2021.article-journal[1].uuid")
                        .value(article2021A.getID().toString()))
                .andExpect(jsonPath("$.results.2021.thesis", hasSize(1)))
                .andExpect(jsonPath("$.results.2021.thesis[0].uuid")
                        .value(thesis2021.getID().toString()))
                .andExpect(jsonPath("$.results.2022.article-journal", hasSize(1)))
                .andExpect(jsonPath("$.results.2022.article-journal[0].uuid")
                        .value(article2022.getID().toString()))
                .andExpect(jsonPath("$.results.2022.thesis", hasSize(2)))
                .andExpect(jsonPath("$.results.2022.thesis[0].uuid")
                        .value(thesis2022B.getID().toString()))
                .andExpect(jsonPath("$.results.2022.thesis[1].uuid")
                        .value(thesis2022A.getID().toString()));
    }

    @Test
    public void getCitationsRejectsBadMultiSortSyntax() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("A Test Publication")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        // Invalid: empty clause after comma
        String invalidEmptyClause = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"date:asc,\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEmptyClause))
                .andExpect(status().isBadRequest());

        // Invalid: unknown sort field in second clause
        String invalidField = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"date:asc,author:desc\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidField))
                .andExpect(status().isBadRequest());

        // Invalid: bad order value
        String invalidOrder = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"title:asc,date:up\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidOrder))
                .andExpect(status().isBadRequest());

        // Valid: single sort still works
        String validSingle = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"title\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSingle))
                .andExpect(status().isOk());

        // Valid: multi-sort without explicit directions
        String validMultiNoDir = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"date,title\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMultiNoDir))
                .andExpect(status().isOk());
    }

    @Test
    public void getCitationsMaintainsCorrectCitationToItemMapping() throws Exception {
        // This test verifies that each item's citation corresponds to that item,
        // even when the CSL bibliography internally reorders entries (e.g. by author name).
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        // Title order (asc): Alpha < Beta < Gamma < Delta
        // Author order: Zorro > Martin > Abel > York
        // Date order: 2020, 2021, 2021, 2022
        // Types: article-journal, thesis, article-journal, thesis
        // CSL bibliography might reorder by author internally
        Item itemAlpha = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::journal::journal article")
                .withTitle("Alpha Paper")
                .withAuthor("Zorro, Antonio")
                .withIssueDate("2020-03-15")
                .inArchive()
                .build();

        Item itemBeta = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::thesis")
                .withTitle("Beta Paper")
                .withAuthor("Martin, Bob")
                .withIssueDate("2021-06-10")
                .inArchive()
                .build();

        Item itemGamma = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::journal::journal article")
                .withTitle("Gamma Paper")
                .withAuthor("Abel, Charlie")
                .withIssueDate("2021-09-20")
                .inArchive()
                .build();

        Item itemDelta = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::thesis")
                .withTitle("Delta Paper")
                .withAuthor("York, Diana")
                .withIssueDate("2022-01-05")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String uuidsJson = "\"" + itemAlpha.getID() + "\",\"" + itemBeta.getID() + "\",\""
                + itemGamma.getID() + "\",\"" + itemDelta.getID() + "\"";

        // --- Case 1: Full format, sort by title ascending ---
        String bodyTitleAsc = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"title:asc\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyTitleAsc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(4)))
                .andExpect(jsonPath("$.results[0].uuid").value(itemAlpha.getID().toString()))
                .andExpect(jsonPath("$.results[0].cslItem.items[0].title").value("Alpha Paper"))
                .andExpect(jsonPath("$.results[0].citation").value(
                        org.hamcrest.Matchers.containsString("Zorro")))
                .andExpect(jsonPath("$.results[1].uuid").value(itemBeta.getID().toString()))
                .andExpect(jsonPath("$.results[1].cslItem.items[0].title").value("Beta Paper"))
                .andExpect(jsonPath("$.results[1].citation").value(
                        org.hamcrest.Matchers.containsString("Martin")))
                .andExpect(jsonPath("$.results[2].uuid").value(itemDelta.getID().toString()))
                .andExpect(jsonPath("$.results[2].cslItem.items[0].title").value("Delta Paper"))
                .andExpect(jsonPath("$.results[2].citation").value(
                        org.hamcrest.Matchers.containsString("York")))
                .andExpect(jsonPath("$.results[3].uuid").value(itemGamma.getID().toString()))
                .andExpect(jsonPath("$.results[3].cslItem.items[0].title").value("Gamma Paper"))
                .andExpect(jsonPath("$.results[3].citation").value(
                        org.hamcrest.Matchers.containsString("Abel")));

        // --- Case 2: Full format, sort by date descending ---
        String bodyDateDesc = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"date:desc\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyDateDesc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(4)))
                .andExpect(jsonPath("$.results[0].uuid").value(itemDelta.getID().toString()))
                .andExpect(jsonPath("$.results[0].citation").value(
                        org.hamcrest.Matchers.containsString("York")))
                .andExpect(jsonPath("$.results[3].uuid").value(itemAlpha.getID().toString()))
                .andExpect(jsonPath("$.results[3].citation").value(
                        org.hamcrest.Matchers.containsString("Zorro")));

        // --- Case 3: Light format, sort by title ascending ---
        String bodyLightTitleAsc = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"light\"," +
                "\"sort\":\"title:asc\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyLightTitleAsc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(4)))
                .andExpect(jsonPath("$.results[0].uuid").value(itemAlpha.getID().toString()))
                .andExpect(jsonPath("$.results[0].citation").value(
                        org.hamcrest.Matchers.containsString("Zorro")))
                .andExpect(jsonPath("$.results[1].uuid").value(itemBeta.getID().toString()))
                .andExpect(jsonPath("$.results[1].citation").value(
                        org.hamcrest.Matchers.containsString("Martin")))
                .andExpect(jsonPath("$.results[2].uuid").value(itemDelta.getID().toString()))
                .andExpect(jsonPath("$.results[2].citation").value(
                        org.hamcrest.Matchers.containsString("York")))
                .andExpect(jsonPath("$.results[3].uuid").value(itemGamma.getID().toString()))
                .andExpect(jsonPath("$.results[3].citation").value(
                        org.hamcrest.Matchers.containsString("Abel")));

        // --- Case 4: Full format, groupBy type, sort by title ascending ---
        String bodyGroupByType = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"groupBy\":\"type\"," +
                "\"sort\":\"title:asc\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.article-journal", hasSize(2)))
                // article-journal sorted by title: Alpha (Zorro), Gamma (Abel)
                .andExpect(jsonPath("$.results.article-journal[0].uuid").value(itemAlpha.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[0].citation").value(
                        org.hamcrest.Matchers.containsString("Zorro")))
                .andExpect(jsonPath("$.results.article-journal[1].uuid").value(itemGamma.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[1].citation").value(
                        org.hamcrest.Matchers.containsString("Abel")))
                .andExpect(jsonPath("$.results.thesis", hasSize(2)))
                // thesis sorted by title: Beta (Martin), Delta (York)
                .andExpect(jsonPath("$.results.thesis[0].uuid").value(itemBeta.getID().toString()))
                .andExpect(jsonPath("$.results.thesis[0].citation").value(
                        org.hamcrest.Matchers.containsString("Martin")))
                .andExpect(jsonPath("$.results.thesis[1].uuid").value(itemDelta.getID().toString()))
                .andExpect(jsonPath("$.results.thesis[1].citation").value(
                        org.hamcrest.Matchers.containsString("York")));

        // --- Case 5: Full format, groupBy year, sort by title ascending ---
        String bodyGroupByYear = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"groupBy\":\"year\"," +
                "\"sort\":\"title:asc\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByYear))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.2020", hasSize(1)))
                .andExpect(jsonPath("$.results.2020[0].uuid").value(itemAlpha.getID().toString()))
                .andExpect(jsonPath("$.results.2020[0].citation").value(
                        org.hamcrest.Matchers.containsString("Zorro")))
                .andExpect(jsonPath("$.results.2021", hasSize(2)))
                // 2021 sorted by title: Beta (Martin), Gamma (Abel)
                .andExpect(jsonPath("$.results.2021[0].uuid").value(itemBeta.getID().toString()))
                .andExpect(jsonPath("$.results.2021[0].citation").value(
                        org.hamcrest.Matchers.containsString("Martin")))
                .andExpect(jsonPath("$.results.2021[1].uuid").value(itemGamma.getID().toString()))
                .andExpect(jsonPath("$.results.2021[1].citation").value(
                        org.hamcrest.Matchers.containsString("Abel")))
                .andExpect(jsonPath("$.results.2022", hasSize(1)))
                .andExpect(jsonPath("$.results.2022[0].uuid").value(itemDelta.getID().toString()))
                .andExpect(jsonPath("$.results.2022[0].citation").value(
                        org.hamcrest.Matchers.containsString("York")));

        // --- Case 6: Light format, groupBy type, sort by date ascending ---
        String bodyLightGroupByType = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"light\"," +
                "\"groupBy\":\"type\"," +
                "\"sort\":\"date:asc\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyLightGroupByType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.article-journal", hasSize(2)))
                // article-journal sorted by date asc: Alpha (2020, Zorro), Gamma (2021, Abel)
                .andExpect(jsonPath("$.results.article-journal[0].uuid").value(itemAlpha.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[0].citation").value(
                        org.hamcrest.Matchers.containsString("Zorro")))
                .andExpect(jsonPath("$.results.article-journal[1].uuid").value(itemGamma.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[1].citation").value(
                        org.hamcrest.Matchers.containsString("Abel")))
                .andExpect(jsonPath("$.results.thesis", hasSize(2)))
                // thesis sorted by date asc: Beta (2021, Martin), Delta (2022, York)
                .andExpect(jsonPath("$.results.thesis[0].uuid").value(itemBeta.getID().toString()))
                .andExpect(jsonPath("$.results.thesis[0].citation").value(
                        org.hamcrest.Matchers.containsString("Martin")))
                .andExpect(jsonPath("$.results.thesis[1].uuid").value(itemDelta.getID().toString()))
                .andExpect(jsonPath("$.results.thesis[1].citation").value(
                        org.hamcrest.Matchers.containsString("York")));

        // --- Case 7: Full format, groupBy type,year, sort by title ascending ---
        String bodyGroupByTypeYear = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"groupBy\":\"type,year\"," +
                "\"sort\":\"title:asc\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyGroupByTypeYear))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.article-journal.2020", hasSize(1)))
                .andExpect(jsonPath("$.results.article-journal.2020[0].uuid").value(itemAlpha.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal.2020[0].citation").value(
                        org.hamcrest.Matchers.containsString("Zorro")))
                .andExpect(jsonPath("$.results.article-journal.2021", hasSize(1)))
                .andExpect(jsonPath("$.results.article-journal.2021[0].uuid").value(itemGamma.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal.2021[0].citation").value(
                        org.hamcrest.Matchers.containsString("Abel")))
                .andExpect(jsonPath("$.results.thesis.2021", hasSize(1)))
                .andExpect(jsonPath("$.results.thesis.2021[0].uuid").value(itemBeta.getID().toString()))
                .andExpect(jsonPath("$.results.thesis.2021[0].citation").value(
                        org.hamcrest.Matchers.containsString("Martin")))
                .andExpect(jsonPath("$.results.thesis.2022", hasSize(1)))
                .andExpect(jsonPath("$.results.thesis.2022[0].uuid").value(itemDelta.getID().toString()))
                .andExpect(jsonPath("$.results.thesis.2022[0].citation").value(
                        org.hamcrest.Matchers.containsString("York")));

        // --- Case 8: Full format, multi-sort date:asc,title:desc ---
        String bodyMultiSort = "{" +
                "\"uuids\":[" + uuidsJson + "]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"sort\":\"date:asc,title:desc\"" +
                "}";

        // date asc: Alpha(2020), then Beta(2021-06) and Gamma(2021-09) by title desc: Gamma before Beta,
        // then Delta(2022)
        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMultiSort))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(4)))
                .andExpect(jsonPath("$.results[0].uuid").value(itemAlpha.getID().toString()))
                .andExpect(jsonPath("$.results[0].citation").value(
                        org.hamcrest.Matchers.containsString("Zorro")))
                .andExpect(jsonPath("$.results[3].uuid").value(itemDelta.getID().toString()))
                .andExpect(jsonPath("$.results[3].citation").value(
                        org.hamcrest.Matchers.containsString("York")));
    }

    // ===== Tests reading pre-computed citations from epfl.citation.* metadata =====

    @Test
    public void getCitationsFromMetadataReturnsPreComputedCitation() throws Exception {
        configurationService.setProperty("citation-rest.generate", false);

        context.turnOffAuthorisationSystem();

        ItemService itemService = ContentServiceFactory.getInstance().getItemService();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Precomputed Collection")
                .build();

        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::journal::journal article")
                .withTitle("Precomputed Publication")
                .withAuthor("Smith, Jane")
                .withIssueDate("2023-03-15")
                .inArchive()
                .build();

        // Simulate pre-computed citation metadata
        itemService.addMetadata(context, item, "epfl", "citation", "apa", null,
                "<div>Smith, J. (2023). Precomputed Publication.</div>");
        itemService.addMetadata(context, item, "epfl", "citation", "cslitem", null,
                "{\"items\":[{\"id\":\"" + item.getID() + "\",\"type\":\"article-journal\","
                + "\"title\":\"Precomputed Publication\"}]}");
        itemService.update(context, item);

        context.restoreAuthSystemState();

        String body = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].uuid").value(item.getID().toString()))
                .andExpect(jsonPath("$.results[0].citation").value(
                        org.hamcrest.Matchers.containsString("Precomputed Publication")))
                .andExpect(jsonPath("$.results[0].type").value("article-journal"))
                .andExpect(jsonPath("$.results[0].cslItem").isNotEmpty())
                .andExpect(jsonPath("$.results[0].cslItem.items[0].title").value("Precomputed Publication"));
    }

    @Test
    public void getCitationsFromMetadataSkipsItemsWithoutPreComputedCitation() throws Exception {
        configurationService.setProperty("citation-rest.generate", false);

        context.turnOffAuthorisationSystem();

        ItemService itemService = ContentServiceFactory.getInstance().getItemService();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Mixed Collection")
                .build();

        Item itemWithCitation = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::journal::journal article")
                .withTitle("With Citation")
                .withIssueDate("2023-01-01")
                .inArchive()
                .build();
        itemService.addMetadata(context, itemWithCitation, "epfl", "citation", "apa", null,
                "<div>With Citation. (2023).</div>");
        itemService.addMetadata(context, itemWithCitation, "epfl", "citation", "cslitem", null,
                "{\"items\":[{\"id\":\"" + itemWithCitation.getID()
                + "\",\"type\":\"article-journal\",\"title\":\"With Citation\"}]}");
        itemService.update(context, itemWithCitation);

        Item itemWithoutCitation = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::journal::journal article")
                .withTitle("Without Citation")
                .withIssueDate("2023-02-01")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        String body = "{" +
                "\"uuids\":[\"" + itemWithCitation.getID() + "\",\"" + itemWithoutCitation.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"light\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].uuid").value(itemWithCitation.getID().toString()));
    }

    @Test
    public void getCitationsFromMetadataWithGroupByTypeAndSort() throws Exception {
        configurationService.setProperty("citation-rest.generate", false);

        context.turnOffAuthorisationSystem();

        ItemService itemService = ContentServiceFactory.getInstance().getItemService();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Grouped Collection")
                .build();

        Item article2021 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::journal::journal article")
                .withTitle("Article 2021")
                .withIssueDate("2021-05-01")
                .inArchive()
                .build();
        itemService.addMetadata(context, article2021, "epfl", "citation", "apa", null,
                "<div>Article 2021 citation</div>");
        itemService.addMetadata(context, article2021, "epfl", "citation", "cslitem", null,
                "{\"items\":[{\"id\":\"" + article2021.getID()
                + "\",\"type\":\"article-journal\",\"title\":\"Article 2021\"}]}");
        itemService.update(context, article2021);

        Item thesis2020 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::thesis")
                .withTitle("Thesis 2020")
                .withIssueDate("2020-03-01")
                .inArchive()
                .build();
        itemService.addMetadata(context, thesis2020, "epfl", "citation", "apa", null,
                "<div>Thesis 2020 citation</div>");
        itemService.addMetadata(context, thesis2020, "epfl", "citation", "cslitem", null,
                "{\"items\":[{\"id\":\"" + thesis2020.getID()
                + "\",\"type\":\"thesis\",\"title\":\"Thesis 2020\"}]}");
        itemService.update(context, thesis2020);

        Item article2019 = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::journal::journal article")
                .withTitle("Article 2019")
                .withIssueDate("2019-01-01")
                .inArchive()
                .build();
        itemService.addMetadata(context, article2019, "epfl", "citation", "apa", null,
                "<div>Article 2019 citation</div>");
        itemService.addMetadata(context, article2019, "epfl", "citation", "cslitem", null,
                "{\"items\":[{\"id\":\"" + article2019.getID()
                + "\",\"type\":\"article-journal\",\"title\":\"Article 2019\"}]}");
        itemService.update(context, article2019);

        context.restoreAuthSystemState();

        // Group by type, sort by date:asc
        String body = "{" +
                "\"uuids\":[\"" + article2021.getID() + "\",\"" + thesis2020.getID()
                + "\",\"" + article2019.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"," +
                "\"groupBy\":\"type\"," +
                "\"sort\":\"date\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("type"))
                .andExpect(jsonPath("$.results.article-journal").isArray())
                .andExpect(jsonPath("$.results.article-journal", hasSize(2)))
                .andExpect(jsonPath("$.results.article-journal[0].uuid").value(article2019.getID().toString()))
                .andExpect(jsonPath("$.results.article-journal[1].uuid").value(article2021.getID().toString()))
                .andExpect(jsonPath("$.results.thesis").isArray())
                .andExpect(jsonPath("$.results.thesis", hasSize(1)))
                .andExpect(jsonPath("$.results.thesis[0].uuid").value(thesis2020.getID().toString()));

        // Group by year
        String bodyByYear = "{" +
                "\"uuids\":[\"" + article2021.getID() + "\",\"" + thesis2020.getID()
                + "\",\"" + article2019.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"light\"," +
                "\"groupBy\":\"year\"," +
                "\"sort\":\"date\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyByYear))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("year"))
                .andExpect(jsonPath("$.results.2019", hasSize(1)))
                .andExpect(jsonPath("$.results.2019[0].uuid").value(article2019.getID().toString()))
                .andExpect(jsonPath("$.results.2020", hasSize(1)))
                .andExpect(jsonPath("$.results.2020[0].uuid").value(thesis2020.getID().toString()))
                .andExpect(jsonPath("$.results.2021", hasSize(1)))
                .andExpect(jsonPath("$.results.2021[0].uuid").value(article2021.getID().toString()));
    }

    @Test
    public void getCitationsEndToEndWithScriptAndRestController() throws Exception {
        configurationService.setProperty("citation-rest.generate", false);

        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("E2E Collection")
                .withEntityType("Publication")
                .build();

        Item item = ItemBuilder.createItem(context, collection)
                .withTitle("End-to-End Publication")
                .withAuthor("Rossi, Mario")
                .withIssueDate("2024-01-15")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        // Run the citation-metadata script to pre-compute citations
        String[] scriptArgs = new String[] {
            "citation-metadata", "-i", item.getID().toString(), "-f"
        };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        ScriptLauncher.handleScript(scriptArgs, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);
        if (handler.getException() != null) {
            throw handler.getException();
        }

        // Now query the REST controller — it should read from metadata
        String body = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].uuid").value(item.getID().toString()))
                .andExpect(jsonPath("$.results[0].citation").value(
                        org.hamcrest.Matchers.containsString("End-to-End Publication")))
                .andExpect(jsonPath("$.results[0].citation").value(
                        org.hamcrest.Matchers.containsString("Rossi")))
                .andExpect(jsonPath("$.results[0].type").value("article-journal"))
                .andExpect(jsonPath("$.results[0].year").value("2024"))
                .andExpect(jsonPath("$.results[0].cslItem").isNotEmpty());
    }

    @Test
    public void getCitationsGeneratesAllConfiguredStyles() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Styles Collection")
                .withEntityType("Publication")
                .build();

        Item item = ItemBuilder.createItem(context, collection)
                .withTitle("Multi-Style Publication")
                .withAuthor("Doe, John")
                .withIssueDate("2023-06-01")
                .withType("text::journal::journal article")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        // citation-rest.generate is true from setUp — generates on the fly
        String[] styles = {"apa", "chicago", "ieee", "vancouver", "harvard", "mla", "iso690"};

        for (String style : styles) {
            String body = "{" +
                    "\"uuids\":[\"" + item.getID() + "\"]," +
                    "\"style\":\"" + style + "\"," +
                    "\"format\":\"light\"" +
                    "}";

            getClient(loggedInToken).perform(post("/api/integration/citations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results", hasSize(1)))
                    .andExpect(jsonPath("$.results[0].uuid").value(item.getID().toString()))
                    .andExpect(jsonPath("$.results[0].citation").isNotEmpty());
        }
    }

    @Test
    public void getCitationsFullFormatContainsAllExpectedFields() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Full Fields Collection")
                .build();

        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::journal::journal article")
                .withTitle("Fields Verification Publication")
                .withAuthor("Einstein, Albert")
                .withIssueDate("2022-11-20")
                .inArchive()
                .build();

        context.restoreAuthSystemState();

        // citation-rest.generate is true from setUp — generates on the fly
        String bodyFull = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"full\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyFull))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style").value("apa"))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results", hasSize(1)))
                // CitationItemFull fields
                .andExpect(jsonPath("$.results[0].uuid").value(item.getID().toString()))
                .andExpect(jsonPath("$.results[0].handle").value(item.getHandle()))
                .andExpect(jsonPath("$.results[0].type").value("article-journal"))
                .andExpect(jsonPath("$.results[0].collection").value("Full Fields Collection"))
                .andExpect(jsonPath("$.results[0].year").value("2022"))
                .andExpect(jsonPath("$.results[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results[0].citation").value(
                        org.hamcrest.Matchers.containsString("Einstein")))
                .andExpect(jsonPath("$.results[0].cslItem").isNotEmpty())
                .andExpect(jsonPath("$.results[0].cslItem.items").isArray())
                .andExpect(jsonPath("$.results[0].cslItem.items[0].id").value(item.getID().toString()))
                .andExpect(jsonPath("$.results[0].cslItem.items[0].type").value("article-journal"))
                .andExpect(jsonPath("$.results[0].cslItem.items[0].title").value("Fields Verification Publication"));

        // CitationItemLight fields — only uuid and citation
        String bodyLight = "{" +
                "\"uuids\":[\"" + item.getID() + "\"]," +
                "\"style\":\"apa\"," +
                "\"format\":\"light\"" +
                "}";

        getClient(loggedInToken).perform(post("/api/integration/citations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyLight))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style").doesNotExist())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results", hasSize(1)))
                // CitationItemLight fields
                .andExpect(jsonPath("$.results[0].uuid").value(item.getID().toString()))
                .andExpect(jsonPath("$.results[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.results[0].citation").value(
                        org.hamcrest.Matchers.containsString("Einstein")))
                // Fields NOT present in light format
                .andExpect(jsonPath("$.results[0].handle").doesNotExist())
                .andExpect(jsonPath("$.results[0].type").doesNotExist())
                .andExpect(jsonPath("$.results[0].collection").doesNotExist())
                .andExpect(jsonPath("$.results[0].year").doesNotExist())
                .andExpect(jsonPath("$.results[0].cslItem").doesNotExist());
    }
}
