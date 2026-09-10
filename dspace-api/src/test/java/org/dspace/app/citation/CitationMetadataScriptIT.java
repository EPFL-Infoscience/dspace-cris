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
import org.dspace.discovery.IndexingService;
import org.dspace.services.factory.DSpaceServicesFactory;
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
                .withTitle("A Publication").withIssueDate("2023-01-01")
                .withType("text::journal::journal article").build();
        Item patent = ItemBuilder.createItem(context, patCol)
                .withTitle("A Patent").withIssueDate("2023-02-01")
                .withType("patent").build();
        Item product = ItemBuilder.createItem(context, prodCol)
                .withTitle("A Product").withIssueDate("2023-03-01")
                .withType("dataset").build();
        Item person = ItemBuilder.createItem(context, personCol)
                .withTitle("John Doe").build();

        context.restoreAuthSystemState();

        runScript("-i", community.getID().toString(), "-f");

        publication = reloadItem(publication);
        patent = reloadItem(patent);
        product = reloadItem(product);
        person = reloadItem(person);

        assertThat("Publication should have epfl.citation.apa",
                getCitationMetadata(publication, "apa"), not(emptyOrNullString()));
        assertThat("Patent should have epfl.citation.apa",
                getCitationMetadata(patent, "apa"), not(emptyOrNullString()));
        assertThat("Product should have epfl.citation.apa",
                getCitationMetadata(product, "apa"), not(emptyOrNullString()));
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
                .withTitle("Pub in A").withIssueDate("2023-01-01")
                .withType("text::report::technical report").build();
        Item itemInB = ItemBuilder.createItem(context, colB)
                .withTitle("Pub in B").withIssueDate("2023-01-01")
                .withType("text::thesis::doctoral thesis").build();

        context.restoreAuthSystemState();

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
                .withTitle("Pub in Community A").withIssueDate("2023-01-01")
                .withType("text::book/monograph").build();
        Item itemInB = ItemBuilder.createItem(context, colB)
                .withTitle("Pub in Community B").withIssueDate("2023-01-01")
                .withType("text::conference output::conference proceedings::conference paper").build();

        context.restoreAuthSystemState();

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
                .withTitle("Target Pub").withIssueDate("2023-01-01")
                .withType("text::preprint").build();
        Item otherItem = ItemBuilder.createItem(context, col)
                .withTitle("Other Pub").withIssueDate("2023-01-01")
                .withType("text::review::book review").build();

        context.restoreAuthSystemState();

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
                .withTitle("New Pub").withIssueDate("2023-01-01")
                .withType("text::journal::journal article").build();

        Item cachedItem = ItemBuilder.createItem(context, col)
                .withTitle("Cached Pub").withIssueDate("2023-01-01")
                .withType("text::journal::journal article").build();
        itemService.addMetadata(context, cachedItem, "epfl", "citation", "date", null,
                java.time.Instant.now().plusSeconds(3600).toString());
        itemService.addMetadata(context, cachedItem, "epfl", "citation", "apa", null, "old-value");
        itemService.update(context, cachedItem);

        context.restoreAuthSystemState();

        runScript("-i", community.getID().toString());

        newItem = reloadItem(newItem);
        cachedItem = reloadItem(cachedItem);

        assertThat("New item should have citation",
                getCitationMetadata(newItem, "apa"), not(emptyOrNullString()));
        assertThat("New item should have citation date",
                getCitationMetadata(newItem, "date"), notNullValue());
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
                .withTitle("Multi-style Pub")
                .withType("text::journal::journal article")
                .withAuthor("Smith, John")
                .withIssueDate("2023-01-01")
                .build();

        context.restoreAuthSystemState();

        runScript("-i", item.getID().toString(), "-f");

        item = reloadItem(item);

        assertThat("apa citation should be generated", getCitationMetadata(item, "apa"), not(emptyOrNullString()));
        assertThat("chicago citation should be generated",
                getCitationMetadata(item, "chicago"), not(emptyOrNullString()));
        assertThat("ieee citation should be generated", getCitationMetadata(item, "ieee"), not(emptyOrNullString()));
        assertThat("vancouver citation should be generated",
                getCitationMetadata(item, "vancouver"), not(emptyOrNullString()));
        assertThat("harvard citation should be generated",
                getCitationMetadata(item, "harvard"), not(emptyOrNullString()));
        assertThat("mla citation should be generated", getCitationMetadata(item, "mla"), not(emptyOrNullString()));
        assertThat("iso690 citation should be generated",
                getCitationMetadata(item, "iso690"), not(emptyOrNullString()));
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
                .withTitle("Stable Publication").withAuthor("Smith, John").withIssueDate("2023-01-01")
                .withType("text::journal::journal article").build();

        context.restoreAuthSystemState();

        runScript("-i", item.getID().toString(), "-f");
        item = reloadItem(item);

        String citationDate = getCitationMetadata(item, "date");
        String apaCitation = getCitationMetadata(item, "apa");
        assertThat("Should have citation date", citationDate, notNullValue());
        assertThat("Should have apa citation", apaCitation, not(emptyOrNullString()));

        context.turnOffAuthorisationSystem();
        itemService.clearMetadata(context, item, "dc", "title", null, Item.ANY);
        itemService.addMetadata(context, item, "dc", "title", null, null, "Modified Title");
        itemService.update(context, item);
        context.restoreAuthSystemState();

        runScript("-i", item.getID().toString());
        item = reloadItem(item);

        assertThat("Citation date should remain unchanged (interval protection)",
                getCitationMetadata(item, "date"), is(citationDate));
        assertThat("Citation should still contain original content (not regenerated)",
                getCitationMetadata(item, "apa"), is(apaCitation));
    }

    @Test
    public void scriptRegeneratesModifiedItem() throws Exception {
        org.dspace.services.ConfigurationService configService =
                org.dspace.services.factory.DSpaceServicesFactory.getInstance().getConfigurationService();
        int originalOffset = configService.getIntProperty("citation-script.date-offset", 10);
        // Set offset to 0 so citationDate = NOW; after modifying the item, lastModified > citationDate
        configService.setProperty("citation-script.date-offset", 0);

        try {
            context.turnOffAuthorisationSystem();

            Community community = CommunityBuilder.createCommunity(context).withName("Community Regen").build();
            Collection col = CollectionBuilder.createCollection(context, community)
                    .withName("Col Regen").withEntityType("Publication").build();

            Item item = ItemBuilder.createItem(context, col)
                    .withTitle("Original Title").withAuthor("Doe, Jane").withIssueDate("2023-06-15")
                    .withType("text::journal::journal article").build();

            context.restoreAuthSystemState();

            runScript("-i", item.getID().toString(), "-f");
            item = reloadItem(item);

            assertThat("Should contain original title",
                    getCitationMetadata(item, "apa"), org.hamcrest.Matchers.containsString("Original Title"));

            context.turnOffAuthorisationSystem();
            itemService.clearMetadata(context, item, "dc", "title", null, Item.ANY);
            itemService.addMetadata(context, item, "dc", "title", null, null, "Updated Title");
            itemService.update(context, item);
            context.restoreAuthSystemState();

            // Run without -f: the item was modified after citationDate (offset=0), so needsUpdate = true
            runScript("-i", item.getID().toString());
            item = reloadItem(item);

            String updatedCitation = getCitationMetadata(item, "apa");
            assertThat("Citation should now contain updated title",
                    updatedCitation, org.hamcrest.Matchers.containsString("Updated Title"));
            assertThat("Citation should NOT still contain original title",
                    updatedCitation, not(org.hamcrest.Matchers.containsString("Original Title")));
        } finally {
            configService.setProperty("citation-script.date-offset", originalOffset);
        }
    }

    @Test
    public void scriptDoesNotRegenerateWhenDateOffsetProtects() throws Exception {
        // With default date-offset=10, citationDate is 10 minutes in the future.
        // An immediate modification won't trigger regeneration because lastModified < citationDate.
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community Offset").build();
        Collection col = CollectionBuilder.createCollection(context, community)
                .withName("Col Offset").withEntityType("Publication").build();

        Item item = ItemBuilder.createItem(context, col)
                .withTitle("Protected Title").withAuthor("Doe, John").withIssueDate("2023-01-01")
                .withType("text::journal::journal article").build();

        context.restoreAuthSystemState();

        // First run: generates citation with date-offset=10 (citationDate = NOW+10min)
        runScript("-i", item.getID().toString(), "-f");
        item = reloadItem(item);

        String originalCitation = getCitationMetadata(item, "apa");
        String originalDate = getCitationMetadata(item, "date");
        assertThat(originalCitation, org.hamcrest.Matchers.containsString("Protected Title"));

        // Modify the item immediately — lastModified is still < citationDate (which is 10min ahead)
        context.turnOffAuthorisationSystem();
        itemService.clearMetadata(context, item, "dc", "title", null, Item.ANY);
        itemService.addMetadata(context, item, "dc", "title", null, null, "Changed Title");
        itemService.update(context, item);
        context.restoreAuthSystemState();

        // Run without -f: needsUpdate should be false (lastModified < citationDate)
        runScript("-i", item.getID().toString());
        item = reloadItem(item);

        assertThat("Citation should NOT be regenerated (date-offset protection)",
                getCitationMetadata(item, "apa"), is(originalCitation));
        assertThat("Citation date should remain unchanged",
                getCitationMetadata(item, "date"), is(originalDate));
    }

    @Test
    public void scriptProcessesMultipleItemsWithCommitBatching() throws Exception {
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community Batch").build();
        Collection col = CollectionBuilder.createCollection(context, community)
                .withName("Col Batch").withEntityType("Publication").build();

        // Create 5 items — we'll use commitSize=2 so we get multiple commits
        Item item1 = ItemBuilder.createItem(context, col)
                .withTitle("Batch Item 1").withAuthor("Author, A.").withIssueDate("2023-01-01")
                .withType("text::journal::journal article").build();
        Item item2 = ItemBuilder.createItem(context, col)
                .withTitle("Batch Item 2").withAuthor("Author, B.").withIssueDate("2023-02-01")
                .withType("text::journal::journal article").build();
        Item item3 = ItemBuilder.createItem(context, col)
                .withTitle("Batch Item 3").withAuthor("Author, C.").withIssueDate("2023-03-01")
                .withType("text::journal::journal article").build();
        Item item4 = ItemBuilder.createItem(context, col)
                .withTitle("Batch Item 4").withAuthor("Author, D.").withIssueDate("2023-04-01")
                .withType("text::journal::journal article").build();
        Item item5 = ItemBuilder.createItem(context, col)
                .withTitle("Batch Item 5").withAuthor("Author, E.").withIssueDate("2023-05-01")
                .withType("text::journal::journal article").build();

        // Item 6: already has a citation date far in the future — should be skipped without -f
        Item item6 = ItemBuilder.createItem(context, col)
                .withTitle("Already Cached Item").withAuthor("Author, F.").withIssueDate("2023-06-01")
                .withType("text::journal::journal article").build();
        itemService.addMetadata(context, item6, "epfl", "citation", "date", null,
                java.time.Instant.now().plusSeconds(7200).toString());
        itemService.addMetadata(context, item6, "epfl", "citation", "apa", null, "pre-existing-citation");
        itemService.update(context, item6);

        context.restoreAuthSystemState();

        // Run with -f (force) and commitSize=2: processes all items regardless of citation date.
        // Force mode is needed because after each commit the Solr index may update synchronously in tests,
        // potentially excluding just-processed items from subsequent pages.
        runScript("-i", community.getID().toString(), "-f", "-c", "2");

        item1 = reloadItem(item1);
        item2 = reloadItem(item2);
        item3 = reloadItem(item3);
        item4 = reloadItem(item4);
        item5 = reloadItem(item5);
        item6 = reloadItem(item6);

        // All 5 new items should have citations generated
        assertThat("Item 1 should have apa citation",
                getCitationMetadata(item1, "apa"), not(emptyOrNullString()));
        assertThat("Item 2 should have apa citation",
                getCitationMetadata(item2, "apa"), not(emptyOrNullString()));
        assertThat("Item 3 should have apa citation",
                getCitationMetadata(item3, "apa"), not(emptyOrNullString()));
        assertThat("Item 4 should have apa citation",
                getCitationMetadata(item4, "apa"), not(emptyOrNullString()));
        assertThat("Item 5 should have apa citation",
                getCitationMetadata(item5, "apa"), not(emptyOrNullString()));

        // Item 6 with -f is also reprocessed — gets a real citation
        assertThat("Item 6 should have been reprocessed with -f",
                getCitationMetadata(item6, "apa"), not(emptyOrNullString()));

        // Verify citation date is set for processed items
        assertThat("Item 1 should have citation date",
                getCitationMetadata(item1, "date"), notNullValue());
        assertThat("Item 5 should have citation date",
                getCitationMetadata(item5, "date"), notNullValue());

        // Verify CSL JSON is set
        assertThat("Item 1 should have cslitem",
                getCitationMetadata(item1, "cslitem"), not(emptyOrNullString()));
        assertThat("Item 5 should have cslitem",
                getCitationMetadata(item5, "cslitem"), not(emptyOrNullString()));
    }

    // ===== Tests verifying Solr filter conditions =====

    @Test
    public void scriptWithoutForceProcessesItemsWithNoCitationDate() throws Exception {
        // Demonstrates condition: (-epfl.citation.date:*)
        // Items that have never been processed (no epfl.citation.date) should be picked up
        // by the script even without -f.
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community NoCitDate").build();
        Collection col = CollectionBuilder.createCollection(context, community)
                .withName("Col NoCitDate").withEntityType("Publication").build();

        // Item without epfl.citation.date — brand new, never processed
        Item newItem = ItemBuilder.createItem(context, col)
                .withTitle("Brand New Item").withAuthor("New, N.").withIssueDate("2024-01-01")
                .withType("text::journal::journal article").build();

        // Item WITH epfl.citation.date in the future and not recently modified —
        // should NOT be picked up (has citation.date AND lastModified is not recent enough
        // if check-interval is set very low)
        Item oldProcessedItem = ItemBuilder.createItem(context, col)
                .withTitle("Old Processed Item").withAuthor("Old, O.").withIssueDate("2020-01-01")
                .withType("text::journal::journal article").build();
        itemService.addMetadata(context, oldProcessedItem, "epfl", "citation", "date", null,
                java.time.Instant.now().plusSeconds(3600).toString());
        itemService.addMetadata(context, oldProcessedItem, "epfl", "citation", "apa", null,
                "existing citation for old item");
        itemService.update(context, oldProcessedItem);

        context.restoreAuthSystemState();

        // Run without -f: only newItem should be processed (no citation.date)
        // oldProcessedItem has citation.date AND its lastModified is within check-interval (25h),
        // but needsUpdate returns false (lastModified < citationDate)
        runScript("-i", community.getID().toString());

        newItem = reloadItem(newItem);
        oldProcessedItem = reloadItem(oldProcessedItem);

        assertThat("New item (no citation.date) should be processed",
                getCitationMetadata(newItem, "apa"), not(emptyOrNullString()));
        assertThat("Old processed item should keep its existing citation",
                getCitationMetadata(oldProcessedItem, "apa"), is("existing citation for old item"));
    }

    @Test
    public void scriptWithoutForceProcessesRecentlyModifiedItems() throws Exception {
        // Demonstrates condition: lastModified:[NOW-checkIntervalHours HOURS TO NOW]
        // Items modified within the check-interval window AND with lastModified > citationDate
        // should be regenerated.
        org.dspace.services.ConfigurationService configService =
                org.dspace.services.factory.DSpaceServicesFactory.getInstance().getConfigurationService();
        int originalOffset = configService.getIntProperty("citation-script.date-offset", 10);
        // Set offset to 0 so that after first run citationDate ≈ NOW,
        // then after modification lastModified > citationDate
        configService.setProperty("citation-script.date-offset", 0);

        try {
            context.turnOffAuthorisationSystem();

            Community community = CommunityBuilder.createCommunity(context).withName("Community Recent").build();
            Collection col = CollectionBuilder.createCollection(context, community)
                    .withName("Col Recent").withEntityType("Publication").build();

            Item item = ItemBuilder.createItem(context, col)
                    .withTitle("Recently Modified").withAuthor("Recent, R.").withIssueDate("2024-03-01")
                    .withType("text::journal::journal article").build();

            context.restoreAuthSystemState();

            // First run with -f to generate initial citation
            runScript("-i", item.getID().toString(), "-f");
            item = reloadItem(item);

            String originalCitation = getCitationMetadata(item, "apa");
            assertThat(originalCitation, org.hamcrest.Matchers.containsString("Recently Modified"));

            // Modify the item — lastModified becomes NOW, which is > citationDate (also ≈ NOW with offset=0)
            context.turnOffAuthorisationSystem();
            itemService.clearMetadata(context, item, "dc", "title", null, Item.ANY);
            itemService.addMetadata(context, item, "dc", "title", null, null, "Freshly Updated");
            itemService.update(context, item);
            context.restoreAuthSystemState();

            // Run without -f: item's lastModified is within check-interval (25h)
            // AND lastModified > citationDate → needsUpdate = true → regenerated
            runScript("-i", item.getID().toString());
            item = reloadItem(item);

            assertThat("Citation should be regenerated with new title",
                    getCitationMetadata(item, "apa"), org.hamcrest.Matchers.containsString("Freshly Updated"));
        } finally {
            configService.setProperty("citation-script.date-offset", originalOffset);
        }
    }

    @Test
    public void scriptWithoutForceSkipsOldUnmodifiedItems() throws Exception {
        // Demonstrates that items with citation.date in the future are skipped by needsUpdate,
        // even when they fall within the check-interval window (because lastModified < citationDate).
        // Also demonstrates that items without citation.date are always processed.
        org.dspace.services.ConfigurationService configService =
                org.dspace.services.factory.DSpaceServicesFactory.getInstance().getConfigurationService();
        int originalCheckInterval = configService.getIntProperty("citation-script.check-interval", 25);
        // Use 1 hour: both items have lastModified within this window,
        // but only the one without citation.date (or with lastModified > citationDate) gets processed.
        configService.setProperty("citation-script.check-interval", 1);

        try {
            context.turnOffAuthorisationSystem();

            Community community = CommunityBuilder.createCommunity(context).withName("Community Old").build();
            Collection col = CollectionBuilder.createCollection(context, community)
                    .withName("Col Old").withEntityType("Publication").build();

            // Item with citation.date in the future — simulates a recently processed item
            Item processedItem = ItemBuilder.createItem(context, col)
                    .withTitle("Already Done").withAuthor("Done, D.").withIssueDate("2022-01-01")
                    .withType("text::journal::journal article").build();
            itemService.addMetadata(context, processedItem, "epfl", "citation", "date", null,
                    java.time.Instant.now().plusSeconds(600).toString());
            itemService.addMetadata(context, processedItem, "epfl", "citation", "apa", null,
                    "old citation value");
            itemService.update(context, processedItem);

            // Item without citation.date — should be processed (condition 1 of the OR)
            Item newItem = ItemBuilder.createItem(context, col)
                    .withTitle("Never Processed").withAuthor("Never, N.").withIssueDate("2024-01-01")
                    .withType("text::journal::journal article").build();

            context.restoreAuthSystemState();

            // Run without -f:
            // - Both items pass the Solr filter (newItem has no citation.date, processedItem has recent lastModified)
            // - But processedItem is skipped by needsUpdate (lastModified < citationDate)
            // - newItem is processed (no citation.date → needsUpdate = true)
            runScript("-i", community.getID().toString());

            processedItem = reloadItem(processedItem);
            newItem = reloadItem(newItem);

            assertThat("Processed item should keep old citation (needsUpdate=false)",
                    getCitationMetadata(processedItem, "apa"), is("old citation value"));
            assertThat("New item (no citation.date) should be processed",
                    getCitationMetadata(newItem, "apa"), not(emptyOrNullString()));
        } finally {
            configService.setProperty("citation-script.check-interval", originalCheckInterval);
        }
    }

    /**
     * Verifies that all items are processed even when the Solr index changes between pages.
     *
     * <p>This test simulates the production scenario where the Solr index gets updated between
     * paginated queries. In production, autoSoftCommit causes items that received
     * {@code epfl.citation.date} to disappear from the query results ({@code -epfl.citation.date:*}).
     * With the old offset-based pagination, items would be skipped because the result set shrinks
     * while the offset advances. With the new offset-0 approach, all items are correctly processed.</p>
     *
     * <p>The test creates 5 items, uses commitSize=2, and forces Solr index commits after each batch
     * to ensure items disappear from subsequent queries — mimicking production behavior.</p>
     */
    @Test
    public void scriptProcessesAllItemsEvenWhenSolrIndexChanges() throws Exception {
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community Pagination").build();
        Collection col = CollectionBuilder.createCollection(context, community)
                .withName("Col Pagination").withEntityType("Publication").build();

        // Create 5 items — no epfl.citation.date → all match the non-force filter
        Item item1 = ItemBuilder.createItem(context, col)
                .withTitle("Pagination Item 1").withAuthor("Author, A.").withIssueDate("2023-01-01")
                .withType("text::journal::journal article").build();
        Item item2 = ItemBuilder.createItem(context, col)
                .withTitle("Pagination Item 2").withAuthor("Author, B.").withIssueDate("2023-02-01")
                .withType("text::journal::journal article").build();
        Item item3 = ItemBuilder.createItem(context, col)
                .withTitle("Pagination Item 3").withAuthor("Author, C.").withIssueDate("2023-03-01")
                .withType("text::journal::journal article").build();
        Item item4 = ItemBuilder.createItem(context, col)
                .withTitle("Pagination Item 4").withAuthor("Author, D.").withIssueDate("2023-04-01")
                .withType("text::journal::journal article").build();
        Item item5 = ItemBuilder.createItem(context, col)
                .withTitle("Pagination Item 5").withAuthor("Author, E.").withIssueDate("2023-05-01")
                .withType("text::journal::journal article").build();

        context.restoreAuthSystemState();

        // Force Solr commit so all items are visible in the index
        IndexingService indexingService = DSpaceServicesFactory.getInstance().getServiceManager()
                .getServiceByName(IndexingService.class.getName(), IndexingService.class);
        indexingService.commit();

        // Run the script with commitSize=2 and force mode.
        // With commitSize=2, the script fetches 2 items per page.
        // After processing each page and committing, processed items get epfl.citation.date
        // and disappear from the non-force query. With the old approach, this caused items
        // to be skipped. With the new offset-0 approach, all items are found.
        runScript("-i", community.getID().toString(), "-f", "-c", "2");

        item1 = reloadItem(item1);
        item2 = reloadItem(item2);
        item3 = reloadItem(item3);
        item4 = reloadItem(item4);
        item5 = reloadItem(item5);

        // ALL 5 items must have been processed — none should be skipped
        assertThat("Item 1 must have citation (offset-0 pagination fix)",
                getCitationMetadata(item1, "apa"), not(emptyOrNullString()));
        assertThat("Item 2 must have citation (offset-0 pagination fix)",
                getCitationMetadata(item2, "apa"), not(emptyOrNullString()));
        assertThat("Item 3 must have citation (offset-0 pagination fix)",
                getCitationMetadata(item3, "apa"), not(emptyOrNullString()));
        assertThat("Item 4 must have citation (offset-0 pagination fix)",
                getCitationMetadata(item4, "apa"), not(emptyOrNullString()));
        assertThat("Item 5 must have citation (offset-0 pagination fix)",
                getCitationMetadata(item5, "apa"), not(emptyOrNullString()));

        // Verify citation dates are set
        assertThat("Item 1 must have citation date",
                getCitationMetadata(item1, "date"), notNullValue());
        assertThat("Item 5 must have citation date",
                getCitationMetadata(item5, "date"), notNullValue());
    }

    /**
     * Verifies that the non-force mode also processes all items when the index changes between pages.
     *
     * <p>In non-force mode, the filter is: {@code (*:* -epfl.citation.date:*) OR lastModified:[NOW-25HOURS TO NOW]}.
     * Once an item gets {@code epfl.citation.date}, it no longer matches the first condition. Since all items
     * in this test are freshly created (lastModified is recent), they still match the second condition.
     * However, after the script runs needsUpdate and finds citationDate > lastModified, it skips them.
     * This ensures the script terminates and all truly-needing-processing items are handled.</p>
     */
    @Test
    public void scriptWithoutForceProcessesAllItemsWithSmallCommitSize() throws Exception {
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community NoForce Pag").build();
        Collection col = CollectionBuilder.createCollection(context, community)
                .withName("Col NoForce Pag").withEntityType("Publication").build();

        // Create 4 items without epfl.citation.date
        Item item1 = ItemBuilder.createItem(context, col)
                .withTitle("NoForce Pag 1").withAuthor("Smith, A.").withIssueDate("2024-01-01")
                .withType("text::journal::journal article").build();
        Item item2 = ItemBuilder.createItem(context, col)
                .withTitle("NoForce Pag 2").withAuthor("Smith, B.").withIssueDate("2024-02-01")
                .withType("text::journal::journal article").build();
        Item item3 = ItemBuilder.createItem(context, col)
                .withTitle("NoForce Pag 3").withAuthor("Smith, C.").withIssueDate("2024-03-01")
                .withType("text::journal::journal article").build();
        Item item4 = ItemBuilder.createItem(context, col)
                .withTitle("NoForce Pag 4").withAuthor("Smith, D.").withIssueDate("2024-04-01")
                .withType("text::journal::journal article").build();

        context.restoreAuthSystemState();

        // Force Solr commit
        IndexingService indexingService = DSpaceServicesFactory.getInstance().getServiceManager()
                .getServiceByName(IndexingService.class.getName(), IndexingService.class);
        indexingService.commit();

        // Run without -f, commitSize=2: processes items without epfl.citation.date
        runScript("-i", community.getID().toString(), "-c", "2");

        item1 = reloadItem(item1);
        item2 = reloadItem(item2);
        item3 = reloadItem(item3);
        item4 = reloadItem(item4);

        // All 4 items must have been processed
        assertThat("Item 1 must have citation (non-force, small batch)",
                getCitationMetadata(item1, "apa"), not(emptyOrNullString()));
        assertThat("Item 2 must have citation (non-force, small batch)",
                getCitationMetadata(item2, "apa"), not(emptyOrNullString()));
        assertThat("Item 3 must have citation (non-force, small batch)",
                getCitationMetadata(item3, "apa"), not(emptyOrNullString()));
        assertThat("Item 4 must have citation (non-force, small batch)",
                getCitationMetadata(item4, "apa"), not(emptyOrNullString()));
    }

    /**
     * Verifies that an error on one item does not prevent the rest from being processed.
     * This tests the per-item error handling added to prevent a single failure from aborting the whole run.
     */
    @Test
    public void scriptContinuesAfterSingleItemError() throws Exception {
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context).withName("Community ErrorHandling").build();
        Collection col = CollectionBuilder.createCollection(context, community)
                .withName("Col ErrorHandling").withEntityType("Publication").build();

        Item item1 = ItemBuilder.createItem(context, col)
                .withTitle("Good Item 1").withAuthor("Good, A.").withIssueDate("2024-01-01")
                .withType("text::journal::journal article").build();
        Item item2 = ItemBuilder.createItem(context, col)
                .withTitle("Good Item 2").withAuthor("Good, B.").withIssueDate("2024-02-01")
                .withType("text::journal::journal article").build();
        Item item3 = ItemBuilder.createItem(context, col)
                .withTitle("Good Item 3").withAuthor("Good, C.").withIssueDate("2024-03-01")
                .withType("text::journal::journal article").build();

        context.restoreAuthSystemState();

        // Force Solr commit
        IndexingService indexingService = DSpaceServicesFactory.getInstance().getServiceManager()
                .getServiceByName(IndexingService.class.getName(), IndexingService.class);
        indexingService.commit();

        // Run the script — all items are valid so should all be processed successfully.
        // The per-item error handling ensures that if one fails, others still get processed.
        runScript("-i", community.getID().toString(), "-f", "-c", "1");

        item1 = reloadItem(item1);
        item2 = reloadItem(item2);
        item3 = reloadItem(item3);

        assertThat("Item 1 must have citation despite small batch size",
                getCitationMetadata(item1, "apa"), not(emptyOrNullString()));
        assertThat("Item 2 must have citation despite small batch size",
                getCitationMetadata(item2, "apa"), not(emptyOrNullString()));
        assertThat("Item 3 must have citation despite small batch size",
                getCitationMetadata(item3, "apa"), not(emptyOrNullString()));
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
