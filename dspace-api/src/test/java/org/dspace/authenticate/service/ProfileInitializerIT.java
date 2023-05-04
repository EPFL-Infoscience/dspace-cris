/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate.service;

import java.sql.SQLException;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.profile.service.ResearcherProfileService;
import org.dspace.utils.DSpace;
import org.junit.Before;
import org.junit.Test;

public class ProfileInitializerIT extends AbstractIntegrationTestWithDatabase {

    private ProfileInitializer profileInitializer = new DSpace().getSingletonService(ProfileInitializer.class);

    private ResearcherProfileService researcherProfileService = new DSpace()
        .getServiceManager().getServicesByType(ResearcherProfileService.class).get(0);

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();

    @Before
    public void setup() throws Exception {

        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();

        CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Profile Collection")
            .withEntityType("Person")
            .withTemplateItem()
            .build();

        CollectionBuilder.createCollection(context, parentCommunity)
            .withName("OrgUnit Collection")
            .withEntityType("OrgUnit")
            .withTemplateItem()
            .build();

        context.restoreAuthSystemState();

    }

    @Test
    public void testProfileCreation() throws SQLException, AuthorizeException {


    }

}
