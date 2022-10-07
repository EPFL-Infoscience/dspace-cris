/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static com.jayway.jsonpath.JsonPath.read;
import static org.dspace.app.rest.matcher.BitstreamMatcher.matchBitstreamEntry;
import static org.dspace.app.rest.matcher.MetadataMatcher.matchMetadata;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.dspace.app.deduplication.utils.MD5ValueSignature;
import org.dspace.app.rest.converter.BitstreamConverter;
import org.dspace.app.rest.converter.DSpaceConverter;
import org.dspace.app.rest.converter.ItemConverter;
import org.dspace.app.rest.model.RestAddressableModel;
import org.dspace.app.rest.projection.Projection;
import org.dspace.app.rest.repository.DeduplicationSetMergeRestRepository;
import org.dspace.app.rest.test.AbstractEntityIntegrationTest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.WorkflowItemBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.RelationshipType;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.EntityTypeService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.RelationshipTypeService;
import org.dspace.core.Constants;
import org.dspace.deduplication.dto.DeduplicationMetadataDTO;
import org.dspace.deduplication.dto.DeduplicationMetadataSourcesDTO;
import org.dspace.deduplication.dto.DeduplicationSetMergeDTO;
import org.dspace.eperson.EPerson;
import org.dspace.workflow.WorkflowItem;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Integration tests for {@link DeduplicationSetMergeRestRepository}.
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 *
 */
public class DeduplicationSetMergeRestRepositoryIT extends AbstractEntityIntegrationTest {

    private MD5ValueSignature md5Signature = new MD5ValueSignature();

    @Autowired
    private ItemConverter itemConverter;

    @Autowired
    private BitstreamConverter bitstreamConverter;

    @Autowired
    private Utils utils;

    @Autowired
    protected RelationshipTypeService relationshipTypeService;

    @Autowired
    protected EntityTypeService entityTypeService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private BundleService bundleService;

    private EPerson submitter;
    private EPerson reviewer;

    private Collection collection;

    private Item item1;
    private Item item2;
    private Item item3;
    private Item item4;
    private Item item5;

    private String itemUri1;
    private String itemUri2;
    private String itemUri3;

    private Bitstream bitstream;
    private Bitstream bitstream1;

    private String bitstreamUri;
    private String bitstreamUri1;

    private String setId;

    private DeduplicationSetMergeDTO deduplicationSetMergeDTO;
    private ObjectMapper mapper;

    /**
     * Build the relationships using the standard test XML with the initialize-entities script
     */
    @Before
    public void setup() throws Exception {

        super.setUp();

        context.turnOffAuthorisationSystem();

        mapper = new ObjectMapper();

        // Two users: one to use as submitter, another one to use as reviewer
        submitter = EPersonBuilder.createEPerson(context)
                                  .withEmail("submitter1@example.com")
                                  .withPassword(password)
                                  .build();
        context.setCurrentUser(submitter);

        reviewer = EPersonBuilder.createEPerson(context)
                                 .withEmail("reviewer1@example.com")
                                 .withPassword(password)
                                 .build();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        collection = CollectionBuilder.createCollection(context, parentCommunity)
                                      .withName("Collection 1")
                                      .withSubmitterGroup(submitter)
                                      .withWorkflowGroup(1, reviewer)
                                      .withWorkflowGroup(2, reviewer)
                                      .withWorkflowGroup(3, reviewer)
                                      .withEntityType("Publication")
                                      .build();

        item1 = ItemBuilder.createItem(context, collection)
                           .withTitle("Test")
                           .withIdentifierDoi("10.1234/123456789")
                           .withAlternativeTitle("item1 title1")
                           .withIssueDate("2010-10-17")
                           .withAuthor("Smith, Donald")
                           .withEditor("editor")
                           .withType("text1")
                           .build();

        item2 = ItemBuilder.createItem(context, collection)
                           .withTitle("Test")
                           .withIdentifierDoi("10.1234/123456789")
                           .withAlternativeTitle("item2 title1")
                           .withAlternativeTitle("item2 title2")
                           .withIssueDate("2015-12-20")
                           .withAuthor("Smith 1, John")
                           .withType("text2")
                           .build();

        item3 = ItemBuilder.createItem(context, collection)
                           .withTitle("Test")
                           .withIdentifierDoi("10.1234/123456789")
                           .withAlternativeTitle("item3 title1")
                           .withAlternativeTitle("item3 title2")
                           .withIssueDate("2015-12-18")
                           .withAuthor("Smith 2, John")
                           .withType("text3")
                           .build();

        item4 = ItemBuilder.createItem(context, collection)
                           .withTitle("Test")
                           .withIssueDate("2015-12-19")
                           .withAuthor("Smith, John")
                           .withAuthor("Smith 4", item2.getID().toString())
                           .withType("text4")
                           .build();

        item5 = ItemBuilder.createItem(context, collection)
                           .withTitle("Test")
                           .withIssueDate("2015-12-25")
                           .withAuthor("Smith, John")
                           .withAuthor("Smith 5", item3.getID().toString())
                           .withType("text5")
                           .build();

        String bitstreamContent = "ThisIsSomeDummyText";

        //Add a bitstream to item2
        bitstream = null;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.
                createBitstream(context, item2, is)
                .withName("Bitstream")
                .withDescription("description")
                .withMimeType("text/plain")
                .build();
        }

        String bitstreamContent1 = "ThisIsSomeDummyTextTest test content for bitstream1";

        //Add a bitstream to item3
        bitstream1 = null;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent1, CharEncoding.UTF_8)) {
            bitstream1 = BitstreamBuilder.
                createBitstream(context, item3, is)
                .withName("Bitstream1")
                .withDescription("description1")
                .withMimeType("text/plain")
                .build();
        }

//      generate URIs for all items and bitstreams that will be merged
        itemUri1 = convertDspaceObjectToUri(itemConverter, item1);
        itemUri2 = convertDspaceObjectToUri(itemConverter, item2);
        itemUri3 = convertDspaceObjectToUri(itemConverter, item3);
        bitstreamUri = convertDspaceObjectToUri(bitstreamConverter, bitstream);
        bitstreamUri1 = convertDspaceObjectToUri(bitstreamConverter, bitstream1);

        setId = createTitleSetId(item1);

//      create the request body DTO
        deduplicationSetMergeDTO = buildDeduplicationSetMergeDTO(setId, itemUri1, itemUri2,
            itemUri3, bitstreamUri, bitstreamUri1);

        context.restoreAuthSystemState();
    }

    @Test
    public void testDedupSetMergeWithGetRequest() throws Exception {

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get("/api/deduplications/merge/" + item1.getID())
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isMethodNotAllowed());
    }

    @Test
    public void testDedupSetMergeUnauthorized() throws Exception {

        getClient().perform(put("/api/deduplications/merge/" + item1.getID())
                       .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                       .contentType(MediaType.APPLICATION_JSON))
                   .andExpect(status().isUnauthorized());
    }

    @Test
    public void testDedupSetMergeIfHasAuthorityNotAdmin() throws Exception {

        String authToken = getAuthToken(eperson.getEmail(), password);
        getClient(authToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                .contentType(MediaType.APPLICATION_JSON))
                            .andExpect(status().isForbidden());
    }

    @Test
    public void testDedupSetMergeIfTargetUUIDIsInvalid() throws Exception {
        String targetId = "invalid_id";
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(put("/api/deduplications/merge/" + targetId)
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isBadRequest());
    }

    @Test
    public void testDedupSetMergeIfTargetItemDoesNotExist() throws Exception {
        String targetId = "6ba5125f-5e78-4b68-834f-25a1c67150e6";
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(put("/api/deduplications/merge/" + targetId)
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isNotFound());
    }

    @Test
    public void testDedupSetMergeIfNotRepeatableMetadata() throws Exception {

        DeduplicationMetadataSourcesDTO source1 = new DeduplicationMetadataSourcesDTO(itemUri1, 0);
        DeduplicationMetadataSourcesDTO source2 = new DeduplicationMetadataSourcesDTO(itemUri2, 0);

        DeduplicationMetadataDTO metadata = new DeduplicationMetadataDTO("dc.contributor.author",
            List.of(source1, source2));

        DeduplicationSetMergeDTO deduplicationSetMergeDTO = new DeduplicationSetMergeDTO( setId,
            List.of(itemUri2),
            List.of(bitstreamUri),
            List.of(metadata)
        );

//        dc.contributor.author not repeatable metadata
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isUnprocessableEntity());

    }

    @Test
    public void testDedupSetMergeIfOneOfItemsDoesNotExist() throws Exception {
        String itemId = "6ba5125f-5e78-4b68-834f-25a1c67150e6";
        String setId = createTitleSetId(item1);
        DeduplicationSetMergeDTO deduplicationSetMergeDTO = buildDeduplicationSetMergeDTO(setId,
            item1.getID().toString(), item2.getID().toString(), itemId, bitstream.getID().toString(),
            bitstream1.getID().toString());

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void testDedupSetMergeIfOneOfBitstreamsDoesNotExist() throws Exception {
        String bitstreamId = "6ba5125f-5e78-4b68-834f-25a1c67150e6";
        String setId = createTitleSetId(item1);
        DeduplicationSetMergeDTO deduplicationSetMergeDTO = buildDeduplicationSetMergeDTO(setId,
            item1.getID().toString(), item2.getID().toString(), item3.getID().toString(), bitstreamId,
            bitstream1.getID().toString());

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void testDedupSetMergeIfItemsHaveTheSameTitle() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);

        context.turnOffAuthorisationSystem();

        Bundle targetBundle = bundleService.create(context, item1, Constants.DEFAULT_BUNDLE_NAME);

        File originalPdf = new File(testProps.getProperty("test.bitstream"));

        //Add a bitstream to target bundle
        Bitstream targetItemBitstream = null;
        try (InputStream is = new FileInputStream(originalPdf)) {
            targetItemBitstream = BitstreamBuilder
                .createBitstream(context, targetBundle, is)
                .withName("Test bitstream")
                .withDescription("This is a bitstream to test the citation cover page.")
                .withMimeType("application/pdf")
                .build();
        }

        context.restoreAuthSystemState();

//      before merge target item has a bundle with one bitstream targetItemBitstream
        getClient().perform(
                       get("/api/core/bundles/" + targetBundle.getID() + "/bitstreams")
                           .param("projection", "full"))
                   .andExpect(status().isOk())
                   .andExpect(content().contentType(contentType))
                   .andExpect(jsonPath("$._embedded.bitstreams", hasSize(1)))
                   .andExpect(jsonPath("$._embedded.bitstreams",
                       containsInAnyOrder(matchBitstreamEntry(targetItemBitstream))));

//      perform merge
        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.targetItem", is(itemUri1)))
                             .andExpect(jsonPath("$.mergedItems", containsInAnyOrder(itemUri2, itemUri3)))
                             .andExpect(jsonPath("$.mergedBitstreams", containsInAnyOrder(bitstreamUri,
                                 bitstreamUri1)))
                             .andExpect(jsonPath("$._embedded.item.id", is(item1.getID().toString())))
                             .andExpect(jsonPath("$._embedded.item.metadata", Matchers.allOf(
                                 matchMetadata("dc.type", "text3"),
                                 matchMetadata("dc.contributor.author", "Smith 2, John"),
                                 matchMetadata("dc.title.alternative", "item1 title1"),
                                 matchMetadata("dc.title.alternative", "item2 title2"),
                                 matchMetadata("dc.title.alternative", "item3 title1"),
                                 matchMetadata("dspace.entity.type", "Publication"),
                                 matchMetadata("dc.title", item1.getName()))))
                             .andExpect(jsonPath(
                                 "$._embedded.item.metadata['dc.contributor.editor']").doesNotExist());

//      after merge target item has a bundle with only merged bitstreams and targetItemBitstream will be removed
        getClient(adminToken).perform(get("/api/core/bundles/" + targetBundle.getID() + "/bitstreams")
                                 .param("projection", "full"))
                             .andExpect(status().isOk())
                             .andExpect(content().contentType(contentType))
                             .andExpect(jsonPath("$._embedded.bitstreams", hasSize(2)))
                             .andExpect(jsonPath("$._embedded.bitstreams", hasItem(matchBitstreamEntry(bitstream))))
                             .andExpect(jsonPath("$._embedded.bitstreams", hasItem(matchBitstreamEntry(bitstream1))))
                             .andExpect(jsonPath("$._embedded.bitstreams", not(
                                 hasItem(matchBitstreamEntry(targetItemBitstream)))));
    }

    @Test
    public void testDedupSetMergeUpdateRelationships() throws Exception {

        context.turnOffAuthorisationSystem();

        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                                                 .withName("Collection 2")
                                                 .withEntityType("Person")
                                                 .build();

        Item author = ItemBuilder.createItem(context, collection)
                                 .withTitle("Author1")
                                 .withIssueDate("2017-10-17")
                                 .withAuthor("Smith, Donald")
                                 .withPersonIdentifierLastName("Smith")
                                 .withPersonIdentifierFirstName("Donald")
                                 .build();

        context.restoreAuthSystemState();

        RelationshipType isAuthorOfPublicationRelationshipType = relationshipTypeService
            .findbyTypesAndTypeName(context, entityTypeService.findByEntityType(context, "Publication"),
                entityTypeService.findByEntityType(context, "Person"),
                "isAuthorOfPublication", "isPublicationOfAuthor");

        AtomicReference<Integer> idRef = new AtomicReference<>();

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(post("/api/core/relationships")
                                 .param("relationshipType",
                                     isAuthorOfPublicationRelationshipType.getID()
                                                                          .toString())
                                 .contentType(MediaType.parseMediaType
                                                           (org.springframework.data.rest.webmvc.RestMediaTypes
                                                               .TEXT_URI_LIST_VALUE))
                                 .content(
                                     "https://localhost:8080/server/api/core/items/" + item2
                                         .getID() + "\n" +
                                         "https://localhost:8080/server/api/core/items/" + author
                                         .getID()))
                             .andExpect(status().isCreated())
                             .andDo(result -> idRef.set(read(result.getResponse().getContentAsString(),
                                 "$.id")));


        getClient().perform(get("/api/core/relationships/" + idRef))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$._links.leftItem.href",
                       containsString(item2.getID().toString())))
                   .andExpect(jsonPath("$._links.rightItem.href",
                       containsString(author.getID().toString())));


        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.targetItem", is(itemUri1)))
                             .andExpect(jsonPath("$.mergedItems", containsInAnyOrder(itemUri2, itemUri3)))
                             .andExpect(jsonPath("$._embedded.item.id", is(item1.getID().toString())))
                             .andExpect(jsonPath("$._embedded.item.metadata", Matchers.allOf(
                                 matchMetadata("dc.type", "text3"),
                                 matchMetadata("dc.contributor.author", "Smith 2, John"),
                                 matchMetadata("dc.title.alternative", "item1 title1"),
                                 matchMetadata("dc.title.alternative", "item2 title2"),
                                 matchMetadata("dc.title.alternative", "item3 title1"),
                                 matchMetadata("dspace.entity.type", "Publication"),
                                 matchMetadata("dc.title", item1.getName()))))
                             .andExpect(jsonPath(
                                 "$._embedded.item.metadata['dc.contributor.editor']").doesNotExist());

        getClient().perform(get("/api/core/relationships/" + idRef))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$._links.leftItem.href",
                       containsString(item1.getID().toString())))
                   .andExpect(jsonPath("$._links.rightItem.href",
                       containsString(author.getID().toString())));
    }

    @Test
    public void testDedupSetMergeUpdateAuthorities() throws Exception {

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/core/items/" + item4.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.metadata", Matchers.allOf(
                                 matchMetadata("dc.contributor.author", "Smith, John",
                                     null, 0),
                                 matchMetadata(
                                     "dc.contributor.author", "Smith 4", item2.getID().toString(), 1
                                 )
                             )));

        getClient(adminToken).perform(get("/api/core/items/" + item5.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.metadata", Matchers.allOf(
                                 matchMetadata("dc.contributor.author", "Smith, John",
                                     null, 0),
                                 matchMetadata(
                                     "dc.contributor.author", "Smith 5", item3.getID().toString(), 1
                                 )
                             )));

        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.targetItem", is(itemUri1)))
                             .andExpect(jsonPath("$.mergedItems", containsInAnyOrder(itemUri2, itemUri3)))
                             .andExpect(jsonPath("$._embedded.item.id", is(item1.getID().toString())))
                             .andExpect(jsonPath("$._embedded.item.metadata", Matchers.allOf(
                                 matchMetadata("dc.type", "text3"),
                                 matchMetadata("dc.contributor.author", "Smith 2, John"),
                                 matchMetadata("dc.title.alternative", "item1 title1"),
                                 matchMetadata("dc.title.alternative", "item2 title2"),
                                 matchMetadata("dc.title.alternative", "item3 title1"),
                                 matchMetadata("dspace.entity.type", "Publication"),
                                 matchMetadata("dc.title", item1.getName()))))
                             .andExpect(jsonPath(
                                 "$._embedded.item.metadata['dc.contributor.editor']").doesNotExist());

        getClient(adminToken).perform(get("/api/core/items/" + item4.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.metadata", Matchers.allOf(
                                 matchMetadata("dc.contributor.author", "Smith, John",
                                     null, 0),
                                 matchMetadata(
                                     "dc.contributor.author", item1.getName(), item1.getID().toString(), 1
                                 )
                             )));

        getClient(adminToken).perform(get("/api/core/items/" + item5.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.metadata", Matchers.allOf(
                                 matchMetadata("dc.contributor.author", "Smith, John",
                                     null, 0),
                                 matchMetadata(
                                     "dc.contributor.author", item1.getName(), item1.getID().toString(), 1
                                 )
                             )));
    }

    @Test
    public void testWithdrawnOtherItemsAfterMerge() throws Exception {

        context.turnOffAuthorisationSystem();

        WorkspaceItem workspaceItem = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
                                                          .withTitle("Test")
                                                          .withIssueDate("2015-12-20")
                                                          .withAuthor("Smith 1, John")
                                                          .withAlternativeTitle("item2 title1")
                                                          .withAlternativeTitle("item2 title2")
                                                          .withType("text2")
                                                          .build();

        Item item2 = workspaceItem.getItem();

        context.restoreAuthSystemState();

//       create the request body DTO
        deduplicationSetMergeDTO = buildDeduplicationSetMergeDTO(setId, itemUri1, item2.getID().toString(),
            itemUri3, bitstreamUri, bitstreamUri1);

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/submission/workspaceitems/" + workspaceItem.getID()))
                             .andExpect(status().isOk());

        getClient(adminToken).perform(get("/api/core/items/" + item1.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.withdrawn", is(false)));

        getClient(adminToken).perform(get("/api/core/items/" + item3.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.withdrawn", is(false)));

        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk());

        getClient(adminToken).perform(get("/api/core/items/" + item1.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.withdrawn", is(false)));

        getClient(adminToken).perform(get("/api/core/items/" + item3.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.withdrawn", is(true)));

//        Trying to get deleted workspace item should fail with 404
        getClient(adminToken).perform(get("/api/submission/workspaceitems/" + workspaceItem.getID()))
                             .andExpect(status().isNotFound());

//        Trying to get deleted item should fail with 404
        getClient(adminToken).perform(get("/api/core/items/" + workspaceItem.getItem().getID()))
                             .andExpect(status().isNotFound());

    }

    @Test
    public void testRemoveItemsFromSetAfterMerge() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/deduplications/sets/" + setId + "/items"))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$._links.self.href",
                                 Matchers.containsString("/api/deduplications/sets/" + setId + "/items")))
                             .andExpect(jsonPath("$.page.number", is(0)))
                             .andExpect(jsonPath("$.page.size", is(20)))
                             .andExpect(jsonPath("$.page.totalPages", is(1)))
                             .andExpect(jsonPath("$.page.totalElements", is(5)));

//        here will merge three items
        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk());

//        check that Set not exist anymore
        getClient(adminToken).perform(get("/api/deduplications/sets/" + setId + "/items"))
                             .andExpect(status().isNotFound());

    }

    @Test
    public void testRemoveItemsFromSetAfterMergeAndExistedIntoAnotherSet() throws Exception {

        String titleSetId = createTitleSetId(item1);
        String identifierSetId = createIdentifierSetId(item1);

        String adminToken = getAuthToken(admin.getEmail(), password);

        // duplicated by title
        getClient(adminToken).perform(get("/api/deduplications/sets/" + titleSetId + "/items"))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$._links.self.href",
                                 Matchers.containsString("/api/deduplications/sets/" + titleSetId + "/items")))
                             .andExpect(jsonPath("$.page.number", is(0)))
                             .andExpect(jsonPath("$.page.size", is(20)))
                             .andExpect(jsonPath("$.page.totalPages", is(1)))
                             .andExpect(jsonPath("$.page.totalElements", is(5)));

        // duplicated by identifier
        getClient(adminToken).perform(get("/api/deduplications/sets/" + identifierSetId + "/items"))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$._links.self.href",
                                 Matchers.containsString("/api/deduplications/sets/" + identifierSetId + "/items")))
                             .andExpect(jsonPath("$.page.number", is(0)))
                             .andExpect(jsonPath("$.page.size", is(20)))
                             .andExpect(jsonPath("$.page.totalPages", is(1)))
                             .andExpect(jsonPath("$.page.totalElements", is(3)));

//        here will merge three items
        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk());

//        check that Title Set not exist anymore
        getClient(adminToken).perform(get("/api/deduplications/sets/" + titleSetId + "/items"))
                             .andExpect(status().isNotFound());

//        check that Identifier Set not exist anymore, cause items have merged above by title
        getClient(adminToken).perform(get("/api/deduplications/sets/" + identifierSetId + "/items"))
                             .andExpect(status().isNotFound());

    }

    @Test
    public void givenItemsInSetWhenMergingAnItemWithoutSetIdThenNothingHappensToSet() throws Exception {

        // create the request body DTO without setId
        deduplicationSetMergeDTO = buildDeduplicationSetMergeDTO(null, itemUri1, itemUri2,
            itemUri3, bitstreamUri, bitstreamUri1);

        String adminToken = getAuthToken(admin.getEmail(), password);

//        check that Set contains all items before merge
        getClient(adminToken).perform(get("/api/deduplications/sets/" + setId + "/items"))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$._links.self.href",
                                 Matchers.containsString("/api/deduplications/sets/" + setId + "/items")))
                             .andExpect(jsonPath("$.page.number", is(0)))
                             .andExpect(jsonPath("$.page.size", is(20)))
                             .andExpect(jsonPath("$.page.totalPages", is(1)))
                             .andExpect(jsonPath("$.page.totalElements", is(5)));

//        here will merge three items
        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk());

//        check that Set contains all items after merge
        getClient(adminToken).perform(get("/api/deduplications/sets/" + setId + "/items"))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$._links.self.href",
                                 Matchers.containsString("/api/deduplications/sets/" + setId + "/items")))
                             .andExpect(jsonPath("$.page.number", is(0)))
                             .andExpect(jsonPath("$.page.size", is(20)))
                             .andExpect(jsonPath("$.page.totalPages", is(1)))
                             .andExpect(jsonPath("$.page.totalElements", is(5)));

    }

    @Test
    public void testDedupSetMergeCreationOfRelationships() throws Exception {

        String adminToken = getAuthToken(admin.getEmail(), password);

        context.turnOffAuthorisationSystem();

        RelationshipType isMergedFromItemRelationshipType = relationshipTypeService
            .findbyTypesAndTypeName(context, entityTypeService.findByEntityType(context, "Publication"),
                entityTypeService.findByEntityType(context, "Publication"),
                "isMergedFromItem", "isMergedInItem");

        context.restoreAuthSystemState();

        // no relationship between items before merge
        getClient().perform(get("/api/core/relationships/search/byItemsAndType")
                       .param("typeId", isMergedFromItemRelationshipType.getID().toString())
                       .param("relationshipLabel", "isMergedFromItem")
                       .param("focusItem", item1.getID().toString())
                       .param("relatedItem", item2.getID().toString(),
                           item3.getID().toString()))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.page.totalPages", is(0)))
                   .andExpect(jsonPath("$.page.totalElements", is(0)));

        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.targetItem", is(itemUri1)))
                             .andExpect(jsonPath("$.mergedItems", containsInAnyOrder(itemUri2, itemUri3)))
                             .andExpect(jsonPath("$._embedded.item.id", is(item1.getID().toString())))
                             .andExpect(jsonPath("$._embedded.item.metadata", Matchers.allOf(
                                 matchMetadata("dc.type", "text3"),
                                 matchMetadata("dc.contributor.author", "Smith 2, John"),
                                 matchMetadata("dc.title.alternative", "item1 title1"),
                                 matchMetadata("dc.title.alternative", "item2 title2"),
                                 matchMetadata("dc.title.alternative", "item3 title1"),
                                 matchMetadata("dspace.entity.type", "Publication"),
                                 matchMetadata("dc.title", item1.getName()))))
                             .andExpect(jsonPath(
                                 "$._embedded.item.metadata['dc.contributor.editor']").doesNotExist());

//      there are two relationships between target item and only the merged items ( item2, item3).
        getClient().perform(get("/api/core/relationships/search/byItemsAndType")
                       .param("typeId", isMergedFromItemRelationshipType.getID().toString())
                       .param("relationshipLabel", "isMergedFromItem")
                       .param("focusItem", item1.getID().toString())
                       .param("relatedItem", item2.getID().toString(), item3.getID().toString(),
                           item4.getID().toString() ))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.page.totalPages", is(1)))
                   .andExpect(jsonPath("$.page.totalElements", is(2)));
    }

    @Test
    public void testDedupSetMergeAddingUriMetadata() throws Exception {

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(put("/api/deduplications/merge/" + item1.getID())
                                 .content(mapper.writeValueAsBytes(deduplicationSetMergeDTO))
                                 .contentType(MediaType.APPLICATION_JSON))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.targetItem", is(itemUri1)))
                             .andExpect(jsonPath("$.mergedItems", containsInAnyOrder(itemUri2, itemUri3)))
                             .andExpect(jsonPath("$._embedded.item.id", is(item1.getID().toString())))
                             .andExpect(jsonPath("$._embedded.item.metadata", Matchers.allOf(
                                 matchMetadata("dc.type", "text3"),
                                 matchMetadata("dc.contributor.author", "Smith 2, John"),
                                 matchMetadata("dc.title.alternative", "item1 title1"),
                                 matchMetadata("dc.title.alternative", "item2 title2"),
                                 matchMetadata("dc.title.alternative", "item3 title1"),
                                 matchMetadata("dspace.entity.type", "Publication"),
                                 matchMetadata("dc.title", item1.getName()))))
                             .andExpect(jsonPath(
                                 "$._embedded.item.metadata['dc.contributor.editor']").doesNotExist());

        item2 = context.reloadEntity(item2);
        item3 = context.reloadEntity(item3);

        // then merged items will contain uri metadata of target item
        String targetUri = itemService.getMetadata(item1, "dc.identifier.uri");
        String mergedItemTwoUri = itemService.getMetadata(item2, "dspace.merge.target-uri");
        String mergedIThreeUri = itemService.getMetadata(item3, "dspace.merge.target-uri");

        assertEquals(mergedItemTwoUri, targetUri);
        assertEquals(mergedIThreeUri, targetUri);
    }

    @Test
    public void testFindTargetsWithAnonymousUser() throws Exception {

        getClient().perform(get("/api/deduplications/merge/search/findTargets")
                       .param("uuid", item1.getID().toString()))
                   .andExpect(status().isUnauthorized());

    }

    @Test
    public void testFindTargetsWithNotAdminUser() throws Exception {

        String epersonToken = getAuthToken(eperson.getEmail(), password);

        getClient(epersonToken).perform(get("/api/deduplications/merge/search/findTargets")
                                   .param("uuid", item1.getID().toString()))
                               .andExpect(status().isForbidden());

    }

    @Test
    public void testFindTargetsWithAdminUser() throws Exception {

        context.turnOffAuthorisationSystem();

        Item item1 = ItemBuilder.createItem(context, collection)
                                .withTitle("item 1")
                                .build();

//        archived item
        Item item2 = ItemBuilder.createItem(context, collection)
                                .withTitle("item 2")
                                .build();

//        workspace item
        WorkspaceItem workspaceItem = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
                                                          .withTitle("workspace item")
                                                          .build();
        Item item3 = workspaceItem.getItem();

//        workflow item
        WorkflowItem workflowItem = WorkflowItemBuilder.createWorkflowItem(context, collection)
                                                       .withTitle("workflow item")
                                                       .build();
        Item item4 = workspaceItem.getItem();

        String fakeId = "9f28fd77-1ebd-445a-aeee-2c1c1be36033";

//       unarchive item1
        item1 = context.reloadEntity(item1);
        item1.setArchived(false);
        itemService.update(context, item1);

        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/deduplications/merge/search/findTargets")
                                 .param("uuid", item1.getID().toString())
                                 .param("uuid", item2.getID().toString())
                                 .param("uuid", item3.getID().toString())
                                 .param("uuid", item4.getID().toString())
                                 .param("uuid", fakeId))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.allowedTargets", hasSize(1)))
                             .andExpect(jsonPath("$.allowedTargets",
                                 containsInAnyOrder(equalTo(convertDspaceObjectToUri(itemConverter, item2)))))
                             .andExpect(jsonPath("$._links.self.href",
                                 containsString("/api/deduplications/merge/search/findTargets" +
                                     "?uuid=" + item1.getID().toString() +
                                     "&uuid=" + item2.getID().toString() +
                                     "&uuid=" + item3.getID().toString() +
                                     "&uuid=" + item4.getID().toString() +
                                     "&uuid=" + fakeId)));

    }

    @Test
    public void testFindTargetsNotFound() throws Exception {

        context.turnOffAuthorisationSystem();

        Item item1 = ItemBuilder.createItem(context, collection)
                                .withTitle("item 1")
                                .build();

//        workspace item
        WorkspaceItem workspaceItem = WorkspaceItemBuilder.createWorkspaceItem(context, collection)
                                                          .withTitle("workspace item")
                                                          .build();
        Item item2 = workspaceItem.getItem();

//        workflow item
        WorkflowItem workflowItem = WorkflowItemBuilder.createWorkflowItem(context, collection)
                                                       .withTitle("workflow item")
                                                       .build();
        Item item3 = workspaceItem.getItem();

//        unarchive item1
        item1 = context.reloadEntity(item1);
        item1.setArchived(false);
        itemService.update(context, item1);

        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/deduplications/merge/search/findTargets")
                                 .param("uuid", item1.getID().toString())
                                 .param("uuid", item2.getID().toString())
                                 .param("uuid", item3.getID().toString()))
                             .andExpect(status().isNotFound());

    }

    private String createTitleSetId(Item item) {
        // Set up MD5ValueSignature state to produce the same signature
        setMD5ValueSignatureInstance("dc.title", null, "title",
            new ArrayList<>(), "[^\\p{L}]");
        return "title:" + md5Signature.getSignature(item, context).get(0);
    }

    private String createIdentifierSetId(Item item) {
        List<String> ignorePrefix =
            Arrays.asList("doi://", "doi:", "DOI:", "DOI://", "http://dx.doi.org/","dx.doi.org/");

        // Set up MD5ValueSignature state to produce the same signature
        setMD5ValueSignatureInstance("dc.identifier.doi", "doi:", "identifier", ignorePrefix, "");
        return "identifier:" + md5Signature.getSignature(item, context).get(0);
    }

    private DeduplicationSetMergeDTO buildDeduplicationSetMergeDTO(String id, String item1, String item2, String item3,
                                                                   String bitstream, String bitstream1) {

        DeduplicationMetadataSourcesDTO source1 = new DeduplicationMetadataSourcesDTO(item1, 0);
        DeduplicationMetadataSourcesDTO source2 = new DeduplicationMetadataSourcesDTO(item2, 1);
        DeduplicationMetadataSourcesDTO source3 = new DeduplicationMetadataSourcesDTO(item3, 0);

        DeduplicationMetadataDTO metadata1 = new DeduplicationMetadataDTO("dc.title.alternative",
            List.of(source1, source2, source3));
        DeduplicationMetadataDTO metadata2 = new DeduplicationMetadataDTO("dc.type",
            List.of(source3));
        DeduplicationMetadataDTO metadata3 = new DeduplicationMetadataDTO("dc.contributor.author",
            List.of(source3));
        DeduplicationMetadataDTO metadata4 = new DeduplicationMetadataDTO("dc.contributor.editor",
            new ArrayList<>());

        DeduplicationSetMergeDTO deduplicationSetMergeDTO = new DeduplicationSetMergeDTO( id,
            List.of(item2, item3),
            List.of(bitstream, bitstream1),
            List.of(metadata1, metadata2, metadata3, metadata4)
        );

        return deduplicationSetMergeDTO;
    }

    private void setMD5ValueSignatureInstance(String metadata, String prefix, String signatureType,
                                              List<String> ignorePrefixes, String normalizeRegex) {
        md5Signature.setMetadata(metadata);
        md5Signature.setPrefix(prefix);
        md5Signature.setSignatureType(signatureType);
        md5Signature.setIgnorePrefix(ignorePrefixes);
        md5Signature.setNormalizationRegexp(normalizeRegex);
    }

    private String convertDspaceObjectToUri(DSpaceConverter converter, DSpaceObject item) {
        return utils.linkToSingleResource(
            (RestAddressableModel) converter.convert(item, Projection.DEFAULT), "self"
        ).getHref();
    }

}
