/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.policy.consumer;

import static org.dspace.app.matcher.MetadataValueMatcher.with;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.tools.ant.filters.StringInputStream;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.policy.PolicyMetadataUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.ResourcePolicyBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.IndexingService;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.SearchUtils;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.utils.DSpace;
import org.junit.Before;
import org.junit.Test;

public class PolicyMetadataEnhancerConsumerIT extends AbstractIntegrationTestWithDatabase {

    private static final String TYPE_CUSTOM = "TYPE_CUSTOM";

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private Collection collection;

    private ResourcePolicyService resourcePolicyService = ContentServiceFactory.getInstance()
            .getResourcePolicyService();
    private BitstreamService bitstreamService = ContentServiceFactory.getInstance()
            .getBitstreamService();
    private ItemService itemService = ContentServiceFactory.getInstance()
            .getItemService();

    private IndexingService indexService = new DSpace().getSingletonService(IndexingService.class);
    private SearchService searchService = SearchUtils.getSearchService();

    @Before
    public void setup() throws SQLException {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).withName("Parent Community").build();

        collection = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection 1").build();

        context.restoreAuthSystemState();
    }

    @Test
    public void testWithoutBitstreams()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        context.restoreAuthSystemState();
        context.commit();

        item = context.reloadEntity(item);

        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.METADATA_ONLY)));
        assertThat(item.getMetadata(), not(hasItem(with("datacite.available", null))));

        context.turnOffAuthorisationSystem();
        this.itemService.update(context, item);
        context.restoreAuthSystemState();

        item = context.reloadEntity(item);

        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.METADATA_ONLY)));
        assertThat(item.getMetadata(), not(hasItem(with("datacite.available", null))));
        Optional<String> accessionedDate = getAccessionedDate(item);
        assertThat(accessionedDate.isPresent(), is(true));
        assertThat(item.getMetadata(), hasItem(with("dc.date.available", accessionedDate.get())));
    }

    @Test
    public void testWithoutPolicyType()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder
                    .createBitstream(context, item, new StringInputStream("test"))
                    .build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN)));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", null))));
        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN)));
        assertThat(item.getMetadata(), not(hasItem(with("datacite.available", null))));

        Optional<String> accessionedDate = getAccessionedDate(item);
        assertThat(accessionedDate.isPresent(), is(true));
        assertThat(item.getMetadata(), hasItem(with("dc.date.available", accessionedDate.get())));
    }

    @Test
    public void testWithoutPolicyTypeDelete()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder
                .createBitstream(context, item, new StringInputStream("test"))
                .build();

        ResourcePolicyBuilder.createResourcePolicy(context, admin, null).withDspaceObject(bitstream)
            .withAction(Constants.READ)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        context.turnOffAuthorisationSystem();

        this.bitstreamService.delete(context, bitstream);

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(),
                not(hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN))));
        assertThat(bitstream.getMetadata(),
                not(hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_RESTRICTED))));
        assertThat(item.getMetadata(),
                not(hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN))));
        assertThat(item.getMetadata(),
                not(hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_RESTRICTED))));
        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.METADATA_ONLY)));
        Optional<String> accessionedDate = getAccessionedDate(item);
        assertThat(accessionedDate.isPresent(), is(true));
        assertThat(item.getMetadata(), hasItem(with("dc.date.available", accessionedDate.get())));
    }

    @Test
    public void testWithoutPolicyName()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test")).build();

        ResourcePolicyBuilder.createResourcePolicy(context, admin, null).withDspaceObject(bitstream)
            .withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN)));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", null))));
        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN)));
        assertThat(item.getMetadata(), not(hasItem(with("datacite.available", null))));
        Optional<String> accessionedDate = getAccessionedDate(item);
        assertThat(accessionedDate.isPresent(), is(true));
        assertThat(item.getMetadata(), hasItem(with("dc.date.available", accessionedDate.get())));

        context.turnOffAuthorisationSystem();

        bitstream = context.reloadEntity(bitstream);
        List<ResourcePolicy> resourcePolicies = bitstream.getResourcePolicies();

        Iterator<ResourcePolicy> iterator = resourcePolicies.iterator();
        while (iterator.hasNext()) {
            try {
                ResourcePolicy next = iterator.next();
                resourcePolicies.remove(next);
                this.resourcePolicyService.delete(context, next);
            } catch (AuthorizeException | SQLException e) {
                throw new RuntimeException(e);
            }
        }

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_RESTRICTED)));
        assertThat(bitstream.getMetadata(),
                not(hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN))));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", null))));
        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_RESTRICTED)));
        assertThat(item.getMetadata(),
                not(hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN))));
        assertThat(item.getMetadata(), not(hasItem(with("datacite.available", null))));
        assertThat(item.getMetadata(), not(hasItem(with("dc.date.available", null))));
    }

    @Test
    public void testWithPolicyName()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test")).build();

        ResourcePolicyBuilder.createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream)
            .withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM)
            .withName(PolicyMetadataUtils.ACCESS_OPEN)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights",
                PolicyMetadataUtils.ACCESS_OPEN)));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", null))));
        assertThat(item.getMetadata(), hasItem(with("datacite.rights",
                PolicyMetadataUtils.ACCESS_OPEN)));
        assertThat(item.getMetadata(), not(hasItem(with("datacite.available", null))));
        Optional<String> accessionedDate = getAccessionedDate(item);
        assertThat(accessionedDate.isPresent(), is(true));
        assertThat(item.getMetadata(), hasItem(with("dc.date.available", accessionedDate.get())));
    }

    @Test
    public void testWithPolicyNameStartDate()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test")).build();

        String embargoDate = "2022-08-16";
        ResourcePolicyBuilder.createResourcePolicy(context, admin, null)
             .withDspaceObject(bitstream).withAction(Constants.READ)
             .withPolicyType(TYPE_CUSTOM).withName("embargo")
             .withStartDate(dateFormat.parse(embargoDate)).build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", "embargo")));
        assertThat(bitstream.getMetadata(), hasItem(with("datacite.available", embargoDate)));
        assertThat(item.getMetadata(), hasItem(with("datacite.rights", "embargo")));
        assertThat(item.getMetadata(), hasItem(with("datacite.available", embargoDate)));
        assertThat(item.getMetadata(), hasItem(with("dc.date.available", embargoDate)));
        Optional<String> accessionedDate = getAccessionedDate(item);
        assertThat(accessionedDate.isPresent(), is(true));
        assertThat(item.getMetadata(), not(hasItem(with("dc.date.available", accessionedDate.get()))));
    }

    @Test
    public void testWithPolicyNameStartDateEdited()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test")).build();

        String embargoDate = "2022-08-16";
        ResourcePolicyBuilder
            .createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream)
            .withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM)
            .withName("embargo")
            .withStartDate(dateFormat.parse(embargoDate))
            .build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        ResourcePolicy resourcePolicy = bitstream.getResourcePolicies()
                            .stream()
                            .filter(rp -> TYPE_CUSTOM.equals(rp.getRpType()))
                            .findFirst()
                            .orElseThrow();

        context.turnOffAuthorisationSystem();

        resourcePolicy.setRpName("test");
        resourcePolicy.setStartDate(null);

        this.resourcePolicyService.update(context, resourcePolicy);

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", "test")));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", embargoDate))));
        assertThat(item.getMetadata(), hasItem(with("datacite.rights", "test")));
        assertThat(item.getMetadata(), not(hasItem(with("datacite.available", embargoDate))));
        assertThat(item.getMetadata(), not(hasItem(with("dc.date.available", embargoDate))));
        Optional<String> accessionedDate = getAccessionedDate(item);
        assertThat(accessionedDate.isPresent(), is(true));
        assertThat(item.getMetadata(), not(hasItem(with("dc.date.available", accessionedDate.get()))));
    }

    @Test
    public void testWithPolicyNameStartDateDeleted()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test")).build();

        String embargoDate = "2022-08-16";
        ResourcePolicyBuilder
            .createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream)
            .withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM)
            .withName("embargo")
            .withStartDate(dateFormat.parse(embargoDate))
            .build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", "embargo")));
        assertThat(bitstream.getMetadata(), hasItem(with("datacite.available", embargoDate)));
        assertThat(item.getMetadata(), hasItem(with("datacite.rights", "embargo")));
        assertThat(item.getMetadata(), hasItem(with("datacite.available", embargoDate)));
        assertThat(item.getMetadata(), hasItem(with("dc.date.available", embargoDate)));

        List<ResourcePolicy> resourcePolicies = bitstream.getResourcePolicies();
        ResourcePolicy resourcePolicy = resourcePolicies
                .stream()
                .filter(rp -> TYPE_CUSTOM.equals(rp.getRpType()))
                .findFirst()
                .orElseThrow();

        context.turnOffAuthorisationSystem();

        resourcePolicies.remove(resourcePolicy);
        resourcePolicy.setRpName(PolicyMetadataUtils.ACCESS_RESTRICTED);
        resourcePolicy.setStartDate(null);

        this.resourcePolicyService.delete(context, resourcePolicy);

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.rights", "embargo"))));
        assertThat(bitstream.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN)));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", embargoDate))));
        assertThat(item.getMetadata(), not(hasItem(with("datacite.rights", "embargo"))));
        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN)));
        assertThat(item.getMetadata(), not(hasItem(with("datacite.available", embargoDate))));
        assertThat(item.getMetadata(), not(hasItem(with("dc.date.available", embargoDate))));
        Optional<String> accessionedDate = getAccessionedDate(item);
        assertThat(accessionedDate.isPresent(), is(true));
        assertThat(item.getMetadata(), hasItem(with("dc.date.available", accessionedDate.get())));
    }

    @Test
    public void testWithTwoBitStreamPolicyDeletedInverted()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test")).build();
        Bitstream bitstream2 = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test2")).build();

        String embargoDate = "2022-08-16";
        ResourcePolicyBuilder
            .createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream)
            .withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM)
            .withName("embargo")
            .withStartDate(dateFormat.parse(embargoDate))
            .build();
        ResourcePolicyBuilder
            .createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream2)
            .withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM)
            .withName(PolicyMetadataUtils.ACCESS_OPEN)
            .build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        bitstream2 = context.reloadEntity(bitstream2);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", "embargo")));
        assertThat(bitstream.getMetadata(), hasItem(with("datacite.available", embargoDate)));
        assertThat(bitstream2.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN)));
        assertThat(bitstream2.getMetadata(), not(hasItem(with("datacite.available", embargoDate))));
        assertThat(item.getMetadata(), hasItem(with("datacite.rights", "embargo")));
        assertThat(item.getMetadata(), hasItem(with("datacite.available", embargoDate)));
        assertThat(item.getMetadata(), hasItem(with("dc.date.available", embargoDate)));
        assertThat(item.getMetadata(),
                not(hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_OPEN))));

        List<ResourcePolicy> resourcePolicies = bitstream.getResourcePolicies();
        ResourcePolicy resourcePolicy = resourcePolicies
                .stream()
                .filter(rp -> TYPE_CUSTOM.equals(rp.getRpType()))
                .findFirst()
                .orElseThrow();

        context.turnOffAuthorisationSystem();

        resourcePolicies.remove(resourcePolicy);
        this.resourcePolicyService.delete(context, resourcePolicy);

        context.restoreAuthSystemState();

        resourcePolicies = bitstream2.getResourcePolicies();
        resourcePolicy = resourcePolicies
                .stream()
                .filter(rp -> TYPE_CUSTOM.equals(rp.getRpType()))
                .findFirst()
                .orElseThrow();

        context.turnOffAuthorisationSystem();

        resourcePolicies.remove(resourcePolicy);
        this.resourcePolicyService.delete(context, resourcePolicy);

        ResourcePolicyBuilder
            .createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream)
            .withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM)
            .withName(PolicyMetadataUtils.ACCESS_RESTRICTED)
            .build();

        ResourcePolicyBuilder
            .createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream2)
            .withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM)
            .withName("embargo")
            .withStartDate(dateFormat.parse(embargoDate))
            .build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.rights", "embargo"))));
        assertThat(bitstream.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_RESTRICTED)));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", embargoDate))));
        assertThat(bitstream2.getMetadata(), hasItem(with("datacite.rights", "embargo")));
        assertThat(bitstream2.getMetadata(), hasItem(with("datacite.available", embargoDate)));
        assertThat(item.getMetadata(), not(hasItem(with("datacite.rights", "embargo"))));
        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.ACCESS_RESTRICTED)));
        assertThat(item.getMetadata(), not(hasItem(with("datacite.available", embargoDate))));
        assertThat(item.getMetadata(), not(hasItem(with("dc.date.available", embargoDate))));
        Optional<String> accessionedDate = getAccessionedDate(item);
        assertThat(accessionedDate.isPresent(), is(true));
        assertThat(item.getMetadata(), not(hasItem(with("dc.date.available", accessionedDate.get()))));
    }

    @Test
    public void testBitstreamViewerProvider()
        throws SQLException, AuthorizeException, IOException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream pdfBitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test"))
                                                 .withMimeType("application/pdf")
                                                 .build();
        Bitstream txtBitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test4"))
                                                 .withMimeType("text/plain")
                                                 .build();

        Bitstream noMimeTypeBitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test4"))
                                                 .build();

        context.restoreAuthSystemState();
        context.commit();

        pdfBitstream = context.reloadEntity(pdfBitstream);
        txtBitstream = context.reloadEntity(txtBitstream);
        noMimeTypeBitstream = context.reloadEntity(noMimeTypeBitstream);

        assertThat(pdfBitstream.getMetadata(), hasItem(with("bitstream.viewer.provider", "pdf")));
        assertThat(txtBitstream.getMetadata(), not(hasItem(with("bitstream.viewer.provider", "pdf"))));
        assertThat(noMimeTypeBitstream.getMetadata(), not(hasItem(with("bitstream.viewer.provider", "iiif"))));
        assertThat(noMimeTypeBitstream.getMetadata(), not(hasItem(with("bitstream.viewer.provider", "pdf"))));
    }

    @Test
    public void testCreateItemWithoutBitstream()
            throws SQLException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        context.restoreAuthSystemState();
        context.commit();

        item = context.reloadEntity(item);

        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.METADATA_ONLY)));

    }

    @Test
    public void testCreateItemWithoutBitstreamWithMetadataUpdate()
            throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        context.restoreAuthSystemState();
        context.commit();

        item = context.reloadEntity(item);

        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.METADATA_ONLY)));

        context.turnOffAuthorisationSystem();
        MetadataValue dataciteRights = itemService.getMetadataByMetadataString(item, "datacite.rights").get(0);
        itemService.removeMetadataValues(context, item, List.of(dataciteRights));
        itemService.update(context, item);
        context.restoreAuthSystemState();
        context.commit();

        item = context.reloadEntity(item);

        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.METADATA_ONLY)));
    }

    @Test
    public void testCreateItemAndIndexingItem()
            throws SQLException, AuthorizeException, SearchServiceException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection)
                .withTitle("itemForTest")
                .build();
        context.restoreAuthSystemState();
        context.commit();

        item = context.reloadEntity(item);

        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.METADATA_ONLY)));

        context.turnOffAuthorisationSystem();
        MetadataValue dataciteRights = itemService.getMetadataByMetadataString(item, "datacite.rights").get(0);
        itemService.removeMetadataValues(context, item, List.of(dataciteRights));
        indexService.indexContent(context, new IndexableItem(item), true);
        indexService.commit();
        itemService.update(context, item);
        context.restoreAuthSystemState();
        context.commit();

        item = context.reloadEntity(item);

        assertThat(item.getMetadata(),
                hasItem(with("datacite.rights", PolicyMetadataUtils.METADATA_ONLY)));

        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setQuery("itemForTest");
        discoverQuery.setStart(0);
        discoverQuery.setMaxResults(1);
        discoverQuery.addFilterQueries("search.resourcetype:" + IndexableItem.TYPE);
        DiscoverResult discoverResult = searchService.search(context, discoverQuery);
        List<IndexableObject> indexableObjects = discoverResult.getIndexableObjects();

        assertEquals(indexableObjects.size(), 1);
        assertTrue( ((Item) indexableObjects.get(0).getIndexedObject()).getMetadata().stream()
                .filter(metadataValue -> metadataValue.getMetadataField().toString()
                        .equals("datacite_rights")).findFirst().get().getValue()
                .equals(PolicyMetadataUtils.METADATA_ONLY));
    }

    @Test
    public void testCreateItemWithOneBitstream()
            throws SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test"))
                .withMimeType("application/pdf")
                .build();

        String embargoDate = "2022-08-16";
        ResourcePolicyBuilder.createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream).withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM).withName("restricted")
            .withStartDate(dateFormat.parse(embargoDate)).build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", "restricted")));
        assertThat(item.getMetadata(), hasItem(with("datacite.rights", "restricted")));
    }

    @Test
    public void testCreateItemWithBitstreamsWithMainDocument()
            throws SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test"))
                .withMimeType("application/pdf")
                .build();
        Bitstream bitstream2 = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test2"))
                .withMimeType("application/pdf")
                .withMetadata("dc", "type", null, "main document")
                .build();

        String embargoDate = "2022-08-16";
        ResourcePolicyBuilder.createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream).withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM).withName("openaccess")
            .withStartDate(dateFormat.parse(embargoDate)).build();

        context.commit();
        ResourcePolicyBuilder.createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream2).withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM).withName("restricted")
            .withStartDate(dateFormat.parse(embargoDate)).build();
        context.commit();

        context.restoreAuthSystemState();
        bitstream = context.reloadEntity(bitstream);
        bitstream2 = context.reloadEntity(bitstream2);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", "openaccess")));
        assertThat(bitstream2.getMetadata(), hasItem(with("datacite.rights", "restricted")));
        assertThat(item.getMetadata(), hasItem(with("datacite.rights", "restricted")));
    }

    @Test
    public void testCreateItemWithBitstreamsWithFirstBitstreamAsJpeg()
            throws SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test"))
                .withMimeType("image/jpeg")
                .build();
        Bitstream bitstream2 = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test2"))
                .withMimeType("application/pdf")
                .build();
        Bitstream bitstream3 = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test3"))
                .withMimeType("application/pdf")
                .build();

        String embargoDate = "2022-08-16";
        ResourcePolicyBuilder.createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream).withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM).withName("openaccess")
            .withStartDate(dateFormat.parse(embargoDate)).build();
        context.commit();

        ResourcePolicyBuilder.createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream2).withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM).withName("restricted")
            .withStartDate(dateFormat.parse(embargoDate)).build();
        context.commit();

        ResourcePolicyBuilder.createResourcePolicy(context, admin, null)
            .withDspaceObject(bitstream3).withAction(Constants.READ)
            .withPolicyType(TYPE_CUSTOM).withName("openaccess")
            .withStartDate(dateFormat.parse(embargoDate)).build();
        context.commit();
        context.restoreAuthSystemState();

        bitstream = context.reloadEntity(bitstream);
        bitstream2 = context.reloadEntity(bitstream2);
        bitstream3 = context.reloadEntity(bitstream3);
        item = context.reloadEntity(item);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", "openaccess")));
        assertThat(bitstream2.getMetadata(), hasItem(with("datacite.rights", "restricted")));
        assertThat(bitstream3.getMetadata(), hasItem(with("datacite.rights", "openaccess")));
        assertThat(item.getMetadata(), hasItem(with("datacite.rights", "restricted")));


    }

    private Optional<String> getAccessionedDate(Item item) {
        Optional<String> accessionedDate =
            item.getMetadata().stream().filter(mv -> "dc_date_accessioned".equals(mv.getMetadataField().toString()))
                .findFirst()
                .map(MetadataValue::getValue);
        return accessionedDate;
    }

}
