/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.repository.SubmissionFieldsRestRepository;
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
 * This class handles {@link SubmissionFieldsRestRepository}
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.com)
 */
public class SubmissionFieldsRestRepositoryIT extends AbstractControllerIntegrationTest {

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
                                      .withEntityType("Publication")
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
                                   "/api/config/submissionfields/" + item.getID()))
                               .andExpect(status().isMethodNotAllowed());
    }

    @Test
    public void testFindAll() throws Exception {

        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get(
                                   "/api/config/submissionfields"))
                               .andExpect(status().isMethodNotAllowed());
    }

    @Test
    public void testFindByWrongItemId() throws Exception {

        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get(
                                   "/api/config/submissionfields/search/findByItem")
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
                          .withAuthorAffiliation("Author-Affiliation")
                          .withEditor("Arnold")
                          .withEditorAffiliation("Editor-Affiliation")
                          .withSubject("item subject 2")
                          .withEntityType("Publication")
                          .withAlternativeTitle("item alternative title")
                          .build();

        context.restoreAuthSystemState();

        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get(
                "/api/config/submissionfields/search/findByItem")
                                   .param("uuid", item.getID().toString()))
                               .andExpect(status().isOk())
                               .andExpect(jsonPath("$.itemId", is(item.getID().toString())))
                               .andExpect(jsonPath("$.repeatableFields", containsInAnyOrder(
                                   "dc.identifier.arxiv",
                                   "dc.identifier.ismn",
                                   "dc.relation.product",
                                   "dc.identifier.pmid",
                                   "dc.relation.grantno",
                                   "dc.relation.conference",
                                   "dc.relation.ispartofseries",
                                   "oairecerif.author.affiliation",
                                   "dc.identifier.uri",
                                   "dc.description.sponsorship",
                                   "dc.description.sponsorship",
                                   "dc.title.alternative",
                                   "dc.identifier.scopus",
                                   "dc.contributor.author",
                                   "oairecerif.editor.affiliation",
                                   "dc.contributor.editor",
                                   "dc.identifier.govdoc",
                                   "dc.identifier.isi",
                                   "dc.identifier.isbn",
                                   "dc.identifier.doi",
                                   "dc.identifier.adsbibcode",
                                   "dc.identifier.issn",
                                   "dc.subject",
                                   "dc.relation.project",
                                   "dc.identifier.other"
                               )))
                               .andExpect(jsonPath("$.nestedFields['dc.contributor.author']", contains(
                                   "oairecerif.author.affiliation")))
                               .andExpect(jsonPath("$.nestedFields['dc.contributor.editor']", contains(
                                   "oairecerif.editor.affiliation")))
                               .andExpect(jsonPath("$._links.self.href",
                                   containsString("/api/config/submissionfields/search/findByItem" +
                                       "?uuid=" + item.getID().toString())));
    }

    @Test
    public void testFindByItemIdInWorkspace() throws Exception {

        context.turnOffAuthorisationSystem();

        WorkspaceItem workspaceItem =
            WorkspaceItemBuilder.createWorkspaceItem(context, collection)
                                .withTitle("workspace item title")
                                .withSubject("workspace item subject")
                                .withAuthor("Smith")
                                .withAuthorAffilitation("Author-Affiliation")
                                .withEditor("Arnold")
                                .withSubject("workspace item subject 2")
                                .withEntityType("Publication")
                                .build();

        context.restoreAuthSystemState();

        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get(
                                   "/api/config/submissionfields/search/findByItem")
                                   .param("uuid", workspaceItem.getItem().getID().toString()))
                               .andExpect(status().isOk())
                               .andExpect(jsonPath("$.itemId", is(workspaceItem.getItem().getID().toString())))
                               .andExpect(jsonPath("$.repeatableFields", containsInAnyOrder(
                                   "dc.identifier.arxiv",
                                   "dc.identifier.ismn",
                                   "dc.relation.product",
                                   "dc.identifier.pmid",
                                   "dc.relation.grantno",
                                   "dc.relation.conference",
                                   "dc.relation.ispartofseries",
                                   "oairecerif.author.affiliation",
                                   "dc.identifier.uri",
                                   "dc.description.sponsorship",
                                   "dc.description.sponsorship",
                                   "dc.title.alternative",
                                   "dc.identifier.scopus",
                                   "dc.contributor.author",
                                   "oairecerif.editor.affiliation",
                                   "dc.contributor.editor",
                                   "dc.identifier.govdoc",
                                   "dc.identifier.isi",
                                   "dc.identifier.isbn",
                                   "dc.identifier.doi",
                                   "dc.identifier.adsbibcode",
                                   "dc.identifier.issn",
                                   "dc.subject",
                                   "dc.relation.project",
                                   "dc.identifier.other"
                               )))
                               .andExpect(jsonPath("$.nestedFields['dc.contributor.author']", contains(
                                   "oairecerif.author.affiliation")))
                               .andExpect(jsonPath("$._links.self.href",
                                   containsString("/api/config/submissionfields/search/findByItem" +
                                       "?uuid=" + workspaceItem.getItem().getID().toString())));
    }

    @Test
    public void testFindByItemIdInWorkflow() throws Exception {

        context.turnOffAuthorisationSystem();

        WorkflowItem workflowItem =
            WorkflowItemBuilder.createWorkflowItem(context, collection)
                               .withTitle("workflow item title")
                               .withSubject("workflow item subject")
                               .withAuthor("Smith")
                               .withAuthorAffiliation("Author-Affiliation")
                               .withSubject("workflow item subject 2")
                               .withEntityType("Publication")
                               .build();

        context.restoreAuthSystemState();

        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get(
                                   "/api/config/submissionfields/search/findByItem")
                                   .param("uuid", workflowItem.getItem().getID().toString()))
                               .andExpect(status().isOk())
                               .andExpect(jsonPath("$.itemId", is(workflowItem.getItem().getID().toString())))
                               .andExpect(jsonPath("$.repeatableFields", containsInAnyOrder(
                                   "dc.identifier.arxiv",
                                   "dc.identifier.ismn",
                                   "dc.relation.product",
                                   "dc.identifier.pmid",
                                   "dc.relation.grantno",
                                   "dc.relation.conference",
                                   "dc.relation.ispartofseries",
                                   "oairecerif.author.affiliation",
                                   "dc.identifier.uri",
                                   "dc.description.sponsorship",
                                   "dc.description.sponsorship",
                                   "dc.title.alternative",
                                   "dc.identifier.scopus",
                                   "dc.contributor.author",
                                   "oairecerif.editor.affiliation",
                                   "dc.contributor.editor",
                                   "dc.identifier.govdoc",
                                   "dc.identifier.isi",
                                   "dc.identifier.isbn",
                                   "dc.identifier.doi",
                                   "dc.identifier.adsbibcode",
                                   "dc.identifier.issn",
                                   "dc.subject",
                                   "dc.relation.project",
                                   "dc.identifier.other"
                               )))
                               .andExpect(jsonPath("$.nestedFields['dc.contributor.author']", contains(
                                   "oairecerif.author.affiliation")))
                               .andExpect(jsonPath("$._links.self.href",
                                   containsString("/api/config/submissionfields/search/findByItem" +
                                       "?uuid=" + workflowItem.getItem().getID().toString())));
    }

}
