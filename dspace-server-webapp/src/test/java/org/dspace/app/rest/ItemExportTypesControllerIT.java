/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.builder.ItemBuilder.createItem;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.GroupBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.eperson.Group;
import org.dspace.services.ConfigurationService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Integration test for {@link ItemExportTypesController}.
 *
 * @author  daniele.ninfo at 4science.com
 */
public class ItemExportTypesControllerIT extends AbstractControllerIntegrationTest {

    @Autowired
    private ConfigurationService configurationService;

    @Test
    public void getExportTypesReturnsNotFoundForUnknownItem() throws Exception {
        getClient().perform(get("/api/core/items/{uuid}/export-types", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getExportTypesReturnsAvailableFormatsForPublication() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("A Test Publication")
                .build();

        context.restoreAuthSystemState();

        getClient().perform(get("/api/core/items/{uuid}/export-types", item.getID()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("publication-apa")))
                .andExpect(content().string(containsString("publication-iso690")));
    }

    @Test
    public void getExportTypesAppliesCitationFilterConfigurationForPublication() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("A Test Publication")
                .build();

        String key = "citation-filter.publication";
        String oldValue = configurationService.getProperty(key);
        configurationService.setProperty(key, "publication-apa, publication-ieee");
        context.restoreAuthSystemState();

        try {
            getClient().perform(get("/api/core/items/{uuid}/export-types", item.getID()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("publication-apa")))
                    .andExpect(content().string(containsString("publication-ieee")))
                    .andExpect(content().string(not(containsString("publication-xls"))));
        } finally {
            configurationService.setProperty(key, oldValue == null ? "" : oldValue);
        }
    }

    @Test
    public void getExportTypesReturnsAvailableFormatsForProduct() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Product")
                .withTitle("A Test Product")
                .build();

        context.restoreAuthSystemState();

        getClient().perform(get("/api/core/items/{uuid}/export-types", item.getID()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("product-apa")))
                .andExpect(content().string(containsString("product-chicago")));
    }

    @Test
    public void getExportTypesReturnsAvailableFormatsForPatent() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Patent")
                .withTitle("A Test Patent")
                .build();

        context.restoreAuthSystemState();

        getClient().perform(get("/api/core/items/{uuid}/export-types", item.getID()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("patent-apa")))
                .andExpect(content().string(containsString("patent-vancouver")));
    }

    @Test
    public void exportByTypeReturnsCitationContentForPublication() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Item item = createItem(context, collection)
                .withTitle("Publication title")
                .withEntityType("Publication")
                .withIssueDate("2018-05-17")
                .withHandle("123456789/0004")
                .withType("text::report::technical report", "report-coar-types:c_18ws")
                .withAuthor("John Smith")
                .withAuthor("Edward Red")
                .build();

        context.restoreAuthSystemState();

        getClient().perform(get("/api/core/items/{uuid}/export-types/{exportType}",
                        item.getID(), "publication-chicago"))
                .andExpect(status().isOk())
                .andExpect(content().string(not("")));
    }

    @Test
    public void exportByTypeReturnsCitationContentForProduct() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Product")
                .withTitle("A Test Product")
                .withAuthor("Doe, John")
                .build();

        context.restoreAuthSystemState();

        getClient().perform(get("/api/core/items/{uuid}/export-types/{exportType}", item.getID(), "product-apa"))
                .andExpect(status().isOk())
                .andExpect(content().string(not("")));
    }

    @Test
    public void exportByTypeReturnsCitationContentForPatent() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Patent")
                .withTitle("A Test Patent")
                .withAuthor("Doe, John")
                .build();

        context.restoreAuthSystemState();

        getClient().perform(get("/api/core/items/{uuid}/export-types/{exportType}", item.getID(), "patent-ieee"))
                .andExpect(status().isOk())
                .andExpect(content().string(not("")));
    }

    @Test
    public void exportByTypeAllReturnsJsonWithAllExports() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Item item = createItem(context, collection)
                .withTitle("Publication title")
                .withEntityType("Publication")
                .withIssueDate("2018-05-17")
                .withHandle("123456789/0004")
                .withType("text::report::technical report", "report-coar-types:c_18ws")
                .withAuthor("John Smith")
                .withAuthor("Edward Red")
                .build();

        context.restoreAuthSystemState();

        getClient().perform(get("/api/core/items/{uuid}/export-types/{exportType}", item.getID(), "all"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"publication-apa\"")))
                .andExpect(content().string(containsString("\"publication-chicago\"")))
                .andExpect(content().string(containsString("\"publication-harvard\"")))
                .andExpect(content().string(containsString("\"publication-mla\"")))
                .andExpect(content().string(containsString("\"publication-iso690\"")))
                .andExpect(content().string(containsString("\"publication-vancouver\"")));

    }

    @Test
    public void exportByTypeReturnsNotFoundForUnknownType() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("A Test Publication")
                .build();

        context.restoreAuthSystemState();

        getClient().perform(get("/api/core/items/{uuid}/export-types/{exportType}", item.getID(), "unknown-type"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getExportTypesReturnsUnauthorizedForRestrictedItem() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        Group restrictedGroup = GroupBuilder.createGroup(context).withName("RestrictedReaders").build();

        Item item = ItemBuilder.createItem(context, collection)
                .withEntityType("Publication")
                .withTitle("Restricted publication")
                .withReaderGroup(restrictedGroup)
                .build();

        context.restoreAuthSystemState();

        // Anonymous should not see restricted (embargo-like) items.
        getClient().perform(get("/api/core/items/{uuid}/export-types", item.getID()))
                .andExpect(status().isUnauthorized());
    }


}

