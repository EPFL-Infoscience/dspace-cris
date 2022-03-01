/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.deduplication.utils.DedupUtils;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.eperson.EPerson;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DeduplicationSetRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    private DedupUtils dedupUtils;

    @Test
    public void findAllUnauthorizedTest() throws Exception {
        // Access endpoint without being authenticated
        getClient().perform(get("/api/deduplications/sets"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void findAllForbiddenTest() throws Exception {
        String authToken = getAuthToken(eperson.getEmail(), password);
        // Access endpoint logged in as an unprivileged user
        getClient(authToken).perform(get("/api/deduplications/sets"))
            .andExpect(status().isForbidden());
    }

    @Test
    public void findAllDefaultTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/deduplications/sets"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/deduplications/sets")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(0)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));
    }

    /**
     * Test with five items:
     * - item1, item2 and item3 have the same title
     * - item4 and item5 have the same doi
     */
    @Test
    public void findAllWithThreeItemsSameTitleAndTwoItemsSameDoiTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Five public items
        Item publicItem1 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        Item publicItem2 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();
        Item publicItem3 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();
        Item publicItem4 = ItemBuilder.createItem(context, collection)
            .withTitle("Test 4")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        Item publicItem5 = ItemBuilder.createItem(context, collection)
            .withTitle("Test 5")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/deduplications/sets"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._links.self.href",
                Matchers.containsString("/api/deduplications/sets")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(2)));
    }

    /**
     * Test with three items:
     * - item1 and item2 have the same title
     * - item2 and item3 have the same doi
     */
    @Test
    public void findAllWithTwoItemsSameTitleAndTwoItemsSameDoiTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Three public items
        Item publicItem1 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        Item publicItem2 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();
        Item publicItem3 = ItemBuilder.createItem(context, collection)
            .withTitle("Test 2")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/deduplications/sets"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._links.self.href",
                Matchers.containsString("/api/deduplications/sets")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(2)));
    }

    @Test
    public void findOneUnauthorizedTest() throws Exception {
        String id = "title:123456789";
        // Access endpoint without being authenticated
        getClient().perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void findOneForbiddenTest() throws Exception {
        String authToken = getAuthToken(eperson.getEmail(), password);
        String id = "title:123456789";
        // Access endpoint logged in as an unprivileged user
        getClient(authToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isForbidden());
    }

    @Test
    public void findOneWithWrongIdTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "test123456789";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    public void findOneWithWrongIdTest2() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "test:123456789";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isNotFound());
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void findOneWithTwoItemsSameTitleTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
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
        String id = "title:098f6bcd4621d373cade4e832627b4f6";
        String signatureId = "title";
        String setChecksum = "098f6bcd4621d373cade4e832627b4f6";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureId", Matchers.equalTo(signatureId)))
            .andExpect(jsonPath("$.setChecksum", Matchers.equalTo(setChecksum)))
            .andExpect(jsonPath("$.otherSetIds", Matchers.hasSize(0)))
            .andExpect(jsonPath("$._links.items.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id + "/items")))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id)));
    }

    /**
     * Test with four items:
     * - item1 and item2 have the same title
     * - item3 and item4 have the same title
     */
    @Test
    public void findOneWithTwoItemsSameTitleAndTwoItemsSameTitleTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Four public items
        Item publicItem1 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .build();
        Item publicItem2 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();
        Item publicItem3 = ItemBuilder.createItem(context, collection)
            .withTitle("Another Test")
            .withIssueDate("2015-12-18")
            .build();
        Item publicItem4 = ItemBuilder.createItem(context, collection)
            .withTitle("Another Test")
            .withIssueDate("2015-12-18")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);

        String id = "title:098f6bcd4621d373cade4e832627b4f6";
        String signatureId = "title";
        String setChecksum = "098f6bcd4621d373cade4e832627b4f6";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureId", Matchers.equalTo(signatureId)))
            .andExpect(jsonPath("$.setChecksum", Matchers.equalTo(setChecksum)))
            .andExpect(jsonPath("$.otherSetIds", Matchers.hasSize(0)))
            .andExpect(jsonPath("$._links.items.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id + "/items")))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id)));

        id = "title:bee1c0f53d40e56f04a84dda46bbfbdb";
        signatureId = "title";
        setChecksum = "bee1c0f53d40e56f04a84dda46bbfbdb";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureId", Matchers.equalTo(signatureId)))
            .andExpect(jsonPath("$.setChecksum", Matchers.equalTo(setChecksum)))
            .andExpect(jsonPath("$.otherSetIds", Matchers.hasSize(0)))
            .andExpect(jsonPath("$._links.items.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id + "/items")))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id)));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void findOneWithTwoItemsSameDoiTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
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
        String id = "identifier:cc562133c18bd2baf21b0b7bfcdd9334";
        String signatureId = "identifier";
        String setChecksum = "cc562133c18bd2baf21b0b7bfcdd9334";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureId", Matchers.equalTo(signatureId)))
            .andExpect(jsonPath("$.setChecksum", Matchers.equalTo(setChecksum)))
            .andExpect(jsonPath("$.otherSetIds", Matchers.hasSize(0)))
            .andExpect(jsonPath("$._links.items.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id + "/items")))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id)));
    }

    /**
     * Test with four items:
     * - item1 and item2 have the same doi
     * - item3 and item4 have the same doi
     */
    @Test
    public void findOneWithTwoItemsSameDoiAndTwoItemsSameDoiTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Four public items
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
        Item publicItem3 = ItemBuilder.createItem(context, collection)
            .withTitle("Third Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.5555/987654321")
            .build();
        Item publicItem4 = ItemBuilder.createItem(context, collection)
            .withTitle("Fourth Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.5555/987654321")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);

        String id = "identifier:cc562133c18bd2baf21b0b7bfcdd9334";
        String signatureId = "identifier";
        String setChecksum = "cc562133c18bd2baf21b0b7bfcdd9334";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureId", Matchers.equalTo(signatureId)))
            .andExpect(jsonPath("$.setChecksum", Matchers.equalTo(setChecksum)))
            .andExpect(jsonPath("$.otherSetIds", Matchers.hasSize(0)))
            .andExpect(jsonPath("$._links.items.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id + "/items")))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id)));

        id = "identifier:c2de594816afd0b5dbab91c0919fca3d";
        signatureId = "identifier";
        setChecksum = "c2de594816afd0b5dbab91c0919fca3d";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.equalTo(id)))
            .andExpect(jsonPath("$.signatureId", Matchers.equalTo(signatureId)))
            .andExpect(jsonPath("$.setChecksum", Matchers.equalTo(setChecksum)))
            .andExpect(jsonPath("$.otherSetIds", Matchers.hasSize(0)))
            .andExpect(jsonPath("$._links.items.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id + "/items")))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("http://localhost/api/deduplications/sets/" + id)));
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
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
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
        String signatureId = "identifier";
        String setChecksum1 = "7efc267730909ed455af806fbb4f9935";
        String setChecksum2 = "cc562133c18bd2baf21b0b7bfcdd9334";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + signatureId + ":" + setChecksum1))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.isOneOf(
                signatureId + ":" + setChecksum1,
                signatureId + ":" + setChecksum2)))
            .andExpect(jsonPath("$.signatureId", Matchers.equalTo(signatureId)))
            .andExpect(jsonPath("$.setChecksum", Matchers.isOneOf(setChecksum1, setChecksum2)))
            .andExpect(jsonPath("$.otherSetIds", Matchers.hasSize(1)))
            .andExpect(jsonPath("$.otherSetIds[0]", Matchers.isOneOf(setChecksum1, setChecksum2)))
            .andExpect(jsonPath("$._links.items.href", Matchers.anyOf(
                Matchers.containsString("http://localhost/api/deduplications/sets/" + signatureId + ":" + setChecksum1 + "/items"),
                Matchers.containsString("http://localhost/api/deduplications/sets/" + signatureId + ":" + setChecksum2 + "/items"))))
            .andExpect(jsonPath("$._links.self.href", Matchers.anyOf(
                Matchers.containsString("http://localhost/api/deduplications/sets/" + signatureId + ":" + setChecksum1),
                Matchers.containsString("http://localhost/api/deduplications/sets/" + signatureId + ":" + setChecksum2))));
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi, arxiv and pmid
     */
    @Test
    public void findOneWithTwoItemsSameDoiArxivAndPmidTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
        Item publicItem1 = ItemBuilder.createItem(context, collection)
            .withTitle("First Test")
            .withIssueDate("2010-10-17")
            .withAuthor("Smith, Donald")
            .withIdentifierDoi("10.1234/123456789")
            .withIdentifierArxiv("arXiv:1501.00001")
            .withIdentifierPmid("1234")
            .build();
        Item publicItem2 = ItemBuilder.createItem(context, collection)
            .withTitle("Second Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .withIdentifierArxiv("arXiv:1501.00001")
            .withIdentifierPmid("1234")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String signatureId = "identifier";
        String setChecksum1 = "760e3775b24106e4e4501d1faf238090";
        String setChecksum2 = "cc562133c18bd2baf21b0b7bfcdd9334";
        String setChecksum3 = "7efc267730909ed455af806fbb4f9935";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + signatureId + ":" + setChecksum1))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.id", Matchers.isOneOf(
                    signatureId + ":" + setChecksum1,
                    signatureId + ":" + setChecksum2,
                    signatureId + ":" + setChecksum3)))
            .andExpect(jsonPath("$.signatureId", Matchers.equalTo(signatureId)))
            .andExpect(jsonPath("$.setChecksum", Matchers.isOneOf(setChecksum1, setChecksum2, setChecksum3)))
            .andExpect(jsonPath("$.otherSetIds", Matchers.hasSize(2)))
            .andExpect(jsonPath("$.otherSetIds[0]", Matchers.isOneOf(setChecksum1, setChecksum2, setChecksum3)))
            .andExpect(jsonPath("$.otherSetIds[1]", Matchers.isOneOf(setChecksum1, setChecksum2, setChecksum3)))
            .andExpect(jsonPath("$._links.items.href", Matchers.anyOf(
                    Matchers.containsString("http://localhost/api/deduplications/sets/" + signatureId + ":" + setChecksum1 + "/items"),
                    Matchers.containsString("http://localhost/api/deduplications/sets/" + signatureId + ":" + setChecksum2 + "/items"),
                    Matchers.containsString("http://localhost/api/deduplications/sets/" + signatureId + ":" + setChecksum3 + "/items"))))
            .andExpect(jsonPath("$._links.self.href", Matchers.anyOf(
                    Matchers.containsString("http://localhost/api/deduplications/sets/" + signatureId + ":" + setChecksum1),
                    Matchers.containsString("http://localhost/api/deduplications/sets/" + signatureId + ":" + setChecksum2),
                    Matchers.containsString("http://localhost/api/deduplications/sets/" + signatureId + ":" + setChecksum3))));
    }

    @Test
    public void searchMethodsExistTest() throws Exception {
        String authToken = getAuthToken(admin.getEmail(), password);
        getClient(authToken).perform(get("/api/deduplications/sets/search"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._links.findBySignature", Matchers.notNullValue()))
            .andExpect(jsonPath("$._links.findBySignatureAndRule", Matchers.notNullValue()));
    }

    @Test
    public void findBySignatureUnauthorizedTest() throws Exception {
        // Access endpoints without being authenticated
        String signatureId = "title";
        getClient().perform(
                get("/api/deduplications/sets/search/findBySignature")
                    .param("signature-id", signatureId))
            .andExpect(status().isUnauthorized());

        signatureId = "identifier";
        getClient().perform(
                get("/api/deduplications/sets/search/findBySignature")
                    .param("signature-id", signatureId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void findBySignatureForbiddenTest() throws Exception {
        // Access endpoints logged in as an unprivileged user
        String authToken = getAuthToken(eperson.getEmail(), password);

        String signatureId = "title";
        getClient(authToken).perform(
                get("/api/deduplications/sets/search/findBySignature")
                    .param("signature-id", signatureId))
            .andExpect(status().isForbidden());

        signatureId = "identifier";
        getClient(authToken).perform(
                get("/api/deduplications/sets/search/findBySignature")
                    .param("signature-id", signatureId))
            .andExpect(status().isForbidden());
    }

    @Test
    public void findBySignatureDefaultTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);

        String signatureId = "title";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignature")
                    .param("signature-id", signatureId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(0)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));

        signatureId = "identifier";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignature")
                    .param("signature-id", signatureId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(0)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));
    }

    @Test
    public void findByTitleSignatureTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
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
        String signatureId = "title";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignature")
                    .param("signature-id", signatureId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._embedded.sets", Matchers.hasSize(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("http://localhost/api/deduplications/sets/search/findBySignature")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(1)));
    }

    @Test
    public void findByIdentifierSignatureTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
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
        String signatureId = "identifier";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignature")
                    .param("signature-id", signatureId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._embedded.sets", Matchers.hasSize(1)))
            .andExpect(jsonPath("$._links.self.href", Matchers.containsString("http://localhost/api/deduplications/sets/search/findBySignature")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(1)));
    }

    @Test
    public void findBySignatureAndRuleUnauthorizedTest() throws Exception {
        // Access endpoints without being authenticated
        String signatureId = "title";
        String rule = "reviewer";
        getClient().perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isUnauthorized());
        rule = "submitter";
        getClient().perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isUnauthorized());
        rule = "administrator";
        getClient().perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isUnauthorized());

        signatureId = "identifier";
        rule = "reviewer";
        getClient().perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isUnauthorized());
        rule = "submitter";
        getClient().perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isUnauthorized());
        rule = "administrator";
        getClient().perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void findBySignatureAndRuleForbiddenTest() throws Exception {
        // Access endpoints without being authenticated
        String authToken = getAuthToken(eperson.getEmail(), password);

        String signatureId = "title";
        String rule = "reviewer";
        getClient(authToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isForbidden());
        rule = "submitter";
        getClient(authToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isForbidden());
        rule = "administrator";
        getClient(authToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isForbidden());

        signatureId = "identifier";
        rule = "reviewer";
        getClient(authToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isForbidden());
        rule = "submitter";
        getClient(authToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isForbidden());
        rule = "administrator";
        getClient(authToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isForbidden());
    }

    @Test
    public void findBySignatureAndRuleDefaultTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);

        String signatureId = "title";
        String rule = "reviewer";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(0)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));
        rule = "submitter";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(0)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));
        rule = "administrator";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(0)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));

        signatureId = "identifier";
        rule = "reviewer";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(0)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));
        rule = "submitter";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(0)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));
        rule = "administrator";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(0)))
            .andExpect(jsonPath("$.page.totalElements", is(0)));
    }

    @Test
    public void findByTitleSignatureAndRuleTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
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
        String signatureId = "title";
        String rule = "reviewer";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._embedded.sets", Matchers.hasSize(1)))
            .andExpect(jsonPath("$._links.self.href",
                Matchers.containsString("http://localhost/api/deduplications/sets/search/findBySignatureAndRule")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(1)));
        rule = "submitter";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._embedded.sets", Matchers.hasSize(1)))
            .andExpect(jsonPath("$._links.self.href",
                Matchers.containsString("http://localhost/api/deduplications/sets/search/findBySignatureAndRule")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(1)));
        rule = "administrator";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._embedded.sets", Matchers.hasSize(1)))
            .andExpect(jsonPath("$._links.self.href",
                Matchers.containsString("http://localhost/api/deduplications/sets/search/findBySignatureAndRule")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(1)));
    }

    @Test
    public void findByIdentifierSignatureAndRuleTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
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
        String signatureId = "identifier";
        String rule = "reviewer";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._embedded.sets", Matchers.hasSize(1)))
            .andExpect(jsonPath("$._links.self.href",
                Matchers.containsString("http://localhost/api/deduplications/sets/search/findBySignatureAndRule")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(1)));
        rule = "submitter";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._embedded.sets", Matchers.hasSize(1)))
            .andExpect(jsonPath("$._links.self.href",
                Matchers.containsString("http://localhost/api/deduplications/sets/search/findBySignatureAndRule")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(1)));
        rule = "administrator";
        getClient(adminToken).perform(
                get("/api/deduplications/sets/search/findBySignatureAndRule")
                    .param("signature-id", signatureId)
                    .param("rule", rule))
            .andExpect(status().isOk())
            .andExpect(content().contentType(contentType))
            .andExpect(jsonPath("$._embedded.sets", Matchers.hasSize(1)))
            .andExpect(jsonPath("$._links.self.href",
                Matchers.containsString("http://localhost/api/deduplications/sets/search/findBySignatureAndRule")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(1)));
    }

    @Test
    public void deleteUnauthorizedTest() throws Exception {
        String id = "title:123456789";
        // Access endpoint without being authenticated
        getClient().perform(delete("/api/deduplications/sets/" + id))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void deleteForbiddenTest() throws Exception {
        String authToken = getAuthToken(eperson.getEmail(), password);
        String id = "title:123456789";
        // Access endpoint logged in as an unprivileged user
        getClient(authToken).perform(delete("/api/deduplications/sets/" + id))
            .andExpect(status().isForbidden());
    }

    @Test
    public void deleteWithWrongIdTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "test123456789";
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    public void deleteWithWrongId2Test() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "test:123456789";
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    public void deleteByTitleSignatureTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
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
        String id = "title:098f6bcd4621d373cade4e832627b4f6";

        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id))
            .andExpect(status().isNoContent());

        // force commit to update dedup solr core
        dedupUtils.commit();

        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    public void deleteByIdentifierSignatureTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
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
        String id = "identifier:cc562133c18bd2baf21b0b7bfcdd9334";

        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id))
            .andExpect(status().isNoContent());

        // force commit to update dedup solr core
        dedupUtils.commit();

        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    public void findItemsUnauthorizedTest() throws Exception {
        String id = "title:123456789";
        // Access endpoint without being authenticated
        getClient().perform(delete("/api/deduplications/sets/" + id))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void findItemsForbiddenTest() throws Exception {
        String authToken = getAuthToken(eperson.getEmail(), password);
        String id = "title:123456789";
        // Access endpoint logged in as an unprivileged user
        getClient(authToken).perform(get("/api/deduplications/sets/" + id + "/items"))
            .andExpect(status().isForbidden());
    }

    @Test
    public void findItemsWithWrongIdTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "test123456789";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id + "/items"))
            .andExpect(status().isNotFound());
    }

    @Test
    public void findItemsWithWrongId2Test() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "test:123456789";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id + "/items"))
            .andExpect(status().isNotFound());
    }

    @Test
    public void findItemsByTitleSignatureTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
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
        String id = "title:098f6bcd4621d373cade4e832627b4f6";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id + "/items"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._links.self.href",
                Matchers.containsString("/api/deduplications/sets/" + id + "/items")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(2)));
    }

    @Test
    public void findItemsByIdentifierSignatureTest() throws Exception {
        // Turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // ** GIVEN **
        // 1. Two users: one to use as submitter, another one to use as reviewer
        EPerson submitter = EPersonBuilder.createEPerson(context)
            .withEmail("submitter1@example.com")
            .withPassword(password)
            .build();
        context.setCurrentUser(submitter);
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

        // 3. Two public items
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
        String id = "identifier:cc562133c18bd2baf21b0b7bfcdd9334";
        getClient(adminToken).perform(get("/api/deduplications/sets/" + id + "/items"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._links.self.href",
                Matchers.containsString("/api/deduplications/sets/" + id + "/items")))
            .andExpect(jsonPath("$.page.number", is(0)))
            .andExpect(jsonPath("$.page.size", is(20)))
            .andExpect(jsonPath("$.page.totalPages", is(1)))
            .andExpect(jsonPath("$.page.totalElements", is(2)));
    }
}
