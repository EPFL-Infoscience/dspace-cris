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
import static org.hamcrest.Matchers.not;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.tools.ant.filters.StringInputStream;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.policy.OpenAirePolicyUtils.AccessRights;
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
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.core.Constants;
import org.junit.Before;
import org.junit.Test;

public class PolicyMetadataEnhancerConusmerIT extends AbstractIntegrationTestWithDatabase {

    private static final String TYPE_CUSTOM = "TYPE_CUSTOM";

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private Collection collection;

    private ResourcePolicyService resourcePolicyService = ContentServiceFactory.getInstance()
            .getResourcePolicyService();

    @Before
    public void setup() {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).withName("Parent Community").build();

        collection = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection 1").build();

        context.restoreAuthSystemState();
    }

    @Test
    public void testWithoutPolicyType()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder
                    .createBitstream(context, item, new StringInputStream("test"))
                    .build();

        ResourcePolicyBuilder.createResourcePolicy(context).withDspaceObject(bitstream).withAction(Constants.READ)
                .withUser(admin).build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", AccessRights.RESTRICTED.getName())));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", null))));
    }

    @Test
    public void testWithoutPolicyName()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test")).build();

        ResourcePolicyBuilder.createResourcePolicy(context).withDspaceObject(bitstream).withAction(Constants.READ)
                .withUser(admin).withPolicyType(TYPE_CUSTOM).build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", AccessRights.RESTRICTED.getName())));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", null))));
    }

    @Test
    public void testWithPolicyName()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test")).build();

        ResourcePolicyBuilder.createResourcePolicy(context).withDspaceObject(bitstream).withAction(Constants.READ)
                .withUser(admin).withPolicyType(TYPE_CUSTOM).withName(AccessRights.OPEN.getName()).build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", AccessRights.OPEN.getName())));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", null))));
    }

    @Test
    public void testWithPolicyNameEndDate()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test")).build();

        String embargoDate = "2022-08-16";
        ResourcePolicyBuilder.createResourcePolicy(context).withDspaceObject(bitstream).withAction(Constants.READ)
                .withUser(admin).withPolicyType(TYPE_CUSTOM).withName(AccessRights.EMBARGOED.getName())
                .withEndDate(dateFormat.parse(embargoDate)).build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", AccessRights.EMBARGOED.getName())));
        assertThat(bitstream.getMetadata(), hasItem(with("datacite.available", embargoDate)));
    }

    @Test
    public void testWithPolicyNameEndDateEdited()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test")).build();

        String embargoDate = "2022-08-16";
        ResourcePolicyBuilder
            .createResourcePolicy(context)
            .withDspaceObject(bitstream)
            .withAction(Constants.READ)
            .withUser(admin)
            .withPolicyType(TYPE_CUSTOM)
            .withName(AccessRights.EMBARGOED.getName())
            .withEndDate(dateFormat.parse(embargoDate))
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

        resourcePolicy.setRpName(AccessRights.RESTRICTED.getName());
        resourcePolicy.setEndDate(null);

        this.resourcePolicyService.update(context, resourcePolicy);

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", AccessRights.RESTRICTED.getName())));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", embargoDate))));
    }

    @Test
    public void testWithPolicyNameEndDateDeleted()
            throws FileNotFoundException, SQLException, AuthorizeException, IOException, ParseException {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).build();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item, new StringInputStream("test")).build();

        String embargoDate = "2022-08-16";
        ResourcePolicyBuilder
            .createResourcePolicy(context)
            .withDspaceObject(bitstream)
            .withAction(Constants.READ)
            .withUser(admin)
            .withPolicyType(TYPE_CUSTOM)
            .withName(AccessRights.EMBARGOED.getName())
            .withEndDate(dateFormat.parse(embargoDate))
            .build();

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);

        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", AccessRights.EMBARGOED.getName())));
        assertThat(bitstream.getMetadata(), hasItem(with("datacite.available", embargoDate)));

        List<ResourcePolicy> resourcePolicies = bitstream.getResourcePolicies();
        ResourcePolicy resourcePolicy = resourcePolicies
                .stream()
                .filter(rp -> TYPE_CUSTOM.equals(rp.getRpType()))
                .findFirst()
                .orElseThrow();

        context.turnOffAuthorisationSystem();

        resourcePolicies.remove(resourcePolicy);
        resourcePolicy.setRpName(AccessRights.RESTRICTED.getName());
        resourcePolicy.setEndDate(null);

        this.resourcePolicyService.delete(context, resourcePolicy);

        context.restoreAuthSystemState();
        context.commit();

        bitstream = context.reloadEntity(bitstream);

        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.rights", AccessRights.EMBARGOED.getName()))));
        assertThat(bitstream.getMetadata(), hasItem(with("datacite.rights", AccessRights.RESTRICTED.getName())));
        assertThat(bitstream.getMetadata(), not(hasItem(with("datacite.available", embargoDate))));
    }
}
