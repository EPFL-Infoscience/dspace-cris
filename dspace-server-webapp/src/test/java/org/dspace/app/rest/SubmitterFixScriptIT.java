/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.eperson.EPerson;
import org.dspace.epfl.script.SubmitterFixScript;
import org.dspace.services.ConfigurationService;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
* Test class for SubmitterFixScript
* 
* @author Mykhaylo Boychuk (mykhaylo.boychuk at 4Science.com)
*/
public class SubmitterFixScriptIT extends AbstractControllerIntegrationTest {

    @Autowired
    private ConfigurationService configurationService;

    @Test
    public void updateSubmitterOfItemsWhereSubmitterWithoutNetIDTest() throws Exception {
        context.turnOffAuthorisationSystem();
        EPerson defaulEPerson = EPersonBuilder.createEPerson(context)
                                              .withEmail("test.default.email@test.ua")
                                              .withPassword(password)
                                              .build();

        configurationService.setProperty("epfl.default-submitter.email", defaulEPerson.getEmail());

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        // collection for Publications
        Collection col1 = CollectionBuilder.createCollection(context, parentCommunity)
                                           .withEntityType("Publication")
                                           .withName("Collection Publication")
                                           .build();
        // collection for Person
        Collection col2 = CollectionBuilder.createCollection(context, parentCommunity)
                                           .withEntityType("Person")
                                           .withName("CollectionPerson")
                                           .build();

        EPerson eperson1 = EPersonBuilder.createEPerson(context)
                                         .withEmail("eperson1@mail.com")
                                         .withNetId("123")
                                         .withPassword(password)
                                         .build();
        EPerson eperson2 = EPersonBuilder.createEPerson(context)
                                         .withEmail("eperson2@mail.com")
                                         .withNetId("456")
                                         .withPassword(password)
                                         .build();
        EPerson eperson3 = EPersonBuilder.createEPerson(context)
                                         .withEmail("eperson3@mail.com")
                                         .withNetId("789")
                                         .withPassword(password)
                                         .build();
        EPerson eperson4 = EPersonBuilder.createEPerson(context)
                                         .withEmail("eperson4@mail.com")
                                         .withNetId("09876")
                                         .withPassword(password)
                                         .build();

        // item for eperson4
        Item itemForEPerson4 = ItemBuilder.createItem(context, col2)
                                          .withTitle("Misha Boychuk")
                                          .withDspaceObjectOwner(eperson4)
                                          .build();

        EPerson originalSubmitter = EPersonBuilder.createEPerson(context)
                                                  .withEmail("originalSubmitter@mail.com")
                                                  .withPassword(password)
                                                  .build();

        Item publication1 = ItemBuilder.createItem(context, col1)
                                       .withTitle("Publication1")
                                       .withSubmitter(originalSubmitter)
                                       .withEpflLastmodifiedEmail("eperson1@mail.com")
                                       .withEpflCuratorEmail("eperson3@mail.com")
                                       .build();
        Item publication2 = ItemBuilder.createItem(context, col1)
                                       .withTitle("Publication2")
                                       .withSubmitter(originalSubmitter)
                                       .withEpflLastmodifiedEmail("eperson2@mail.com")
                                       .withEpflCuratorEmail("eperson3@mail.com")
                                       .build();
        Item publication3 = ItemBuilder.createItem(context, col1)
                                       .withTitle("Publication3")
                                       .withSubmitter(originalSubmitter)
                                       .withEpflCuratorEmail("eperson3@mail.com")
                                       .build();
        Item publication4 = ItemBuilder.createItem(context, col1)
                                       .withTitle("Publication4")
                                       .withSubmitter(originalSubmitter)
                                       .withAuthor("Misha Boychuk", itemForEPerson4.getID().toString())
                                       .build();
        Item publication5 = ItemBuilder.createItem(context, col1)
                                       .withSubmitter(originalSubmitter)
                                       .withTitle("Publication5")
                                       .build();

        // before launching the script all publications have eperson as submitter
        assertEquals(originalSubmitter.getEmail(), publication1.getSubmitter().getEmail());
        assertEquals(originalSubmitter.getEmail(), publication2.getSubmitter().getEmail());
        assertEquals(originalSubmitter.getEmail(), publication3.getSubmitter().getEmail());
        assertEquals(originalSubmitter.getEmail(), publication4.getSubmitter().getEmail());
        assertEquals(originalSubmitter.getEmail(), publication5.getSubmitter().getEmail());

        context.restoreAuthSystemState();
        String[] args = new String[] {"epfl-update-submitter", "-c", col1.getID().toString()};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        SubmitterFixScript submitterFixScript = new SubmitterFixScript();
        submitterFixScript.initialize(args, handler, admin);
        submitterFixScript.run();

        String tokenAdmin = getAuthToken(admin.getEmail(), password);
        // case 1: if the publication contains the metadata epfl.lastmodified.email
        //         and the person with that email exists, it is updated with this
        getClient(tokenAdmin).perform(get("/api/core/items/" + publication1.getID().toString()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.submitterEmail", Matchers.is(eperson1.getEmail())));

        getClient(tokenAdmin).perform(get("/api/core/items/" + publication2.getID().toString()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.submitterEmail", Matchers.is(eperson2.getEmail())));

        // case 2: if the publication does not contain the epfl.lastmodified.email metadata,
        //         the epfl.curator.email metadata is checked
        getClient(tokenAdmin).perform(get("/api/core/items/" + publication3.getID().toString()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.submitterEmail", Matchers.is(eperson3.getEmail())));

        // case 3: if there are  no metadata epfl.lastmodified.email & epfl.curator.email
        //         check is exists item(entityType:Person) for any dc.contributor.author
        getClient(tokenAdmin).perform(get("/api/core/items/" + publication4.getID().toString()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.submitterEmail", Matchers.is(eperson4.getEmail())));

        // case 4: if cases 1-2-3 do not pass, the default person is set up
        //         see Property "epfl.default-submitter.email"
        getClient(tokenAdmin).perform(get("/api/core/items/" + publication5.getID().toString()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.submitterEmail", Matchers.is(defaulEPerson.getEmail())));
    }

    @Test
    public void updateSubmitterOfItemsWithoutSubmitterTest() throws Exception {
        context.turnOffAuthorisationSystem();
        EPerson defaulEPerson = EPersonBuilder.createEPerson(context)
                                              .withEmail("test.default.email@test.ua")
                                              .withNetId("321")
                                              .withPassword(password)
                                              .build();

        configurationService.setProperty("epfl.default-submitter.email", defaulEPerson.getEmail());

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        // collection for Publications
        Collection col1 = CollectionBuilder.createCollection(context, parentCommunity)
                                           .withEntityType("Publication")
                                           .withName("Collection Publication")
                                           .build();
        // collection for Person
        Collection col2 = CollectionBuilder.createCollection(context, parentCommunity)
                                           .withEntityType("Person")
                                           .withName("CollectionPerson")
                                           .build();

        EPerson eperson1 = EPersonBuilder.createEPerson(context)
                                         .withEmail("eperson1@mail.com")
                                         .withNetId("123")
                                         .withPassword(password)
                                         .build();
        EPerson eperson2 = EPersonBuilder.createEPerson(context)
                                         .withEmail("eperson2@mail.com")
                                         .withNetId("456")
                                         .withPassword(password)
                                         .build();
        EPerson eperson3 = EPersonBuilder.createEPerson(context)
                                         .withEmail("eperson3@mail.com")
                                         .withNetId("789")
                                         .withPassword(password)
                                         .build();
        EPerson eperson4 = EPersonBuilder.createEPerson(context)
                                         .withEmail("eperson4@mail.com")
                                         .withNetId("09876")
                                         .withPassword(password)
                                         .build();

        // item for eperson4
        Item itemForEPerson4 = ItemBuilder.createItem(context, col2)
                                          .withTitle("Misha Boychuk")
                                          .withDspaceObjectOwner(eperson4)
                                          .build();

        Item publication1 = ItemBuilder.createItem(context, col1)
                                       .withTitle("Publication1")
                                       .withSubmitter(null)
                                       .withEpflLastmodifiedEmail("eperson1@mail.com")
                                       .withEpflCuratorEmail("eperson3@mail.com")
                                       .build();
        Item publication2 = ItemBuilder.createItem(context, col1)
                                       .withTitle("Publication2")
                                       .withSubmitter(null)
                                       .withEpflLastmodifiedEmail("eperson2@mail.com")
                                       .withEpflCuratorEmail("eperson3@mail.com")
                                       .build();
        Item publication3 = ItemBuilder.createItem(context, col1)
                                       .withTitle("Publication3")
                                       .withSubmitter(null)
                                       .withEpflCuratorEmail("eperson3@mail.com")
                                       .build();
        Item publication4 = ItemBuilder.createItem(context, col1)
                                       .withTitle("Publication4")
                                       .withSubmitter(null)
                                       .withAuthor("Misha Boychuk", itemForEPerson4.getID().toString())
                                       .build();
        Item publication5 = ItemBuilder.createItem(context, col1)
                                       .withSubmitter(null)
                                       .withTitle("Publication5")
                                       .build();

        // before launching the script all publications haven't submitter
        assertNull(publication1.getSubmitter());
        assertNull(publication2.getSubmitter());
        assertNull(publication3.getSubmitter());
        assertNull(publication4.getSubmitter());
        assertNull(publication5.getSubmitter());

        context.restoreAuthSystemState();
        String[] args = new String[] {"epfl-update-submitter", "-c", col1.getID().toString()};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        SubmitterFixScript submitterFixScript = new SubmitterFixScript();
        submitterFixScript.initialize(args, handler, admin);
        submitterFixScript.run();

        String tokenAdmin = getAuthToken(admin.getEmail(), password);
        // case 1: if the publication contains the metadata epfl.lastmodified.email
        //         and the person with that email exists, it is updated with this
        getClient(tokenAdmin).perform(get("/api/core/items/" + publication1.getID().toString()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.submitterEmail", Matchers.is(eperson1.getEmail())));

        getClient(tokenAdmin).perform(get("/api/core/items/" + publication2.getID().toString()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.submitterEmail", Matchers.is(eperson2.getEmail())));

        // case 2: if the publication does not contain the epfl.lastmodified.email metadata,
        //         the epfl.curator.email metadata is checked
        getClient(tokenAdmin).perform(get("/api/core/items/" + publication3.getID().toString()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.submitterEmail", Matchers.is(eperson3.getEmail())));

        // case 3: if there are  no metadata epfl.lastmodified.email & epfl.curator.email
        //         check is exists item(entityType:Person) for any dc.contributor.author
        getClient(tokenAdmin).perform(get("/api/core/items/" + publication4.getID().toString()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.submitterEmail", Matchers.is(eperson4.getEmail())));

        // case 4: if cases 1-2-3 do not pass, the default person is set up
        //         see Property "epfl.default-submitter.email"
        getClient(tokenAdmin).perform(get("/api/core/items/" + publication5.getID().toString()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.submitterEmail", Matchers.is(defaulEPerson.getEmail())));
    }

}
