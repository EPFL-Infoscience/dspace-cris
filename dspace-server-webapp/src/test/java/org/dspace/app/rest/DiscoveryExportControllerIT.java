/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.builder.ItemBuilder.createItem;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.StreamDisseminationCrosswalk;
import org.dspace.content.integration.crosswalks.ItemExportCrosswalk;
import org.dspace.content.integration.crosswalks.StreamDisseminationCrosswalkMapper;
import org.dspace.core.Context;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.GroupService;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration tests for {@link DiscoveryExportController}
 *
 * @author Andrea Bollini (andrea.bollini at 4science.com)
 *
 */
public class DiscoveryExportControllerIT extends AbstractControllerIntegrationTest {

    @Autowired
    private DiscoveryExportController controller;
    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private GroupService groupService;

    private Community community;

    private Collection collection;
    private Collection collectionRestricted;
    private Item firstPerson;
    private Item publication1;
    private Item publication2;
    private Item publication3;
    private Item publication4;
    private Item publicationRestricted;

    @Before
    public void setup() throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        community = CommunityBuilder.createCommunity(context).withName("Community").build();
        collection = CollectionBuilder.createCollection(context, community).withName("Public Items collection").build();
        collectionRestricted = CollectionBuilder.createCollection(context, community)
                .withName("Restricted Items collection")
                .withDefaultItemRead(groupService.findByName(context, Group.ADMIN)).build();

        firstPerson = createItem(context, collection).withEntityType("Person").withTitle("Smith, John")
                .withVariantName("J.S.").withVariantName("Smith John").withGender("M")
                .withPersonMainAffiliation("University").withOrcidIdentifier("0000-0002-9079-5932")
                .withSciperIdentifier("sciper1").withScopusAuthorIdentifier("SA-01").withPersonEmail("test@test.com")
                .withResearcherIdentifier("R-01").withResearcherIdentifier("R-02").withPersonAffiliation("Company")
                .withPersonAffiliationStartDate("2018-01-01")
                .withPersonAffiliationEndDate(PLACEHOLDER_PARENT_METADATA_VALUE).withPersonAffiliationRole("Developer")
                .withPersonAffiliation("Another Company").withPersonAffiliationStartDate("2017-01-01")
                .withPersonAffiliationEndDate("2017-12-31").withPersonAffiliationRole("Developer").build();

        publication1 = ItemBuilder.createItem(context, collection).withEntityType("Publication")
                .withTitle("First Publication").withAlternativeTitle("Alternative publication title")
                .withSubject("test").withAuthor("John Smith", firstPerson.getID().toString()).withIssueDate("2021")
                .build();

        publication2 = ItemBuilder.createItem(context, collection).withEntityType("Publication")
                .withTitle("Second Publication").withAlternativeTitle("Alternative publication title")
                .withSubject("test").withAuthor("John Smith", firstPerson.getID().toString()).withIssueDate("2022")
                .build();

        publication3 = ItemBuilder.createItem(context, collection).withEntityType("Publication")
                .withTitle("Third Publication").withAlternativeTitle("Alternative publication title")
                .withSubject("something-else").withAuthor("John Smith", firstPerson.getID().toString())
                .withIssueDate("2023").build();

        publication4 = ItemBuilder.createItem(context, collection).withEntityType("Publication")
                .withTitle("Forth Publication").withAlternativeTitle("Alternative publication title")
                .withSubject("test").withAuthor("John Smith", firstPerson.getID().toString()).withIssueDate("2024")
                .build();

        publicationRestricted = ItemBuilder.createItem(context, collectionRestricted).withEntityType("Publication")
                .withTitle("Restricted Publication").withAlternativeTitle("Alternative publication title")
                .withAuthor("John Smith", firstPerson.getID().toString()).withSubject("test").withIssueDate("2022")
                .build();

        context.restoreAuthSystemState();
        context.setCurrentUser(admin);
    }

    @Test
    public void sequentialTest() throws Exception {
        getClient()
                .perform(
                        get("/api/discover/export")
                            .param("query", "author_authority:" + firstPerson.getID().toString())
                            .param("of", "xm")
                            .param("configuration", "researchoutputs"))
                // The status has to be 200 OK
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(publication1.getID().toString())))
                .andExpect(content().string(containsString(publication2.getID().toString())))
                .andExpect(content().string(containsString(publication3.getID().toString())))
                .andExpect(content().string(containsString(publication4.getID().toString())))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));

        // check that the query param is really used
        getClient()
                .perform(get("/api/discover/export")
                            .param("query", "subject:test AND author_authority:" + firstPerson.getID().toString())
                            .param("of", "xm")
                            .param("configuration", "researchoutputs"))
                // The status has to be 200 OK
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(publication1.getID().toString())))
                .andExpect(content().string(containsString(publication2.getID().toString())))
                .andExpect(content().string(not(containsString(publication3.getID().toString()))))
                .andExpect(content().string(containsString(publication4.getID().toString())))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));

        // check that pagination is working
        getClient()
                .perform(
                        get("/api/discover/export")
                            .param("query", "author_authority:" + firstPerson.getID().toString())
                            .param("of", "xm")
                            .param("configuration", "researchoutputs")
                            .param("spc.sd", "ASC")
                            .param("spc.sf", "dc.date.issued")
                            .param("spc.page", "2")
                            .param("spc.rpp", "2"))
                // The status has to be 200 OK
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(publication1.getID().toString()))))
                .andExpect(content().string(not(containsString(publication2.getID().toString()))))
                .andExpect(content().string(containsString(publication3.getID().toString())))
                .andExpect(content().string(containsString(publication4.getID().toString())))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));

        String authToken = getAuthToken(admin.getEmail(), password);
        // no differences expected compared to the anonymousSequentialTest
        getClient(authToken)
                .perform(
                        get("/api/discover/export").param("query", "author_authority:" + firstPerson.getID().toString())
                                .param("of", "xm").param("configuration", "researchoutputs"))
                // The status has to be 200 OK
                .andExpect(status().isOk()).andExpect(content().string(containsString(publication1.getID().toString())))
                .andExpect(content().string(containsString(publication2.getID().toString())))
                .andExpect(content().string(containsString(publication3.getID().toString())))
                .andExpect(content().string(containsString(publication4.getID().toString())))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));

        // check that the query param is really used
        getClient(authToken)
                .perform(get("/api/discover/export")
                            .param("query", "subject:test AND author_authority:" + firstPerson.getID().toString())
                            .param("of", "xm")
                            .param("configuration", "researchoutputs"))
                // The status has to be 200 OK
                .andExpect(status().isOk()).andExpect(content().string(containsString(publication1.getID().toString())))
                .andExpect(content().string(containsString(publication2.getID().toString())))
                .andExpect(content().string(not(containsString(publication3.getID().toString()))))
                .andExpect(content().string(containsString(publication4.getID().toString())))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));

        // check that pagination is working
        getClient(authToken)
                .perform(
                        get("/api/discover/export")
                            .param("query", "author_authority:" + firstPerson.getID().toString())
                            .param("of", "xm")
                            .param("configuration", "researchoutputs")
                            .param("spc.sd", "ASC")
                            .param("spc.sf", "dc.date.issued")
                            .param("spc.page", "2")
                            .param("spc.rpp", "2"))
                // The status has to be 200 OK
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(publication1.getID().toString()))))
                .andExpect(content().string(not(containsString(publication2.getID().toString()))))
                .andExpect(content().string(containsString(publication3.getID().toString())))
                .andExpect(content().string(containsString(publication4.getID().toString())))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));
    }

    @Test
    public void limitedTest() throws Exception {
        configurationService.setProperty("discover-export.limit.notLoggedIn", "1");
        configurationService.setProperty("discover-export.limit.loggedIn", "2");
        configurationService.setProperty("discover-export.limit.admin", "3");
        getClient()
                .perform(
                        get("/api/discover/export")
                            .param("query", "author_authority:" + firstPerson.getID().toString())
                            .param("of", "xm")
                            .param("configuration", "researchoutputs")
                            .param("spc.sd", "ASC")
                            .param("spc.sf", "dc.date.issued"))
                // The status has to be 200 OK
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(publication1.getID().toString())))
                .andExpect(content().string(not(containsString(publication2.getID().toString()))))
                .andExpect(content().string(not(containsString(publication3.getID().toString()))))
                .andExpect(content().string(not(containsString(publication4.getID().toString()))))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));

        getClient()
                .perform(
                        get("/api/discover/export")
                            .param("query", "author_authority:" + firstPerson.getID().toString())
                            .param("of", "xm")
                            .param("configuration", "researchoutputs")
                            .param("spc.sd", "ASC")
                            .param("spc.sf", "dc.date.issued")
                            .param("spc.page", "2")
                            .param("spc.rpp", "2"))
                // The status has to be 200 OK
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(publication1.getID().toString()))))
                .andExpect(content().string(not(containsString(publication2.getID().toString()))))
                .andExpect(content().string(containsString(publication3.getID().toString())))
                .andExpect(content().string(not(containsString(publication4.getID().toString()))))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));

        String authToken = getAuthToken(eperson.getEmail(), password);
        getClient(authToken)
                .perform(
                        get("/api/discover/export")
                            .param("query", "author_authority:" + firstPerson.getID().toString())
                            .param("of", "xm")
                            .param("configuration", "researchoutputs")
                            .param("spc.sd", "ASC")
                            .param("spc.sf", "dc.date.issued"))
                // The status has to be 200 OK
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(publication1.getID().toString())))
                .andExpect(content().string(containsString(publication2.getID().toString())))
                .andExpect(content().string(not(containsString(publication3.getID().toString()))))
                .andExpect(content().string(not(containsString(publication4.getID().toString()))))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));

        getClient(authToken)
                .perform(
                        get("/api/discover/export")
                            .param("query", "author_authority:" + firstPerson.getID().toString())
                            .param("of", "xm")
                            .param("configuration", "researchoutputs")
                            .param("spc.sd", "ASC")
                            .param("spc.sf", "dc.date.issued")
                            .param("spc.page", "2")
                            .param("spc.rpp", "2"))
                // The status has to be 200 OK
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(publication1.getID().toString()))))
                .andExpect(content().string(not(containsString(publication2.getID().toString()))))
                .andExpect(content().string(containsString(publication3.getID().toString())))
                .andExpect(content().string(containsString(publication4.getID().toString())))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken)
                .perform(
                        get("/api/discover/export")
                            .param("query", "author_authority:" + firstPerson.getID().toString())
                            .param("of", "xm")
                            .param("configuration", "researchoutputs")
                            .param("spc.sd", "ASC")
                            .param("spc.sf", "dc.date.issued"))
                // The status has to be 200 OK
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(publication1.getID().toString())))
                .andExpect(content().string(containsString(publication2.getID().toString())))
                .andExpect(content().string(containsString(publication3.getID().toString())))
                .andExpect(content().string(not(containsString(publication4.getID().toString()))))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));

        getClient(adminToken)
                .perform(
                        get("/api/discover/export")
                            .param("query", "author_authority:" + firstPerson.getID().toString())
                            .param("of", "xm")
                            .param("configuration", "researchoutputs")
                            .param("spc.sd", "ASC")
                            .param("spc.sf", "dc.date.issued")
                            .param("spc.page", "2")
                            .param("spc.rpp", "2"))
                // The status has to be 200 OK
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(publication1.getID().toString()))))
                .andExpect(content().string(not(containsString(publication2.getID().toString()))))
                .andExpect(content().string(containsString(publication3.getID().toString())))
                .andExpect(content().string(containsString(publication4.getID().toString())))
                .andExpect(content().string(not(containsString(publicationRestricted.getID().toString()))));
    }

    @Test
    public void concurrentTest() throws Exception {
        ItemExportCrosswalk crosswalk = (ItemExportCrosswalk)
                new DSpace().getSingletonService(StreamDisseminationCrosswalkMapper.class)
                    .getByType("epfl-publication-marc-xml");
        controller.setLowUsageSemaphore(2);
        controller.setHighUsageSemaphore(1);
        controller.setLowUsageAuthenticatedSemaphore(3);
        controller.setHighUsageAuthenticatedSemaphore(2);
        configurationService.setProperty("discover-export.limit.threshold", 50);
        try {
            StreamDisseminationCrosswalk delayed = new ItemExportCrosswalk() {
                int counter;
                @Override
                public String getFileName() {
                    return crosswalk.getFileName();
                }
                @Override
                public void disseminate(Context context, Iterator<? extends DSpaceObject> dsoIterator, OutputStream out)
                        throws CrosswalkException, IOException, SQLException, AuthorizeException {
                    try {
                        Thread.sleep(1000L + 200L * counter++);
                    } catch (InterruptedException e) {
                        // MockMvc is not thread safe see
                        // https://github.com/spring-projects/spring-framework/issues/31543
                        // https://github.com/spring-projects/spring-security/issues/9175
                        // we need to avoid to process the response concurrently
                        // make sure that the requests would take enough time so that our controller would
                        // see the incoming requests as concurrent
                    }
                    crosswalk.disseminate(context, dsoIterator, out);
                }
                @Override
                public String getMIMEType() {
                    return crosswalk.getMIMEType();
                }
                @Override
                public void disseminate(Context context, DSpaceObject dso, OutputStream out)
                        throws CrosswalkException, IOException, SQLException, AuthorizeException {
                    try {
                        Thread.sleep(500L + 100L * counter++);
                    } catch (InterruptedException e) {
                        // MockMvc is not thread safe see
                        // https://github.com/spring-projects/spring-framework/issues/31543
                        // https://github.com/spring-projects/spring-security/issues/9175
                        // we need to avoid to process the response concurrently
                        // make sure that the requests would take enough time so that our controller would
                        // see the incoming requests as concurrent
                    }
                    crosswalk.disseminate(context, dso, out);
                }
                @Override
                public boolean canDisseminate(Context context, DSpaceObject dso) {
                    return crosswalk.canDisseminate(context, dso);
                }
            };
            controller.setStreamDisseminationCrosswalk(delayed);

            // perform a couple of requests in parallel and check if the semaphore works properly
            String authToken = getAuthToken(eperson.getEmail(), password);
            MockHttpServletRequestBuilder request = get("/api/discover/export")
                    .param("query", "author_authority:" + firstPerson.getID().toString()).param("of", "xm")
                    .param("configuration", "researchoutputs");
            MockHttpServletRequestBuilder largeRequest = get("/api/discover/export")
                    .param("query", "author_authority:" + firstPerson.getID().toString()).param("of", "xm")
                    .param("spc.rpp", "51")
                    .param("configuration", "researchoutputs");

            // three concurrent anonymous requests, 1 should get 429
            checkSemaphore(3, null, request, 2, 1);
            // three concurrent large anonymous requests, 2 should get 429
            checkSemaphore(3, null, largeRequest, 1, 2);
            // four concurrent authenticated requests, 1 should get 429
            checkSemaphore(4, authToken, request, 3, 1);
            // four concurrent authenticated large anonymous requests, 2 should get 429
            checkSemaphore(4, authToken, largeRequest, 2, 2);
        } finally {
            // FIXME if other test use the semaphore limits configuration we should consider
            // to restore the default values here
            controller.setStreamDisseminationCrosswalk(crosswalk);
        }
    }

    private void checkSemaphore(int numThreads, String authToken, MockHttpServletRequestBuilder request,
            int exp200, int exp429) throws InterruptedException {
        AtomicInteger numOk = new AtomicInteger(0);
        AtomicInteger num429 = new AtomicInteger(0);
        AtomicInteger numOther = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    MvcResult result = getClient(authToken).perform(request)
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        numOk.incrementAndGet();
                    } else if (result.getResponse().getStatus() == 429) {
                        num429.incrementAndGet();
                    } else {
                        numOther.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            // make sure to give enough time to MockMvc to complete the preparation and send of the request
            Thread.sleep(100L);
        }
        executor.shutdown();
        assertTrue("Executor takes more than 10 sec to process all the requests",
                executor.awaitTermination(10L, TimeUnit.SECONDS));
        assertEquals("Concurrent requests total responses mismatch", numThreads,
                numOk.get() + num429.get() + numOther.get());
        assertEquals("Concurrent requests that get a 200 responses mismatch", exp200, numOk.get());
        assertEquals("Concurrent requests that get a 429 responses mismatch", exp429, num429.get());
    }

}