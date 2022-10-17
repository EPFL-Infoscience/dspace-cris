/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.WorkflowItemBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.workflow.WorkflowItem;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * This class handles {@link org.dspace.app.rest.repository.SubmissionRepeatableFieldsRestRepository}
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.com)
 */
public class SubmissionRepeatableFieldsRestRepositoryIT extends AbstractControllerIntegrationTest {

    private Collection collection;

    private Item item;

    @Before
    public void setUp() throws Exception {

        super.setUp();

        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withTitle("community")
                                          .build();

        collection = CollectionBuilder.createCollection(context, parentCommunity)
                                      .withName("collection")
                                      .build();

        context.restoreAuthSystemState();
    }

    @Test
    public void testFindOne() throws Exception {

        context.turnOffAuthorisationSystem();

        item = ItemBuilder.createItem(context, collection)
                          .withTitle("item title")
                          .withSubject("item subject")
                          .withAuthor("Smith")
                          .withEditor("Arnold")
                          .withSubject("item subject 2")
                          .withEntityType("Publication")
                          .withAlternativeTitle("item alternative title")
                          .build();

        context.restoreAuthSystemState();


        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get(
                                   "/api/config/submissionrepeatablefields/" + item.getID()))
                               .andExpect(status().isMethodNotAllowed());
    }

    @Test
    public void testFindAll() throws Exception {

        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get(
                                   "/api/config/submissionrepeatablefields"))
                               .andExpect(status().isMethodNotAllowed());
    }

    @Test
    public void testFindByWrongItemId() throws Exception {

        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get(
                                   "/api/config/submissionrepeatablefields/search/findByItem")
                                   .param("uuid", "74dd0cc9-e33f-49ec-a543-affd1f4d92ad"))
                               .andExpect(status().isNotFound());
    }

    @Test
    public void testFindByItemId() throws Exception {

        context.turnOffAuthorisationSystem();

        item = ItemBuilder.createItem(context, collection)
                          .withTitle("item title")
                          .withSubject("item subject")
                          .withAuthor("Smith")
                          .withEditor("Arnold")
                          .withSubject("item subject 2")
                          .withEntityType("Publication")
                          .withAlternativeTitle("item alternative title")
                          .build();

        context.restoreAuthSystemState();

        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get(
                "/api/config/submissionrepeatablefields/search/findByItem")
                                   .param("uuid", item.getID().toString()))
                               .andExpect(status().isOk())
                               .andExpect(jsonPath("$.itemId", is(item.getID().toString())))
                               .andExpect(jsonPath("$.repeatableFields", containsInAnyOrder(
                                   "dc.contributor.author",
                                   "dc.identifier.uri",
                                   "dc.title.alternative",
                                   "dc.subject")))
                               .andExpect(jsonPath("$._links.self.href",
                                   containsString("/api/config/submissionrepeatablefields/search/findByItem" +
                                       "?uuid=" + item.getID().toString())));
    }

    @Test
    public void testFindByItemIdInWorkspace() throws Exception {

        context.turnOffAuthorisationSystem();

        WorkspaceItem workspaceItem = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
                                                          .withTitle("workspace item title")
                                                          .withSubject("workspace item subject")
                                                          .withAuthor("Smith")
                                                          .withEditor("Arnold")
                                                          .withSubject("workspace item subject 2")
                                                          .withEntityType("Publication")
                                                          .build();

        context.restoreAuthSystemState();

        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get(
                                   "/api/config/submissionrepeatablefields/search/findByItem")
                                   .param("uuid", workspaceItem.getItem().getID().toString()))
                               .andExpect(status().isOk())
                               .andExpect(jsonPath("$.itemId", is(workspaceItem.getItem().getID().toString())))
                               .andExpect(jsonPath("$.repeatableFields", containsInAnyOrder(
                                   "dc.contributor.author",
                                   "dc.subject")))
                               .andExpect(jsonPath("$._links.self.href",
                                   containsString("/api/config/submissionrepeatablefields/search/findByItem" +
                                       "?uuid=" + workspaceItem.getItem().getID().toString())));
    }

    @Test
    public void testFindByItemIdInWorkflow() throws Exception {

        context.turnOffAuthorisationSystem();

        WorkflowItem workflowItem = WorkflowItemBuilder.createWorkflowItem(context, collection)
                                                       .withTitle("workflow item title")
                                                       .withSubject("workflow item subject")
                                                       .withAuthor("Smith")
                                                       .withSubject("workflow item subject 2")
                                                       .withEntityType("Publication")
                                                       .build();

        context.restoreAuthSystemState();

        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get(
                                   "/api/config/submissionrepeatablefields/search/findByItem")
                                   .param("uuid", workflowItem.getItem().getID().toString()))
                               .andExpect(status().isOk())
                               .andExpect(jsonPath("$.itemId", is(workflowItem.getItem().getID().toString())))
                               .andExpect(jsonPath("$.repeatableFields", containsInAnyOrder(
                                   "dc.contributor.author",
                                   "dc.identifier.uri",
                                   "dc.subject")))
                               .andExpect(jsonPath("$._links.self.href",
                                   containsString("/api/config/submissionrepeatablefields/search/findByItem" +
                                       "?uuid=" + workflowItem.getItem().getID().toString())));
    }

}
