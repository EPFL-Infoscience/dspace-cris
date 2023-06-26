/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.integration.crosswalks.virtualfields;

import static org.dspace.builder.CollectionBuilder.createCollection;
import static org.dspace.builder.CommunityBuilder.createCommunity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.utils.DSpace;
import org.junit.Before;
import org.junit.Test;

public class VirtualFieldConfigPropertiesIT extends AbstractIntegrationTestWithDatabase {

    private VirtualFieldConfigProperties virtualField;
    private Collection collection;

    @Before
    public void setup() {

        virtualField = new DSpace().getServiceManager().getServiceByName("virtualFieldConfigProps",
                                                                         VirtualFieldConfigProperties.class);

        context.setCurrentUser(admin);
        parentCommunity = createCommunity(context).build();
        collection = createCollection(context, parentCommunity).build();
    }

    @Test
    public void configurationFound() {
        Item item = ItemBuilder.createItem(context, collection).build();

        String[] metadata = virtualField.getMetadata(context, item, "virtual.config.dspace-server-url");

        assertThat(metadata.length, is(1));
        assertThat(metadata[0], is("http://localhost"));
    }

    @Test
    public void configurationNotFound() {
        Item item = ItemBuilder.createItem(context, collection).build();

        String[] metadata = virtualField.getMetadata(context, item, "virtual.config.foo-bar");

        assertThat(metadata.length, is(0));
    }
}
