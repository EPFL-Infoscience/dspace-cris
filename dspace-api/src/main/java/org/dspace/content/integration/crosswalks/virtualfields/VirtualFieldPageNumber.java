/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks.virtualfields;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implements virtual field processing for split pagenumber range information.
 *
 * @author bollini
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 */
public class VirtualFieldPageNumber implements VirtualField {

    private final ItemService itemService;

    @Autowired
    public VirtualFieldPageNumber(ItemService itemService) {
        this.itemService = itemService;
    }

    public String[] getMetadata(Context context, Item item, String fieldName) {
        List<MetadataValue> startPageList = itemService.getMetadataByMetadataString(item, "oaire.citation.startPage");

        if (CollectionUtils.isEmpty(startPageList)) {
            return new String[] {};
        }

        String startPage = startPageList.get(0).getValue();

        List<MetadataValue> endPageList = itemService.getMetadataByMetadataString(item, "oaire.citation.endPage");

        if (CollectionUtils.isEmpty(endPageList)) {
            return new String[] { startPage };
        }

        String endPage = endPageList.get(0).getValue();

        return new String[] { startPage + " - " + endPage };
    }
}