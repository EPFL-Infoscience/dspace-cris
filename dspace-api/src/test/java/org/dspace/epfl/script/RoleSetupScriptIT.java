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
import static org.junit.Assert.assertNull;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.xmlworkflow.factory.XmlWorkflowServiceFactory;
import org.dspace.xmlworkflow.service.XmlWorkflowService;
import org.junit.Test;

public class RoleSetupScriptIT extends AbstractIntegrationTestWithDatabase {

    private GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private XmlWorkflowService workflowService = XmlWorkflowServiceFactory.getInstance().getXmlWorkflowService();

    @Test
    public void testRoleSetupScript() throws Exception {
        context.turnOffAuthorisationSystem();
        Community  researchOutputsCommunity = createCommunity(context).build();
        Collection researchOutputsCollection = createCollection(context, researchOutputsCommunity)
                .withWorkflow("defaultWorkflow")
                .withEntityType("Publication").build();

        Community  entitiesCommunity = createCommunity(context).build();
        Collection virtualCollection = createCollection(context, entitiesCommunity)
                .withWorkflow("defaultWorkflow")
                .withEntityType("VirtualCollection").build();
        context.restoreAuthSystemState();


        String[] args = new String[] { "epfl-roles-setup", "-r", researchOutputsCommunity.getID().toString(),
                "-e", entitiesCommunity.getID().toString(), "-v", virtualCollection.getID().toString() };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertNull(groupService.findByName(context, "Publications reviewers"));
        assertNull(workflowService.getWorkflowRoleGroup(context, researchOutputsCollection, "reviewer", null));
    }

}