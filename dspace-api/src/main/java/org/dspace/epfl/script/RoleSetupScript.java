/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;
import org.dspace.xmlworkflow.factory.XmlWorkflowServiceFactory;
import org.dspace.xmlworkflow.service.XmlWorkflowService;

public class RoleSetupScript extends DSpaceRunnable<RoleSetupScriptConfiguration<RoleSetupScript>> {

    private static final String ADMINS_GROUP = Group.ADMIN;

    private static final String CURATORS_GROUP = "Curators";

    private static final String SUBMITTERS_GROUP = "Submitter";

    private static final String EPFL_PUBLICATION_REVIEWERS = "EPFL Publications reviewers";

    private static final String PUBLICATION_REVIEWERS = "Publications reviewers";

    private GroupService groupService;

    private CommunityService communityService;

    private CollectionService collectionService;

    private XmlWorkflowService workflowService;

    private String researchOutputsCommunityId;

    private String entitiesCommunityId;

    private String virtualCollectionsId;

    private Group submittersGroup;

    private Group curatorsGroup;

    private Group adminsGroup;

    private Group epflReviewersGroup;

    private Group reviewersGroup;

    private Context context;

    @Override
    @SuppressWarnings("unchecked")
    public RoleSetupScriptConfiguration<RoleSetupScript> getScriptConfiguration() {
        return new DSpace().getServiceManager()
            .getServiceByName("epfl-roles-setup", RoleSetupScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        collectionService = ContentServiceFactory.getInstance().getCollectionService();
        groupService = EPersonServiceFactory.getInstance().getGroupService();
        communityService = ContentServiceFactory.getInstance().getCommunityService();
        workflowService = XmlWorkflowServiceFactory.getInstance().getXmlWorkflowService();
        researchOutputsCommunityId = commandLine.getOptionValue('r');
        entitiesCommunityId = commandLine.getOptionValue('e');
        virtualCollectionsId = commandLine.getOptionValue('v');
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        try {

            adminsGroup = findByName(ADMINS_GROUP);
            curatorsGroup = findByName(CURATORS_GROUP);
            submittersGroup = findByName(SUBMITTERS_GROUP);
            epflReviewersGroup = findByNameOrCreate(EPFL_PUBLICATION_REVIEWERS);
            reviewersGroup = findByNameOrCreate(PUBLICATION_REVIEWERS);

            setupRoles();

            context.complete();
            context.restoreAuthSystemState();

        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        }
    }

    private void setupRoles() throws Exception {
        setupResearchOutputCommunityRoles();
        setupEntitiesCommunityRoles();
    }

    private void setupResearchOutputCommunityRoles() throws Exception {
        
        Community community = findById(researchOutputsCommunityId);

        handler.logInfo("Processing community named " + community.getName());
        
        List<Collection> collections = community.getCollections();
        
        for(Collection collection : collections) {
            
            String collectioName = collection.getName();

            handler.logInfo("Processing collection named " + collectioName);
            
            Group submitters = createNewSubmitters(collection);
            addSubgroup(submitters, submittersGroup);
            addSubgroup(submitters, curatorsGroup);

            Group administrators = createNewAdministrators(collection);
            addSubgroup(administrators, curatorsGroup);
            
            Group firstRoleGroup = createWorkflowRoleGroup(collection, "EPFLReviewer");
            addSubgroup(firstRoleGroup, epflReviewersGroup);
            addSubgroup(epflReviewersGroup, curatorsGroup);
            addSubgroup(epflReviewersGroup, adminsGroup);

            Group secondRoleGroup = createWorkflowRoleGroup(collection, "Reviewer");
            addSubgroup(secondRoleGroup, reviewersGroup);
            addSubgroup(reviewersGroup, curatorsGroup);
            addSubgroup(reviewersGroup, adminsGroup);

        }
        
    }

    private void setupEntitiesCommunityRoles() throws Exception {

        Community community = findById(entitiesCommunityId);

        handler.logInfo("Processing community named " + community.getName());

        List<Collection> collections = community.getCollections();

        for (Collection collection : collections) {

            String collectioName = collection.getName();

            handler.logInfo("Processing collection named " + collectioName);

            Group submitters = createNewSubmitters(collection);
            addSubgroup(submitters, adminsGroup);

            if (!collection.getID().toString().equals(virtualCollectionsId)) {
                addSubgroup(submitters, curatorsGroup);
            }

        }

    }

    private Group createWorkflowRoleGroup(Collection collection, String role) throws Exception {
        Group workflowRoleGroup = workflowService.getWorkflowRoleGroup(context, collection, role, null);
        if (workflowRoleGroup != null) {
            groupService.delete(context, workflowRoleGroup);
            handler.logInfo("Deleted previous workflow role group for role " + role);
        }
        workflowRoleGroup = workflowService.createWorkflowRoleGroup(context, collection, role);
        handler.logInfo("Created workflow role group named " + workflowRoleGroup.getName());
        return workflowRoleGroup;
    }

    private Group createNewSubmitters(Collection collection) throws SQLException, AuthorizeException, IOException {
        Group submitters = collection.getSubmitters();
        if(submitters != null) {
            groupService.delete(context, submitters);
            handler.logInfo("Deleted previous submitters group");
        }
        
        submitters = collectionService.createSubmitters(context, collection);
        handler.logInfo("Created submitters group named " + submitters.getName());
        return submitters;
    }

    private Group createNewAdministrators(Collection collection) throws SQLException, AuthorizeException, IOException {
        Group administrators = collection.getAdministrators();
        if (administrators != null) {
            groupService.delete(context, administrators);
            handler.logInfo("Deleted previous administrators group");
        }

        administrators = collectionService.createAdministrators(context, collection);
        handler.logInfo("Created administrators group named " + administrators.getName());
        return administrators;
    }

    private void addSubgroup(Group parent, Group child) throws SQLException {
        groupService.addMember(context, parent, child);
        handler.logInfo("Configured " + child.getName() + " as subgroup of + " + parent.getName());
    }

    private Community findById(String id) throws SQLException {
        Community community = communityService.find(context, UUIDUtils.fromString(id));
        if (community == null) {
            throw new IllegalArgumentException("No community found by id " + id);
        }
        return community;
    }

    private Group findByName(String name) throws SQLException {
        Group group = groupService.findByName(context, name);
        if (group == null) {
            throw new IllegalArgumentException("No group found by name " + name);
        }
        return group;
    }

    private Group findByNameOrCreate(String name) throws SQLException, AuthorizeException {
        Group group = groupService.findByName(context, name);
        if (group == null) {
            group = groupService.create(context);
            groupService.setName(group, name);
            groupService.update(context, group);
            handler.logInfo("Created group named " + name);
        }
        return group;
    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() throws SQLException {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }

}
