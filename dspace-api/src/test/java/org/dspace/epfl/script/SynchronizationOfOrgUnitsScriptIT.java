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
import static org.junit.Assert.assertEquals;

import java.sql.SQLException;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.junit.Before;
import org.junit.Test;

public class SynchronizationOfOrgUnitsScriptIT extends AbstractIntegrationTestWithDatabase {

    private ItemService itemService;
    private Collection collection;

    @Before
    public void beforeTests() throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        itemService = ContentServiceFactory.getInstance().getItemService();
        Community community = createCommunity(context).build();
        collection = createCollection(context, community)
            .withEntityType("OrgUnit")
            .build();
        context.restoreAuthSystemState();
        context.commit();
    }

    @Test
    public void testParentOrganizationIsSetWithAcronym() throws Exception {
        context.turnOffAuthorisationSystem();
        Item orgUnitParent = ItemBuilder.createItem(context, collection)
                                        .withTitle("Parent OrgUnit")
                                        .withAcronym("PARENT")
                                        .build();
        Item orgUnitChild = ItemBuilder.createItem(context, collection)
                                       .withTitle("Child OrgUnit")
                                       .withAcronym("CHILD")
                                       .withParentOrganization("PARENT", orgUnitParent.getID().toString())
                                       .build();
        context.restoreAuthSystemState();

        String[] args = new String[] { "mock-synchronization-of-orgunits" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);

        context.reloadEntity(orgUnitChild);
        List<MetadataValue> parentOrganizationValues = itemService.getMetadataByMetadataString(
            orgUnitChild, "organization.parentOrganization");

        // Check that organization.parentOrganization of orgUnitChild
        // is still set with acronym and not name of orgUnitParent
        assertEquals(1, parentOrganizationValues.size());
        assertEquals("PARENT", parentOrganizationValues.get(0).getValue());
        assertEquals(orgUnitParent.getID().toString(), parentOrganizationValues.get(0).getAuthority());

        ItemBuilder.deleteItem(orgUnitChild.getID());
        ItemBuilder.deleteItem(orgUnitParent.getID());
    }
}
