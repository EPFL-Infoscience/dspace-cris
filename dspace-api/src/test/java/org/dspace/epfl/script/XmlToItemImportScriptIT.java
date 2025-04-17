/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.dspace.builder.CollectionBuilder.createCollection;
import static org.dspace.builder.CommunityBuilder.createCommunity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.NonUniqueMetadataException;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class XmlToItemImportScriptIT extends AbstractIntegrationTestWithDatabase {
    private static final String BASE_XML_DIR_PATH = "./target/testing/dspace/assetstore/xml-to-item-import/";
    private static final String FIELD_TO_DELETE_SCHEMA = "dc";
    private static final String FIELD_TO_DELETE_ELEMENT = "title";
    private Collection collection;
    private Community community;

    private GroupService groupService;
    private CollectionService collectionService;
    private MetadataSchemaService metadataSchemaService;
    private MetadataFieldService metadataFieldService;
    private ItemService itemService;

    @Before
    public void beforeTests() throws SQLException, AuthorizeException, NonUniqueMetadataException {
        groupService = EPersonServiceFactory.getInstance().getGroupService();
        collectionService = ContentServiceFactory.getInstance().getCollectionService();
        metadataSchemaService = ContentServiceFactory.getInstance().getMetadataSchemaService();
        metadataFieldService = ContentServiceFactory.getInstance().getMetadataFieldService();
        itemService = ContentServiceFactory.getInstance().getItemService();

        context.turnOffAuthorisationSystem();
        community = createCommunity(context).build();
        collection = createCollection(context, community).withEntityType("Publication").build();

        Group adminGroup = collectionService.createAdministrators(context, collection);
        groupService.addMember(context, adminGroup, context.getCurrentUser());
        context.restoreAuthSystemState();
        context.commit();

    }

    @Test
    public void testAddingItemWithExistingFields() throws Exception {
        String fileLocation = getFilePath("item.xml");
        String[] args = new String[]{"is-academia-xml-import", "-c", collection.getID().toString(), "-f", fileLocation};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
    }

    // TODO rewrite this test without deleting important dc.title metadata field
    //      because without creating it again it causes further tests to fail,
    //      or just create dc.title again after test is performed
    @Test
    @Ignore
    public void testAddingItemWithNonexistentFields() throws Exception {
        String fileLocation = getFilePath("item.xml");
        String[] args = new String[]{"is-academia-xml-import", "-c", collection.getID().toString(), "-f", fileLocation};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        MetadataSchema metadataSchema = metadataSchemaService.find(context, FIELD_TO_DELETE_SCHEMA);
        if (metadataSchema != null) {
            MetadataField metadataField = metadataFieldService
                    .findByElement(context, FIELD_TO_DELETE_SCHEMA, FIELD_TO_DELETE_ELEMENT, null);
            if (metadataField != null) {
                context.turnOffAuthorisationSystem();
                metadataFieldService.delete(context, metadataField);
                context.restoreAuthSystemState();
                context.commit();
            }
        }

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
    }

    @Test
    public void testAddingMultipleItems() throws Exception {
        String fileLocation = getFilePath("items.xml");
        String[] args = new String[]{"is-academia-xml-import", "-c", collection.getID().toString(), "-f", fileLocation};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
    }

    @Test
    public void testLanguageValueItems() throws Exception {
        context.turnOffAuthorisationSystem();

        Collection collectionForTest = CollectionBuilder.createCollection(context, community)
                .withName("CollectionForTest")
                .withEntityType("Publication")
                .build();

        Group adminGroup = collectionService.createAdministrators(context, collectionForTest);
        groupService.addMember(context, adminGroup, context.getCurrentUser());
        context.restoreAuthSystemState();

        String fileLocation = getFilePath("IS-Academia-file.xml");
        String[] args = new String[]{"is-academia-xml-import", "-c",
                collectionForTest.getID().toString(), "-f", fileLocation};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        Item item = itemService.findAllByCollection(context, collectionForTest).next();

        assertEquals(item.getMetadata().stream().filter(metadataValue ->
                metadataValue.getMetadataField().toString().equals("dc_language_iso"))
                .map(MetadataValue::getValue).findFirst().get(), "en");
    }

    /**
     * This test checks that, for metadata with sciper as authority, the link to the
     * person profile is correctly created
     */
    @Test
    public void testSciperAsAuthority() throws Exception {
        context.turnOffAuthorisationSystem();
        Collection collectionForTest = CollectionBuilder.createCollection(context, community)
                .withName("CollectionForTest")
                .withEntityType("Publication")
                .build();

        Collection profiles = CollectionBuilder.createCollection(context, community)
                .withName("Profile Collection")
                .withEntityType("Person")
                .build();

        Item profileDupuis = ItemBuilder.createItem(context, profiles)
                .withTitle("Dupuis, Different name from file")
                .withMetadata("epfl", "sciperId", null, "186919")
                .build();

        Item profileDietz = ItemBuilder.createItem(context, profiles)
                .withTitle("Dietz, TEST Dieter")
                .withMetadata("epfl", "sciperId", null, "173997")
                .build();

        Item profileCitton = ItemBuilder.createItem(context, profiles)
                .withTitle("Citton, Yves")
                .withMetadata("epfl", "sciperId", null, "325534")
                .build();

        Group adminGroup = collectionService.createAdministrators(context, collectionForTest);
        groupService.addMember(context, adminGroup, context.getCurrentUser());
        context.restoreAuthSystemState();

        String fileLocation = getFilePath("IS-Academia-file.xml");
        String[] args = new String[]{"is-academia-xml-import", "-c",
                collectionForTest.getID().toString(), "-f", fileLocation};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        Item item = itemService.findAllByCollection(context, collectionForTest).next();

        List<MetadataValue> metadataAuthor = itemService.getMetadata(item, "dc", "contributor", "author", "*", false);
        metadataAuthor.stream()
            .filter(mv -> mv.getValue().startsWith("Dupuis"))
            .forEach(mv -> {
                assertThat(mv.getValue(), is("Dupuis, Aurélie"));
                assertThat(mv.getAuthority(), is(profileDupuis.getID().toString()));
            });

        List<MetadataValue> metadataAdvisor = itemService.getMetadata(item, "dc", "contributor", "advisor", "*", false);
        metadataAdvisor.stream()
            .filter(mv -> mv.getValue().startsWith("Dietz") || mv.getValue().startsWith("Citton"))
            .forEach(mv -> {
                if (mv.getValue().startsWith("Dietz")) {
                    assertThat(mv.getValue(), is("Dietz, Dieter"));
                    assertThat(mv.getAuthority(), is(profileDietz.getID().toString()));
                } else if (mv.getValue().startsWith("Citton")) {
                    assertThat(mv.getValue(), is("Citton, Yves"));
                    assertThat(mv.getAuthority(), is(profileCitton.getID().toString()));
                }
            });
    }

    private String getFilePath(String name) {
        return new File(BASE_XML_DIR_PATH, name).getAbsolutePath();
    }

}
