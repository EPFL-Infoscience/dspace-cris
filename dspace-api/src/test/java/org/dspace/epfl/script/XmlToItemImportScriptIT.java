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

import java.io.File;
import java.sql.SQLException;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.NonUniqueMetadataException;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.junit.Before;
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

    @Before
    public void beforeTests() throws SQLException, AuthorizeException, NonUniqueMetadataException {
        groupService = EPersonServiceFactory.getInstance().getGroupService();
        collectionService = ContentServiceFactory.getInstance().getCollectionService();
        metadataSchemaService = ContentServiceFactory.getInstance().getMetadataSchemaService();
        metadataFieldService = ContentServiceFactory.getInstance().getMetadataFieldService();

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

    private String getFilePath(String name) {
        return new File(BASE_XML_DIR_PATH, name).getAbsolutePath();
    }

}
