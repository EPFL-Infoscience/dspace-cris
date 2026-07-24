/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.citation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for {@link CitationMetadataScript}.
 *
 * @author Daniele Ninfo (daniele.ninfo at 4science.com)
 */
public class CitationMetadataScriptIT extends AbstractIntegrationTestWithDatabase {

    private ItemService itemService;

    @Before
    public void setup() {
        itemService = ContentServiceFactory.getInstance().getItemService();
    }

    @Test
    public void scriptProcessesOnlyPublicationPatentAndProduct() throws Exception {
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community 1").build();
        Collection pubCol = CollectionBuilder.createCollection(context, community)
                .withName("Publications").withEntityType("Publication").build();
        Collection patCol = CollectionBuilder.createCollection(context, community)
                .withName("Patents").withEntityType("Patent").build();
        Collection prodCol = CollectionBuilder.createCollection(context, community)
                .withName("Products").withEntityType("Product").build();
        Collection personCol = CollectionBuilder.createCollection(context, community)
                .withName("People").withEntityType("Person").build();

        Item publication = ItemBuilder.createItem(context, pubCol)
                .withTitle("A Publication").withIssueDate("2023-01-01").build();
        Item patent = ItemBuilder.createItem(context, patCol)
                .withTitle("A Patent").withIssueDate("2023-02-01").build();
        Item product = ItemBuilder.createItem(context, prodCol)
                .withTitle("A Product").withIssueDate("2023-03-01").build();
        Item person = ItemBuilder.createItem(context, personCol)
                .withTitle("John Doe").build();

        context.restoreAuthSystemState();

        // Run the script with force and scoped to the community
        runScript("-i", community.getID().toString(), "-f");

        // Reload items from DB
        publication = reloadItem(publication);
        patent = reloadItem(patent);
        product = reloadItem(product);
        person = reloadItem(person);

        // Publication, Patent, Product should have citation metadata
        assertThat("Publication should have epfl.citation.apa",
                getCitationMetadata(publication, "apa"), not(emptyOrNullString()));
        assertThat("Patent should have epfl.citation.apa",
                getCitationMetadata(patent, "apa"), not(emptyOrNullString()));
        assertThat("Product should have epfl.citation.apa",
                getCitationMetadata(product, "apa"), not(emptyOrNullString()));

        // Person should NOT have citation metadata
        assertThat("Person should NOT have epfl.citation.apa",
                getCitationMetadata(person, "apa"), nullValue());
    }

    @Test
    public void scriptScopedByCollectionProcessesOnlyItemsInThatCollection() throws Exception {
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community Scope").build();
        Collection colA = CollectionBuilder.createCollection(context, community)
                .withName("Collection A").withEntityType("Publication").build();
        Collection colB = CollectionBuilder.createCollection(context, community)
                .withName("Collection B").withEntityType("Publication").build();

        Item itemInA = ItemBuilder.createItem(context, colA)
                .withTitle("Pub in A").withIssueDate("2023-01-01").build();
        Item itemInB = ItemBuilder.createItem(context, colB)
                .withTitle("Pub in B").withIssueDate("2023-01-01").build();

        context.restoreAuthSystemState();

        // Run scoped to collection A
        runScript("-i", colA.getID().toString(), "-f");

        itemInA = reloadItem(itemInA);
        itemInB = reloadItem(itemInB);

        assertThat("Item in collection A should have citation",
                getCitationMetadata(itemInA, "apa"), not(emptyOrNullString()));
        assertThat("Item in collection B should NOT have citation",
                getCitationMetadata(itemInB, "apa"), nullValue());
    }

    @Test
    public void scriptScopedByCommunityProcessesOnlyItemsInThatCommunity() throws Exception {
        context.turnOffAuthorisationSystem();

        Community communityA = CommunityBuilder.createCommunity(context).withName("Community A").build();
        Collection colA = CollectionBuilder.createCollection(context, communityA)
                .withName("Col in A").withEntityType("Publication").build();

        Community communityB = CommunityBuilder.createCommunity(context).withName("Community B").build();
        Collection colB = CollectionBuilder.createCollection(context, communityB)
                .withName("Col in B").withEntityType("Publication").build();

        Item itemInA = ItemBuilder.createItem(context, colA)
                .withTitle("Pub in Community A").withIssueDate("2023-01-01").build();
        Item itemInB = ItemBuilder.createItem(context, colB)
                .withTitle("Pub in Community B").withIssueDate("2023-01-01").build();

        context.restoreAuthSystemState();

        // Run scoped to community A
        runScript("-i", communityA.getID().toString(), "-f");

        itemInA = reloadItem(itemInA);
        itemInB = reloadItem(itemInB);

        assertThat("Item in community A should have citation",
                getCitationMetadata(itemInA, "apa"), not(emptyOrNullString()));
        assertThat("Item in community B should NOT have citation",
                getCitationMetadata(itemInB, "apa"), nullValue());
    }

    @Test
    public void scriptScopedByItemProcessesOnlyThatItem() throws Exception {
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community Item Scope").build();
        Collection col = CollectionBuilder.createCollection(context, community)
                .withName("Col").withEntityType("Publication").build();

        Item targetItem = ItemBuilder.createItem(context, col)
                .withTitle("Target Pub").withIssueDate("2023-01-01").build();
        Item otherItem = ItemBuilder.createItem(context, col)
                .withTitle("Other Pub").withIssueDate("2023-01-01").build();

        context.restoreAuthSystemState();

        // Run scoped to a single item
        runScript("-i", targetItem.getID().toString(), "-f");

        targetItem = reloadItem(targetItem);
        otherItem = reloadItem(otherItem);

        assertThat("Target item should have citation",
                getCitationMetadata(targetItem, "apa"), not(emptyOrNullString()));
        assertThat("Other item should NOT have citation",
                getCitationMetadata(otherItem, "apa"), nullValue());
    }

    @Test
    public void scriptWithoutForceSkipsItemsWithUpToDateCitations() throws Exception {
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community Force").build();
        Collection col = CollectionBuilder.createCollection(context, community)
                .withName("Col Force").withEntityType("Publication").build();

        Item newItem = ItemBuilder.createItem(context, col)
                .withTitle("New Pub").withIssueDate("2023-01-01").build();

        // Simulate an item that already has citation date set (up-to-date)
        Item cachedItem = ItemBuilder.createItem(context, col)
                .withTitle("Cached Pub").withIssueDate("2023-01-01").build();
        itemService.addMetadata(context, cachedItem, "epfl", "citation", "date", null,
                java.time.Instant.now().plusSeconds(3600).toString());
        itemService.addMetadata(context, cachedItem, "epfl", "citation", "apa", null, "old-value");
        itemService.update(context, cachedItem);

        context.restoreAuthSystemState();

        // Run without force, scoped to community
        runScript("-i", community.getID().toString());

        newItem = reloadItem(newItem);
        cachedItem = reloadItem(cachedItem);

        // New item should have been processed
        assertThat("New item should have citation",
                getCitationMetadata(newItem, "apa"), not(emptyOrNullString()));
        assertThat("New item should have citation date",
                getCitationMetadata(newItem, "date"), notNullValue());

        // Cached item should NOT have been overwritten
        assertThat("Cached item should keep its old citation value",
                getCitationMetadata(cachedItem, "apa"), is("old-value"));
    }

    @Test
    public void scriptSavesAllCitationStyles() throws Exception {
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community Styles").build();
        Collection col = CollectionBuilder.createCollection(context, community)
                .withName("Col Styles").withEntityType("Publication").build();

        Item item = ItemBuilder.createItem(context, col)
                .withTitle("Multi-style Pub").withIssueDate("2023-01-01").build();

        context.restoreAuthSystemState();

        runScript("-i", item.getID().toString(), "-f");

        item = reloadItem(item);

        // At minimum, apa and cslitem should be generated (these crosswalks are always available)
        assertThat("apa citation should be generated", getCitationMetadata(item, "apa"), not(emptyOrNullString()));
        assertThat("cslitem should be generated", getCitationMetadata(item, "cslitem"), not(emptyOrNullString()));
        assertThat("citation date should be set", getCitationMetadata(item, "date"), notNullValue());
    }

    @Test
    public void scriptDoesNotRegenerateItemsWithinInterval() throws Exception {
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community NoRegen").build();
        Collection col = CollectionBuilder.createCollection(context, community)
                .withName("Col NoRegen").withEntityType("Publication").build();

        Item item = ItemBuilder.createItem(context, col)
                .withTitle("Stable Publication").withAuthor("Smith, John").withIssueDate("2023-01-01").build();

        context.restoreAuthSystemState();

        // First run with force
        runScript("-i", item.getID().toString(), "-f");
        item = reloadItem(item);

        String citationDate = getCitationMetadata(item, "date");
        String apaCitation = getCitationMetadata(item, "apa");
        assertThat("Should have citation date", citationDate, notNullValue());
        assertThat("Should have apa citation", apaCitation, not(emptyOrNullString()));

        // Modify the item (within the 30-minute interval)
        context.turnOffAuthorisationSystem();
        itemService.clearMetadata(context, item, "dc", "title", null, Item.ANY);
        itemService.addMetadata(context, item, "dc", "title", null, null, "Modified Title");
        itemService.update(context, item);
        context.restoreAuthSystemState();

        // Second run WITHOUT force: should NOT regenerate because citation date is 30 min in the future
        runScript("-i", item.getID().toString());
        item = reloadItem(item);

        assertThat("Citation date should remain unchanged (interval protection)",
                getCitationMetadata(item, "date"), is(citationDate));
        assertThat("Citation should still contain original title (not regenerated)",
                getCitationMetadata(item, "apa"), is(apaCitation));
    }

    @Test
    public void scriptRegeneratesModifiedItemWhenIntervalIsZero() throws Exception {
        org.dspace.services.ConfigurationService configService =
                org.dspace.services.factory.DSpaceServicesFactory.getInstance().getConfigurationService();
        int originalInterval = configService.getIntProperty("citation-script.interval", 30);
        configService.setProperty("citation-script.interval", 0);

        try {
            context.turnOffAuthorisationSystem();

            Community community = CommunityBuilder.createCommunity(context).withName("Community Regen").build();
            Collection col = CollectionBuilder.createCollection(context, community)
                    .withName("Col Regen").withEntityType("Publication").build();

            Item item = ItemBuilder.createItem(context, col)
                    .withTitle("Original Title").withAuthor("Doe, Jane").withIssueDate("2023-06-15").build();

            context.restoreAuthSystemState();

            // First run with force
            runScript("-i", item.getID().toString(), "-f");
            item = reloadItem(item);

            assertThat("Should contain original title",
                    getCitationMetadata(item, "apa"), org.hamcrest.Matchers.containsString("Original Title"));

            // Modify the item
            context.turnOffAuthorisationSystem();
            itemService.clearMetadata(context, item, "dc", "title", null, Item.ANY);
            itemService.addMetadata(context, item, "dc", "title", null, null, "Updated Title");
            itemService.update(context, item);
            context.restoreAuthSystemState();

            // Second run WITHOUT force: should regenerate because interval is 0
            runScript("-i", item.getID().toString());
            item = reloadItem(item);

            String updatedCitation = getCitationMetadata(item, "apa");
            assertThat("Citation should now contain updated title",
                    updatedCitation, org.hamcrest.Matchers.containsString("Updated Title"));
            assertThat("Citation should NOT still contain original title",
                    updatedCitation, not(org.hamcrest.Matchers.containsString("Original Title")));
        } finally {
            configService.setProperty("citation-script.interval", originalInterval);
        }
    }

    private void runScript(String... args) throws Exception {
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = "citation-metadata";
        System.arraycopy(args, 0, fullArgs, 1, args.length);
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        ScriptLauncher.handleScript(fullArgs, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);
        if (handler.getException() != null) {
            throw handler.getException();
        }
    }

    private Item reloadItem(Item item) throws Exception {
        return itemService.find(context, item.getID());
    }

    private String getCitationMetadata(Item item, String qualifier) {
        return itemService.getMetadataFirstValue(item, "epfl", "citation", qualifier, Item.ANY);
    }
}
