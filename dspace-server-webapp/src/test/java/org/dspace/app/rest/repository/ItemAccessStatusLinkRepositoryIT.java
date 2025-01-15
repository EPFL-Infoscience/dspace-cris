/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import static org.dspace.access.status.AccessStatusHelper.EMBARGO;
import static org.dspace.access.status.AccessStatusHelper.METADATA_ONLY;
import static org.dspace.access.status.AccessStatusHelper.OPEN_ACCESS;
import static org.dspace.access.status.AccessStatusHelper.RESTRICTED;
import static org.dspace.access.status.AccessStatusHelper.UNKNOWN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.event.factory.EventServiceFactory;
import org.dspace.event.service.EventService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.hamcrest.Matchers;
import org.joda.time.LocalDate;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test class for {@link ItemAccessStatusLinkRepository}
 *
 * @author Mohamed Eskander(mohamed.eskander at 4science.com)
 */
public class ItemAccessStatusLinkRepositoryIT extends AbstractControllerIntegrationTest {

    private static ConfigurationService configService = DSpaceServicesFactory.getInstance().getConfigurationService();
    private static final EventService eventService = EventServiceFactory.getInstance().getEventService();

    private static String[] consumers;
    private Community parentCommunity;
    private Collection collection1;
    private Item item;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .build();
        collection1 = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection 1")
                .build();
        context.restoreAuthSystemState();
    }

    /**
     * This method will be run before the first test as per @BeforeClass. It will
     * configure the event.dispatcher.default.consumers property to remove the
     * PolicyMetadataEnhancerConsumer. This because that consumer changes the default
     * behaviour and will invalidate some of the tests that belong to this class.
     * The new behaviour is described in the test class for the consumer, so it is already
     * checked {@link PolicyMetadataEnhancerConsumerIT}
     */
    @BeforeClass
    public static void initConsumers() {
        consumers = configService.getArrayProperty("event.dispatcher.default.consumers");
        Set<String> consumersSet = new HashSet<String>(Arrays.asList(consumers));
        if (consumersSet.contains("policymetadataenhancer")) {
            consumersSet.remove("policymetadataenhancer");
            configService.setProperty("event.dispatcher.default.consumers", consumersSet.toArray());
            eventService.reloadConfiguration();
        }
    }

    /**
     * Reset the event.dispatcher.default.consumers property value.
     */
    @AfterClass
    public static void resetDefaultConsumers() {
        configService.setProperty("event.dispatcher.default.consumers", consumers);
        eventService.reloadConfiguration();
    }

    @Test
    public void getItemAccessStatusUnknownTest() throws Exception {
        context.turnOffAuthorisationSystem();
        item = ItemBuilder.createItem(context, collection1)
                .withTitle("test item")
                .build();
        context.restoreAuthSystemState();
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/core/items/" + item.getID() + "/accessStatus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", Matchers.is(UNKNOWN)));
    }

    @Test
    public void getItemAccessStatusMetadataOnlyTest() throws Exception {
        context.turnOffAuthorisationSystem();
        item = ItemBuilder.createItem(context, collection1)
                .withTitle("test item")
                .withDataCiteRights("metadata-only")
                .build();
        context.restoreAuthSystemState();
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/core/items/" + item.getID() + "/accessStatus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", Matchers.is(METADATA_ONLY)));
    }

    @Test
    public void getItemAccessStatusRestrictedTest() throws Exception {
        context.turnOffAuthorisationSystem();
        item = ItemBuilder.createItem(context, collection1)
                .withTitle("test item")
                .withDataCiteRights("restricted")
                .build();
        context.restoreAuthSystemState();
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/core/items/" + item.getID() + "/accessStatus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", Matchers.is(RESTRICTED)));
    }

    @Test
    public void getItemAccessStatusOpenAccessTest() throws Exception {
        context.turnOffAuthorisationSystem();
        item = ItemBuilder.createItem(context, collection1)
                .withTitle("test item")
                .withDataCiteRights("openaccess")
                .build();
        context.restoreAuthSystemState();
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/core/items/" + item.getID() + "/accessStatus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", Matchers.is(OPEN_ACCESS)));
    }

    @Test
    public void getItemAccessStatusValidEmbargoTest() throws Exception {
        context.turnOffAuthorisationSystem();
        item = ItemBuilder.createItem(context, collection1)
                .withTitle("test item")
                .withDataCiteRights("embargo")
                .withDataCiteAvailable(LocalDate.now().plusDays(20).toString())
                .build();
        context.restoreAuthSystemState();
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/core/items/" + item.getID() + "/accessStatus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", Matchers.is(EMBARGO)));
    }

    @Test
    public void getItemAccessStatusInvalidEmbargoTest() throws Exception {
        context.turnOffAuthorisationSystem();
        item = ItemBuilder.createItem(context, collection1)
                .withTitle("test item")
                .withDataCiteRights("embargo")
                .withDataCiteAvailable(LocalDate.now().minusDays(20).toString())
                .build();

        Item itemTwo = ItemBuilder.createItem(context, collection1)
                .withTitle("test item two")
                .withDataCiteRights("embargo")
                .withDataCiteAvailable("fake")
                .build();
        Item itemThree = ItemBuilder.createItem(context, collection1)
                .withTitle("test item three")
                .withDataCiteRights("embargo")
                .withDataCiteAvailable("")
                .build();
        context.restoreAuthSystemState();
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/core/items/" + item.getID() + "/accessStatus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", Matchers.is(OPEN_ACCESS)));

        getClient(adminToken).perform(get("/api/core/items/" + itemTwo.getID() + "/accessStatus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", Matchers.is(OPEN_ACCESS)));

        getClient(adminToken).perform(get("/api/core/items/" + itemThree.getID() + "/accessStatus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", Matchers.is(OPEN_ACCESS)));
    }

}
