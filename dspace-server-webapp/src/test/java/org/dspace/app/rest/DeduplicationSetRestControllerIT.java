/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.dspace.app.deduplication.utils.DedupUtils;
import org.dspace.app.rest.builder.CollectionBuilder;
import org.dspace.app.rest.builder.CommunityBuilder;
import org.dspace.app.rest.builder.EPersonBuilder;
import org.dspace.app.rest.builder.ItemBuilder;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.eperson.EPerson;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DeduplicationSetRestControllerIT extends AbstractControllerIntegrationTest {

    @Autowired
    private DedupUtils dedupUtils;

    @Test
    public void deleteItemUnauthorizedTest() throws Exception {
        String id = "title:123456789";
        UUID itemUUID = UUID.randomUUID();
        // Access endpoint without being authenticated
        getClient().perform(delete("/api/deduplications/sets/" + id + "/items/" + itemUUID))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void deleteItemForbiddenTest() throws Exception {
        String authToken = getAuthToken(eperson.getEmail(), password);
        String id = "title:123456789";
        UUID itemUUID = UUID.randomUUID();
        // Access endpoint logged in as an unprivileged user
        getClient(authToken).perform(delete("/api/deduplications/sets/" + id + "/items/" + itemUUID))
            .andExpect(status().isForbidden());
    }

    @Test
    public void deleteItemWithWrongIdTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "test123456789";
        UUID itemUUID = UUID.randomUUID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id + "/items/" + itemUUID))
            .andExpect(status().isNotFound());
    }

    @Test
    public void deleteItemWithWrongId2Test() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "test:123456789";
        UUID itemUUID = UUID.randomUUID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id + "/items/" + itemUUID))
            .andExpect(status().isNotFound());
    }

    @Test
    public void deleteItemWithWrongUUIDTest() throws Exception {
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
        UUID itemUUID = UUID.randomUUID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id + "/items/" + itemUUID))
            .andExpect(status().isNotFound());
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title
     */
    @Test
    public void deleteItemWithTwoItemsSameTitleTest() throws Exception {
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
        UUID itemUUID = publicItem1.getID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id + "/items/" + itemUUID))
            .andExpect(status().isNoContent());

        // force commit to update dedup solr core
        dedupUtils.commit();

        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isNotFound());
    }

    /**
     * Test with three items:
     * - item1, item2 and item3 have the same title
     */
    @Test
    public void deleteItemWithThreeItemsSameTitleTest() throws Exception {
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
        Item publicItem3 = ItemBuilder.createItem(context, collection)
            .withTitle("Test")
            .withIssueDate("2015-12-18")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "title:098f6bcd4621d373cade4e832627b4f6";

        UUID itemUUID = publicItem1.getID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id + "/items/" + itemUUID))
            .andExpect(status().isNoContent());

        // force commit to update dedup solr core
        dedupUtils.commit();

        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isOk());

        itemUUID = publicItem2.getID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id + "/items/" + itemUUID))
            .andExpect(status().isNoContent());

        // force commit to update dedup solr core
        dedupUtils.commit();

        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isNotFound());
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same doi
     */
    @Test
    public void deleteItemWithTwoItemsSameDoiTest() throws Exception {
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
        UUID itemUUID = publicItem1.getID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id + "/items/" + itemUUID))
            .andExpect(status().isNoContent());

        // force commit to update dedup solr core
        dedupUtils.commit();

        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isNotFound());
    }

    /**
     * Test with three items:
     * - item1, item2 and item3 have the same doi
     */
    @Test
    public void deleteItemWithThreeItemsSameDoiTest() throws Exception {
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
        Item publicItem3 = ItemBuilder.createItem(context, collection)
            .withTitle("Third Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String id = "identifier:cc562133c18bd2baf21b0b7bfcdd9334";

        UUID itemUUID = publicItem1.getID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id + "/items/" + itemUUID))
            .andExpect(status().isNoContent());

        // force commit to update dedup solr core
        dedupUtils.commit();

        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isOk());

        itemUUID = publicItem2.getID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + id + "/items/" + itemUUID))
            .andExpect(status().isNoContent());

        // force commit to update dedup solr core
        dedupUtils.commit();

        getClient(adminToken).perform(get("/api/deduplications/sets/" + id))
            .andExpect(status().isNotFound());
    }

    /**
     * Test with two items:
     * - item1 and item2 have the same title and doi
     */
    @Test
    public void deleteItemWithTwoItemsSameTitleAndDoiTest() throws Exception {
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
        String idTitle = "title:098f6bcd4621d373cade4e832627b4f6";
        String idIdentifier = "identifier:cc562133c18bd2baf21b0b7bfcdd9334";

        UUID itemUUID = publicItem1.getID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + idTitle + "/items/" + itemUUID))
            .andExpect(status().isNoContent());

        // force commit to update dedup solr core
        dedupUtils.commit();

        getClient(adminToken).perform(get("/api/deduplications/sets/" + idTitle))
            .andExpect(status().isNotFound());

        getClient(adminToken).perform(get("/api/deduplications/sets/" + idIdentifier))
            .andExpect(status().isNotFound());
    }

    /**
     * Test with three items:
     * - item1 and item2 have the same title
     * - item2 and item3 have the same doi
     */
    @Test
    public void deleteItemWithTwoItemsSameTitleAndTwoItemsSameDoiTest() throws Exception {
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
            .withIdentifierDoi("10.1234/123456789")
            .build();
        Item publicItem3 = ItemBuilder.createItem(context, collection)
            .withTitle("Another Test")
            .withIssueDate("2015-12-18")
            .withIdentifierDoi("10.1234/123456789")
            .build();

        // Restore the authorization system
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        String idTitle = "title:098f6bcd4621d373cade4e832627b4f6";
        String idIdentifier = "identifier:cc562133c18bd2baf21b0b7bfcdd9334";

        UUID itemUUID = publicItem1.getID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + idTitle + "/items/" + itemUUID))
            .andExpect(status().isNoContent());

        // force commit to update dedup solr core
        dedupUtils.commit();

        getClient(adminToken).perform(get("/api/deduplications/sets/" + idTitle))
            .andExpect(status().isNotFound());

        getClient(adminToken).perform(get("/api/deduplications/sets/" + idIdentifier))
            .andExpect(status().isOk());

        itemUUID = publicItem2.getID();
        getClient(adminToken).perform(delete("/api/deduplications/sets/" + idIdentifier + "/items/" + itemUUID))
            .andExpect(status().isNoContent());

        // force commit to update dedup solr core
        dedupUtils.commit();

        getClient(adminToken).perform(get("/api/deduplications/sets/" + idIdentifier))
            .andExpect(status().isNotFound());
    }
}
