/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.DeduplicationSignatureBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.WorkflowItemBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.workflow.WorkflowItem;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;

public class DeduplicationSignatureRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Test
    public void findAllUnauthorizedTest() throws Exception {
        // Access endpoint without being authenticated
        getClient().perform(get("/api/deduplications/signatures"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void findAllForbiddenTest() throws Exception {
        String authToken = getAuthToken(eperson.getEmail(), password);
        // Access endpoint logged in as an unprivileged user
        getClient(authToken).perform(get("/api/deduplications/signatures"))
            .andExpect(status().isForbidden());
    }

    @Test
    public void findAllDefaultTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        int signaturesSize = 2;
        getClient(adminToken).perform(get("/api/deduplications/signatures"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._embedded.signatures", Matchers.hasSize(is(signaturesSize))))
            .andExpect(jsonPath("$._embedded.signatures[*].id",
                Matchers.containsInAnyOrder("title", "identifier")))
            .andExpect(jsonPath("$._embedded.signatures[*].signatureType",
                Matchers.containsInAnyOrder("title", "identifier")))
            .andExpect(jsonPath("$._embedded.signatures[*].type",
                Matchers.containsInAnyOrder("signature", "signature")))
            .andExpect(jsonPath("$._embedded.signatures[*].groupReviewerCheck", Matchers.containsInAnyOrder(0, 0)))
            .andExpect(jsonPath("$._embedded.signatures[*].groupSubmitterCheck", Matchers.containsInAnyOrder(0, 0)))
            .andExpect(jsonPath("$._embedded.signatures[*].groupAdminstratorCheck", Matchers.containsInAnyOrder(0, 0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(signaturesSize)));
    }

    @Test
    public void findOneUnauthorizedTest() throws Exception {
        String id = "title";
        // Access endpoint without being authenticated
        getClient().perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void findOneForbiddenTest() throws Exception {
        String authToken = getAuthToken(eperson.getEmail(), password);
        String id = "title";
        // Access endpoint logged in as an unprivileged user
        getClient(authToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isForbidden());
    }

    @Test
    public void findOneWithWrongIdTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "anothertitle";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    public void findOneTitleDefaultTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    @Test
    public void findOneIdentifierDefaultTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .build();

        // 2. Two public items
        Item publicItem1 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        Item publicItem2 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .build();

        // 2. Two public items
        Item publicItem1 = ItemBuilder.createItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        Item publicItem2 = ItemBuilder.createItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi and arxiv
     */
    @Test
    public void findOneWithTwoItemsSameDoiAndArxivTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .build();

        // 2. Two public items
        Item publicItem1 = ItemBuilder.createItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .withIdentifierArxiv("arXiv:1501.00001")
            .build();
        Item publicItem2 = ItemBuilder.createItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .withIdentifierArxiv("arXiv:1501.00001")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(2)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(2)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(2)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title and doi
     */
    @Test
    public void findOneWithTwoItemsSameTitleAndDoiTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .build();

        // 2. Two public items
        Item publicItem1 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        Item publicItem2 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);

        String id = "title";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));

        id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
        .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with three items:
     * - item1 and item2 have the same title
     * - item2 and item3 have the same doi
     */
    @Test
    public void findOneWithTwoItemsSameTitleAndTwoItemsSameDoiTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .build();

        // 2. Three public items
        Item publicItem1 = ItemBuilder.createItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        Item publicItem2 = ItemBuilder.createItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        Item publicItem3 = ItemBuilder.createItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2020-08-10")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));

        id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
        .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    // TODO: Da qui in poi iniziano i seguenti test:
    // admin niente, reviewer niente, submitter niente - é findOneTitleTest()
    // admin niente, reviewer niente, submitter verifies - aggiunto
    // admin niente, reviewer niente, submitter rejects - aggiunto
    // admin niente, reviewer verify, submitter niente - aggiunto
    // admin niente, reviewer verify, submitter verifies (x)
    // admin niente, reviewer verify, submitter rejects - aggiunto
    // admin niente, reviewer rejects, submitter niente - aggiunto
    // admin niente, reviewer rejects, submitter verifies - aggiunto
    // admin niente, reviewer rejects, submitter rejects (x)
    // admin rejects, reviewer niente, submitter niente - aggiunto
    // admin rejects, reviewer niente, submitter verifies - aggiunto
    // admin rejects, reviewer niente, submitter rejects - aggiunto
    // admin rejects, reviewer verify, submitter niente - aggiunto
    // admin rejects, reviewer verify, submitter verifies - aggiunto
    // admin rejects, reviewer verify, submitter rejects - aggiunto
    // admin rejects, reviewer rejects, submitter niente - aggiunto
    // admin rejects, reviewer rejects, submitter verifies - aggiunto
    // admin rejects, reviewer rejects, submitter rejects - aggiunto

    // questi test sono fatti su due item con title signature
    // TODO: avrebbe senso integrare questi test anche per i seguenti casi:
    // - 2 item: 1 e 2 condividono identifier signature
    // - 2 item: 1 e 2 condividono sia la title signature che l'identifier signature
    // - 3 item: 1 e 2 condividono la title signature, 2 e 3 l'identifier signature
    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithSubmitterVerifyTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithSubmitterVerifyAndReviewerRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithSubmitterVerifyAndReviewerVerifyAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer verifies item1 and item2 duplicate matching
        createReviewerVerifyDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 3: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithSubmitterVerifyAndReviewerRejectAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 3: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithSubmitterVerifyAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithSubmitterRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: submitter rejects item1 and item2 duplicate matching
        createSubmitterRejectDecision(context, submitter, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithSubmitterRejectAndReviewerVerifyTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: submitter rejects item1 and item2 duplicate matching
        createSubmitterRejectDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer verifies item1 and item2 duplicate matching
        createReviewerVerifyDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithSubmitterRejectAndReviewerVerifyAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: submitter rejects item1 and item2 duplicate matching
        createSubmitterRejectDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer verifies item1 and item2 duplicate matching
        createReviewerVerifyDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 3: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithSubmitterRejectAndReviewerRejectAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: submitter rejects item1 and item2 duplicate matching
        createSubmitterRejectDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 3: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithSubmitterRejectAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: submitter rejects item1 and item2 duplicate matching
        createSubmitterRejectDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithReviewerVerifyTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: reviewer verifies item1 and item2 duplicate matching
        createReviewerVerifyDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithReviewerVerifyAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: reviewer verifies item1 and item2 duplicate matching
        createReviewerVerifyDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 2: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithReviewerRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithReviewerRejectAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 2: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneTitleWithAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Step 1: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithSubmitterVerifyTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithSubmitterVerifyAndReviewerRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithSubmitterVerifyAndReviewerVerifyAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer verifies item1 and item2 duplicate matching
        createReviewerVerifyDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 3: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithSubmitterVerifyAndReviewerRejectAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 3: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithSubmitterVerifyAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithSubmitterRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter rejects item1 and item2 duplicate matching
        createSubmitterRejectDecision(context, submitter, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithSubmitterRejectAndReviewerVerifyTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter rejects item1 and item2 duplicate matching
        createSubmitterRejectDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer verifies item1 and item2 duplicate matching
        createReviewerVerifyDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithSubmitterRejectAndReviewerVerifyAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter rejects item1 and item2 duplicate matching
        createSubmitterRejectDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer verifies item1 and item2 duplicate matching
        createReviewerVerifyDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 3: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithSubmitterRejectAndReviewerRejectAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter rejects item1 and item2 duplicate matching
        createSubmitterRejectDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 3: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithSubmitterRejectAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter rejects item1 and item2 duplicate matching
        createSubmitterRejectDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters and reviewers
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithReviewerVerifyTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: reviewer verifies item1 and item2 duplicate matching
        createReviewerVerifyDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithReviewerVerifyAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: reviewer verifies item1 and item2 duplicate matching
        createReviewerVerifyDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 2: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithReviewerRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithReviewerRejectAndAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 2: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneIdentifierWithAdminRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workflow items submitted by submitter
        context.setCurrentUser(submitter);
        WorkflowItem item1 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkflowItem item2 = WorkflowItemBuilder.createWorkflowItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        // 0 groups for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi and arxiv
     */
    @Test
    public void findOneWithTwoItemsSameDoiAndArxivWithSubmitterVerifyTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .withIdentifierArxiv("arXiv:1501.00001")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .withIdentifierArxiv("arXiv:1501.00001")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(2)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(2)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi and arxiv
     */
    @Test
    public void findOneWithTwoItemsSameDoiAndArxivWithSubmitterVerifyAndReviewerRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .withIdentifierArxiv("arXiv:1501.00001")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .withIdentifierArxiv("arXiv:1501.00001")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, submitter, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(2)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi and arxiv
     */
    @Test
    public void findOneWithTwoItemsSameDoiAndArxivWithSubmitterVerifyAndReviewerRejectAndAdminRejectTest()
        throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .withIdentifierArxiv("arXiv:1501.00001")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .withIdentifierArxiv("arXiv:1501.00001")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 3: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title and doi
     */
    @Test
    public void findOneWithTwoItemsSameTitleAndDoiWithSubmitterVerifyTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);

        String id = "title";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));

        id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
        .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title and doi
     */
    @Test
    public void findOneWithTwoItemsSameTitleAndDoiWithSubmitterVerifyAndReviewerRejectTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);

        String id = "title";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));

        id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
        .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title and doi
     */
    @Test
    public void findOneWithTwoItemsSameTitleAndDoiWithSubmitterVerifyAndReviewerRejectAndAdminRejectTest()
        throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 3: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);

        String id = "title";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));

        id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
        .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with three items:
     * - item1 and item2 have the same title
     * - item2 and item3 have the same doi
     */
    @Test
    public void findOneWithTwoItemsSameTitleAndTwoItemsSameDoiWithSubmitterVerifyTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkspaceItem item3 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Another Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: submitter verifies item2 and item3 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item2.getItem(), item3.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));

        id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
        .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with three items:
     * - item1 and item2 have the same title
     * - item2 and item3 have the same doi
     */
    @Test
    public void findOneWithTwoItemsSameTitleAndTwoItemsSameDoiWithSubmitterVerifyAndReviewerRejectTest()
        throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkspaceItem item3 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Another Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: submitter verifies item2 and item3 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item2.getItem(), item3.getItem());

        // Step 3: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 4: reviewer rejects item2 and item3 duplicate matching
        createReviewerRejectDecision(context, reviewer, item2.getItem(), item3.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));

        id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
        .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
        .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    /**
     * Test with three items:
     * - item1 and item2 have the same title
     * - item2 and item3 have the same doi
     */
    @Test
    public void findOneWithTwoItemsSameTitleAndTwoItemsSameDoiWithSubmitterVerifyAndReviewerRejectAndAdminRejectTest()
        throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        WorkspaceItem item3 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Another Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Step 1: submitter verifies item1 and item2 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item1.getItem(), item2.getItem());

        // Step 2: submitter verifies item2 and item3 duplicate matching
        createSubmitterVerifyDecision(context, submitter, item2.getItem(), item3.getItem());

        // Step 3: reviewer rejects item1 and item2 duplicate matching
        createReviewerRejectDecision(context, reviewer, item1.getItem(), item2.getItem());

        // Step 4: reviewer rejects item2 and item3 duplicate matching
        createReviewerRejectDecision(context, reviewer, item2.getItem(), item3.getItem());

        // Step 5: administrator rejects item1 and item2 duplicate matching
        createAdminRejectDecision(context, item1.getItem(), item2.getItem());

        // Step 6: administrator rejects item2 and item3 duplicate matching
        createAdminRejectDecision(context, item2.getItem(), item3.getItem());

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));

        id = "identifier";
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
        .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
        .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(0)))
        .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }

    private void createSubmitterVerifyDecision(Context context, EPerson ePerson, Item item1, Item item2)
        throws SQLException {
        context.setCurrentUser(ePerson);
        DeduplicationSignatureBuilder.createDeduplication(context)
            .withFirstItemId(item1.getID())
            .withSecondItemId(item2.getID())
            .withToFix(true)
            .withReaderNote("message note")
            .withSubmitterDecision()
            .withVerifyAction()
            .build();
    }

    private void createSubmitterRejectDecision(Context context, EPerson ePerson, Item item1, Item item2)
        throws SQLException {
        context.setCurrentUser(ePerson);
        DeduplicationSignatureBuilder.createDeduplication(context)
            .withFirstItemId(item1.getID())
            .withSecondItemId(item2.getID())
            .withFake(true)
            .withNote("message note")
            .withSubmitterDecision()
            .withRejectAction()
            .build();
    }

    private void createReviewerVerifyDecision(Context context, EPerson ePerson, Item item1, Item item2)
        throws SQLException {
        context.setCurrentUser(ePerson);
        DeduplicationSignatureBuilder.createDeduplication(context)
            .withFirstItemId(item1.getID())
            .withSecondItemId(item2.getID())
            .withToFix(true)
            .withReaderNote("message note")
            .withWorkflowDecision()
            .withVerifyAction()
            .build();
    }

    private void createReviewerRejectDecision(Context context, EPerson ePerson, Item item1, Item item2)
        throws SQLException {
        context.setCurrentUser(ePerson);
        DeduplicationSignatureBuilder.createDeduplication(context)
            .withFirstItemId(item1.getID())
            .withSecondItemId(item2.getID())
            .withFake(false)
            .withNote("message note")
            .withWorkflowDecision()
            .withRejectAction()
            .build();
    }

    private void createAdminRejectDecision(Context context, Item item1, Item item2)
        throws SQLException {
        context.setCurrentUser(admin);
        DeduplicationSignatureBuilder.createDeduplication(context)
            .withFirstItemId(item1.getID())
            .withSecondItemId(item2.getID())
            .withAdminRejectAction()
            .build();
    }

    @Ignore
    public void unexpectedNotAuthorizedExceptionTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        EPerson reviewer = EPersonBuilder.createEPerson(context)
            .withEmail("reviewer1@example.com")
            .withPassword(password)
            .build();

        // 2. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection 1")
            .withSubmitterGroup(submitter)
            .withWorkflowGroup(1, reviewer)
            .withWorkflowGroup(2, reviewer)
            .withWorkflowGroup(3, reviewer)
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        // 3. Two workspace items submitted by submitter
        context.setCurrentUser(submitter);
        WorkspaceItem item1 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        WorkspaceItem item2 = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title";
        // Step 1: 1 group for submitters, reviewers and administrators
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));

        // Step 2: submitter verifies item1 and item2 duplicate matching
        // FIXME: Building a DeduplicationSignature as submitter throws unauthorized exception
        DeduplicationSignatureBuilder.createDeduplication(context)
            .withFirstItemId(item1.getItem().getID())
            .withSecondItemId(item2.getItem().getID())
            .withToFix(true)
            .withReaderNote("message note")
            .withSubmitterDecision()
            .withVerifyAction()
            .build();
        // 0 groups for submitters
        getClient(adminToken).perform(get("/api/deduplications/signatures/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureType", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.type", Matchers.equalTo("signature")))
            .andExpect(jsonPath("$.groupReviewerCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$.groupSubmitterCheck", Matchers.equalTo(0)))
            .andExpect(jsonPath("$.groupAdminstratorCheck", Matchers.equalTo(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/signatures")));
    }
}
