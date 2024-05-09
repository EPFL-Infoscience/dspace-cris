/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks.virtualfields;

import java.util.Arrays;

import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * Implements virtual field processing for split metadata by '|'
 *
 * @author Aliaksei Bykau
 */
public class VirtualFieldMultiValueSplitter implements VirtualField {

    private final ItemService itemService;

    private String metadataToSplit;

    @Autowired
    public VirtualFieldMultiValueSplitter(ItemService itemService) {
        this.itemService = itemService;
    }

    public String[] getMetadata(Context context, Item item, String fieldName) {
        return itemService.getMetadataByMetadataString(item, metadataToSplit).stream()
                .map(MetadataValue::getValue).map(value ->  value.split("\\|"))
                .flatMap(Arrays::stream).toArray(String[]::new);
    }

    public void setMetadataToSplit(String metadataToSplit) {
        this.metadataToSplit = metadataToSplit;
    }
}