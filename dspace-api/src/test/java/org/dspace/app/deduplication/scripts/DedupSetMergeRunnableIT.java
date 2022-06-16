/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.deduplication.scripts;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.deduplication.model.DeduplicationMerge;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EntityTypeBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.RelationshipBuilder;
import org.dspace.builder.RelationshipTypeBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.EntityType;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.Relationship;
import org.dspace.content.RelationshipType;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.EntityService;
import org.dspace.content.service.EntityTypeService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.RelationshipService;
import org.dspace.content.service.RelationshipTypeService;
import org.junit.Before;
import org.junit.Test;

public class DedupSetMergeRunnableIT extends AbstractIntegrationTestWithDatabase {

    private  ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private RelationshipService relationshipService = ContentServiceFactory.getInstance().getRelationshipService();
    private RelationshipTypeService relationshipTypeService = ContentServiceFactory.getInstance()
                                                                                     .getRelationshipTypeService();
    private EntityService entityService = ContentServiceFactory.getInstance().getEntityService();
    private EntityTypeService entityTypeService = ContentServiceFactory.getInstance().getEntityTypeService();

    private Collection collection;

    private Item item1;
    private Item item2;
    private Item item3;
    private Item item4;
    private EntityType publicationEntityType;
    private RelationshipType isMergedRelationshipType;


    @Before
    public void setup() throws Exception {

        super.setUp();

        context.turnOffAuthorisationSystem();

        if (entityTypeService.findAll(context).size() < 2) {
            //Don't initialize the entity more than once
            publicationEntityType = EntityTypeBuilder.createEntityTypeBuilder(context, "Publication").build();
            isMergedRelationshipType =
                RelationshipTypeBuilder.createRelationshipTypeBuilder(context, publicationEntityType,
                    publicationEntityType, "isMergedFromItem", "isMergedInItem",
                    null, null, null, null).build();
        }

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        collection = CollectionBuilder.createCollection(context, parentCommunity)
                                      .withName("Collection 1")
                                      .withEntityType("Publication")
                                      .build();

        item1 = ItemBuilder.createItem(context, collection)
                           .withTitle("title of item1")
                           .withIssueDate("2010-10-17")
                           .withAuthor("Smith, Donald")
                           .withSubject("item1 subject 1")
                           .withSubject("item1 subject 2")
                           .withType("text1")
                           .build();

        item2 = ItemBuilder.createItem(context, collection)
                           .withTitle("title of item2")
                           .withIssueDate("2015-12-20")
                           .withAuthor("Smith 2, John")
                           .withSubject("item2 subject 1")
                           .withSubject("item2 subject 2")
                           .withType("text2")
                           .withEditor("editor2")
                           .build();

        item3 = ItemBuilder.createItem(context, collection)
                           .withTitle("title of item3")
                           .withIssueDate("2015-12-18")
                           .withAuthor("Smith 3, John")
                           .withSubject("item3 subject 1")
                           .withSubject("item3 subject 2")
                           .withType("text3")
                           .withEditor("editor3")
                           .build();

        item4 = ItemBuilder.createItem(context, collection)
                           .withTitle("title of item4")
                           .withIssueDate("2015-12-18")
                           .withAuthor("Smith", item2.getID().toString())
                           .withType("text3")
                           .build();

        context.restoreAuthSystemState();

    }

    @Test
    public void testMergeItemsIfInvalidId() throws Exception {
        String invalidId = "invalid_id";
        DeduplicationMerge deduplicationMerge =
            new DeduplicationMerge(invalidId,
                Arrays.asList(item2.getID().toString()),
                Arrays.asList("dc.subject"),
                Arrays.asList("dc.contributor.author"),
                Arrays.asList("dc.contributor.author"), true, false);

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(1));
        assertThat(handler.getErrorMessages().get(0),
            containsString("Invalid UUID string: " + invalidId));
    }

    @Test
    public void testMergeItemsOfNotExistTargetItem() throws Exception {
        String itemId = "68cd99be-fc30-4d70-b5bc-a4242cf549d5";
        DeduplicationMerge deduplicationMerge =
            new DeduplicationMerge(itemId,
                Arrays.asList(item2.getID().toString()),
                Arrays.asList("dc.subject"),
                Arrays.asList("dc.contributor.author"),
                Arrays.asList("dc.contributor.author"), true, false);

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(1));
        assertThat(handler.getErrorMessages().get(0),
            containsString("IllegalArgumentException: Item not found for id = " + itemId));
    }

    @Test
    public void testMergeItemsIfOneOfMergedItemsNotExist() throws Exception {
        String itemId = "68cd99be-fc30-4d70-b5bc-a4242cf549d5";
        DeduplicationMerge deduplicationMerge =
            new DeduplicationMerge(item1.getID().toString(),
                Arrays.asList(itemId),
                Arrays.asList("dc.subject"),
                Arrays.asList("dc.contributor.author"),
                Arrays.asList("dc.contributor.author"), true, false);

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(1));
        assertThat(handler.getErrorMessages().get(0),
            containsString("IllegalArgumentException: Item not found for id = " + itemId));
    }

    @Test
    public void testMergeItemsDuplicationOfMetadata() throws Exception {
        DeduplicationMerge deduplicationMerge =
            new DeduplicationMerge(item1.getID().toString(),
                Arrays.asList(item2.getID().toString()),
                Arrays.asList("dc.subject"),
                Arrays.asList("dc.contributor.author"),
                Arrays.asList("dc.contributor.author"), true, false);

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(1));
        assertThat(handler.getErrorMessages().get(0),
            containsString("IllegalArgumentException: duplicated metadata"));
    }

    @Test
    public void testMergeTwoItemsIfNotRepeatableMetadata() throws Exception {
        DeduplicationMerge deduplicationMerge =
            new DeduplicationMerge(item1.getID().toString(),
                Arrays.asList(item2.getID().toString()),
                Arrays.asList("dc.subject"),
                Arrays.asList("dc.contributor.author"),
                Arrays.asList("dc.title"), true, false);

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(1));
        assertThat(handler.getErrorMessages().get(0),
            containsString("IllegalArgumentException: the metadata dc.title isn't repeatable"));
    }

    @Test
    public void testMergeMultipleItemsIfNotRepeatableMetadata() throws Exception {
        DeduplicationMerge deduplicationMerge =
            new DeduplicationMerge(item1.getID().toString(),
                Arrays.asList(item2.getID().toString(), item3.getID().toString()),
                Arrays.asList("dc.subject"),
                Arrays.asList("dc.contributor.author"),
                Arrays.asList("dc.title"), true, false);

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(1));
        assertThat(handler.getErrorMessages().get(0),
            containsString("IllegalArgumentException: the metadata dc.contributor.author isn't repeatable"));
    }

    @Test
    public void testMergeTwoItemsWithExcludeIfNotRepeatableMetadata() throws Exception {
        DeduplicationMerge deduplicationMerge =
            new DeduplicationMerge(item1.getID().toString(),
                Arrays.asList(item2.getID().toString()),
                Arrays.asList("dc.subject"),
                Arrays.asList("dc.contributor.author"),
                Arrays.asList("dc.title"), true, true);
        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);
        assertThat(handler.getErrorMessages(), hasSize(0));
    }

    @Test
    public void testMergeMultipleItemsWithExcludeIfNotRepeatableMetadata() throws Exception {
        DeduplicationMerge deduplicationMerge =
            new DeduplicationMerge(item1.getID().toString(),
                Arrays.asList(item2.getID().toString(), item3.getID().toString()),
                Arrays.asList("dc.subject"),
                Arrays.asList("dc.contributor.author"),
                Arrays.asList("dc.title"), true, true);
        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);
        assertThat(handler.getErrorMessages(), hasSize(0));
    }

    @Test
    public void testMergeItemsWithExcludeOption() throws Exception {

        DeduplicationMerge deduplicationMerge = new DeduplicationMerge(item1.getID().toString(),
            Arrays.asList(item2.getID().toString(), item3.getID().toString()),
            Arrays.asList("dc.contributor.author"),
            Arrays.asList("dc.subject"),
            Arrays.asList("dc.subject.author"), true, true);

        context.turnOffAuthorisationSystem();

        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                                                 .withName("Collection 2")
                                                 .withEntityType("Author")
                                                 .build();

        Item author = ItemBuilder.createItem(context, collection)
                                 .withTitle("Author1")
                                 .build();

        RelationshipType isAuthorOfPublicationRelationshipType;

        EntityType authorEntityType =
            EntityTypeBuilder.createEntityTypeBuilder(context, "Author").build();

        isAuthorOfPublicationRelationshipType =
            RelationshipTypeBuilder.createRelationshipTypeBuilder(context, publicationEntityType, authorEntityType,
                "isAuthorOfPublication", "isPublicationOfAuthor",
                null, null, null, null).build();

//        create a relationship between author item and one of merged items item2
        Relationship relationship =
            RelationshipBuilder.createRelationshipBuilder(context, item2, author,
                isAuthorOfPublicationRelationshipType).build();

        context.restoreAuthSystemState();

        assertThat(relationship.getLeftItem().getID(), equalTo(item2.getID()));
        assertThat(relationship.getRightItem().getID(), equalTo(author.getID()));

        List<MetadataValue> metadataValue = itemService.getMetadata(item4,"dc", "contributor",
            "author", null);

//        before merge check that dc.contributor.author metadata of item4 has authority id of item2
        assertThat(metadataValue.get(0).getAuthority(), containsString(item2.getID().toString()));

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(0));

        Relationship relationship2 = relationshipService.find(context, relationship.getID());

//        after merge the relationship will be between author item and target item item1
        assertThat(relationship2.getLeftItem().getID(), equalTo(item1.getID()));
        assertThat(relationship2.getRightItem().getID(), equalTo(author.getID()));

        List<MetadataValue> metadataValue2 = itemService.getMetadata(item4,"dc", "contributor",
            "author", null);

//        after merge check that dc.contributor.author metadata of item4 has authority id of target item item1
        assertThat(metadataValue.get(0).getAuthority(), containsString(item1.getID().toString()));
    }

    @Test
    public void testMergeTwoItems() throws Exception {

        DeduplicationMerge deduplicationMerge = new DeduplicationMerge(item1.getID().toString(),
            Arrays.asList(item2.getID().toString()),
            Arrays.asList("dc.title", "dc.identifier.citation"),
            Arrays.asList("dc.contributor.author", "dc.contributor.editor"),
            Arrays.asList("dc.subject"), true, false);

        List<MetadataValue> item1TitleMetadata = itemService.getMetadata(item1,"dc", "title",
            null, null);
        List<MetadataValue> item1EditorMetadata = itemService.getMetadata(item1,"dc", "contributor",
            "editor", null);
        List<MetadataValue> item1AuthorMetadata = itemService.getMetadata(item1,"dc", "contributor",
            "author", null);
        List<MetadataValue> item1SubjectMetadata = itemService.getMetadata(item1,"dc", "subject",
            null, null);

        assertThat(item1TitleMetadata.get(0).getValue(), is("title of item1"));
        assertThat(item1EditorMetadata.size(), is(0));
        assertThat(item1AuthorMetadata.get(0).getValue(), is("Smith, Donald"));

        assertThat(item1SubjectMetadata, hasSize(2));
        assertThat(item1SubjectMetadata, containsInAnyOrder(
            hasProperty("value", is("item1 subject 1")),
            hasProperty("value", is("item1 subject 2"))
        ));

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(0));

        item1TitleMetadata = itemService.getMetadata(item1,"dc", "title",
            null, null);
        item1EditorMetadata = itemService.getMetadata(item1,"dc", "contributor",
            "editor", null);
        item1AuthorMetadata = itemService.getMetadata(item1,"dc", "contributor",
            "author", null);
        item1SubjectMetadata = itemService.getMetadata(item1,"dc", "subject",
            null, null);

        assertThat(item1TitleMetadata.get(0).getValue(), is("title of item2"));
        assertThat(item1EditorMetadata.get(0).getValue(), is("editor2"));
        assertThat(item1AuthorMetadata.get(0).getValue(), is("Smith 2, John"));

        assertThat(item1SubjectMetadata, hasSize(4));
        assertThat(item1SubjectMetadata, containsInAnyOrder(
            hasProperty("value", is("item1 subject 1")),
            hasProperty("value", is("item1 subject 2")),
            hasProperty("value", is("item2 subject 1")),
            hasProperty("value", is("item2 subject 2"))
        ));

    }

    @Test
    public void testMergeMultipleItems() throws Exception {

        context.turnOffAuthorisationSystem();
        WorkspaceItem witem = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
                            .withTitle("workspace item title")
                            .withSubject("workspace item subject")
                            .withEntityType("Publication")
                            .build();
        context.restoreAuthSystemState();

        DeduplicationMerge deduplicationMerge = new DeduplicationMerge(witem.getItem().getID().toString(),
            Arrays.asList(item2.getID().toString(), item3.getID().toString()),
            new ArrayList<>(),
            new ArrayList<>(),
            Arrays.asList("dc.subject"), true, false);

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(0));

        List<MetadataValue> item1SubjectMetadata = itemService.getMetadata(witem.getItem(),"dc",
            "subject", null, null);

        assertThat(item1SubjectMetadata, containsInAnyOrder(
            hasProperty("value", is("workspace item subject")),
            hasProperty("value", is("item2 subject 1")),
            hasProperty("value", is("item2 subject 2")),
            hasProperty("value", is("item3 subject 1")),
            hasProperty("value", is("item3 subject 2"))
        ));
    }

    @Test
    public void testWithdrawMergedItems() throws Exception {
        DeduplicationMerge deduplicationMerge =
            new DeduplicationMerge(item1.getID().toString(),
                Arrays.asList(item2.getID().toString(), item3.getID().toString()),
                Arrays.asList("dc.subject"),
                new ArrayList<>(),
                new ArrayList<>(), false, false);

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(0));

        // target item is not withdrawn
        assertThat(itemService.find(context, this.item1.getID()).isWithdrawn(), is(false));
        // merged items
        assertThat(itemService.find(context, this.item2.getID()).isWithdrawn(), is(true));
        assertThat(itemService.find(context, this.item3.getID()).isWithdrawn(), is(true));
    }

    @Test
    public void testMergeItemsCreateRelationships() throws Exception {

        DeduplicationMerge deduplicationMerge = new DeduplicationMerge(item1.getID().toString(),
            Arrays.asList(item2.getID().toString(), item3.getID().toString()),
            new ArrayList<>(),
            Arrays.asList("dc.subject"),
            new ArrayList<>(), false, false);

        List<Relationship> relationships
            = relationshipService.findByItemAndRelationshipType(context, item1, isMergedRelationshipType);

        assertThat(relationships, hasSize(0));

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(0));

        relationships
            = relationshipService.findByItemAndRelationshipType(context, item1, isMergedRelationshipType);

        assertThat(relationships, hasSize(2));
        assertThat(relationships, hasItems(
            hasProperty("leftItem", is(item1)),
            hasProperty("rightItem", is(item2)),
            hasProperty("rightItem", is(item3))
            ));
    }

    @Test
    public void testDeleteMergedItems() throws Exception {
        DeduplicationMerge deduplicationMerge =
            new DeduplicationMerge(item1.getID().toString(),
                Arrays.asList(item2.getID().toString(), item3.getID().toString()),
                Arrays.asList("dc.subject"),
                new ArrayList<>(),
                new ArrayList<>(), true, false);

        TestDSpaceRunnableHandler handler = runMergeItemsScript(deduplicationMerge);

        assertThat(handler.getErrorMessages(), hasSize(0));
        assertThat(itemService.find(context, item2.getID()), equalTo(null));
        assertThat(itemService.find(context, item3.getID()), equalTo(null));
    }

    private TestDSpaceRunnableHandler runMergeItemsScript(DeduplicationMerge data) throws Exception {
        String[] args = new String[] { "deduplication-merge-items", "-t", data.getTargetItem() };

        for (String item : data.getMergedItems()) {
            args = ArrayUtils.add(args, "-m");
            args = ArrayUtils.add(args, item);
        }

        for (String metadata : data.getReplacedNotEmptyMetadata()) {
            args = ArrayUtils.add(args, "-p");
            args = ArrayUtils.add(args, metadata);
        }

        for (String metadata : data.getReplacedMetadata()) {
            args = ArrayUtils.add(args, "-r");
            args = ArrayUtils.add(args, metadata);
        }

        for (String metadata : data.getAppendedMetadata()) {
            args = ArrayUtils.add(args, "-a");
            args = ArrayUtils.add(args, metadata);
        }

        if (data.isDelete()) {
            args = ArrayUtils.add(args, "-d");
        }

        if (data.isExclude()) {
            args = ArrayUtils.add(args, "-x");
        }

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        return handler;
    }
}