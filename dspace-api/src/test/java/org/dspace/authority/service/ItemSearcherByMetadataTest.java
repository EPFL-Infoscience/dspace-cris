/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authority.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dspace.AbstractUnitTest;
import org.dspace.authority.service.impl.ItemSearcherByMetadata;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.utils.DSpace;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Test class for {@link ItemSearcherByMetadata }
 * 
 * @author Daniele Ninfo (daniele.ninfo at 4science.com)
 *
 */
public class ItemSearcherByMetadataTest extends AbstractUnitTest {

    protected ItemSearcherByMetadata itemSearcherByORCID;

    @Mock
    private Item item;

    @Mock
    private ItemService itemService;

    @Before
    public void setUp() {
        itemSearcherByORCID = new DSpace().getServiceManager().getApplicationContext()
                .getBean("itemSearcherByORCID", ItemSearcherByMetadata.class);
    }

    /**
     * This test verifies that itemService.resolveReferences will not take in consideration
     * the placeholder PLACEHOLDER_PARENT_METADATA_VALUE, avoiding useless clauses towards solr
     */
    @Test
    public void testResolveReferencesFilterPlaceholderMetadataValue() {

        ItemService currentItemService = itemSearcherByORCID.getItemService();

        try {

            String orcid = "0000-0000-0000-0001";

            List<MetadataValue> metadataValues = new ArrayList<MetadataValue>();
            MetadataValue metadataValueOrcid = mock(MetadataValue.class);
            when(metadataValueOrcid.getValue()).thenReturn(orcid);
            metadataValues.add(metadataValueOrcid);

            for (int i = 0; i < 5; i++) {
                MetadataValue metadataValue = mock(MetadataValue.class);
                when(metadataValue.getValue()).thenReturn("#PLACEHOLDER_PARENT_METADATA_VALUE#");
                metadataValues.add(metadataValue);
            }
            when(itemService.getMetadataByMetadataString(item, "person.identifier.orcid")).thenReturn(metadataValues);
            when(itemService.findRelatedItemsByAuthorityControlledFields(eq(context), eq(item), anyList()))
                .thenReturn(Collections.emptyIterator());
            itemSearcherByORCID.setItemService(itemService);

            itemSearcherByORCID.resolveReferences(context, item);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);

            verify(itemService).findRelatedItemsByAuthorityControlledFields(eq(context), eq(item), captor.capture());
            List<String> capturedAuthorities = captor.getValue();
            assertEquals(1, capturedAuthorities.size());
            assertTrue(capturedAuthorities.contains("will be referenced::ORCID::" + orcid));

        } finally {
            itemSearcherByORCID.setItemService(currentItemService);
        }
    }

    /**
     * This test verifies that duplicated orcid values will be skipped, resulting in only one clause towards solr
     */
    @Test
    public void testResolveReferencesAvoidDuplicates() {

        ItemService currentItemService = itemSearcherByORCID.getItemService();

        try {

            String orcid = "0000-0000-0000-0001";

            List<MetadataValue> metadataValues = new ArrayList<MetadataValue>();
            for (int i = 0; i < 5; i++) {
                MetadataValue metadataValue = mock(MetadataValue.class);
                when(metadataValue.getValue()).thenReturn(orcid);
                metadataValues.add(metadataValue);
            }
            when(itemService.getMetadataByMetadataString(item, "person.identifier.orcid")).thenReturn(metadataValues);
            when(itemService.findRelatedItemsByAuthorityControlledFields(eq(context), eq(item), anyList()))
                .thenReturn(Collections.emptyIterator());
            itemSearcherByORCID.setItemService(itemService);

            itemSearcherByORCID.resolveReferences(context, item);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);

            verify(itemService).findRelatedItemsByAuthorityControlledFields(eq(context), eq(item), captor.capture());
            List<String> capturedAuthorities = captor.getValue();
            assertEquals(1, capturedAuthorities.size());
            assertTrue(capturedAuthorities.contains("will be referenced::ORCID::" + orcid));

        } finally {
            itemSearcherByORCID.setItemService(currentItemService);
        }
    }
}