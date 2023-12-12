/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks.virtualfields;

import static org.dspace.content.Item.ANY;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

public class VirtualFieldPublisherWithPlace implements VirtualField {

    @Autowired
    private ItemService itemService;

    private String publisherField = "dc.publisher";

    private String publisherPlaceField = "dc.publisher.place";

    @Override
    public String[] getMetadata(Context context, Item item, String fieldName) {

        String publisher = itemService.getMetadataFirstValue(item, new MetadataFieldName(publisherField), ANY);
        String publisherPlace = itemService.getMetadataFirstValue(item, new MetadataFieldName(publisherPlaceField),
            ANY);

        String value = Stream.of(publisherPlace, publisher)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.joining(", "));

        if (StringUtils.isNotBlank(value)) {
            return new String[] { value };
        } else {
            return new String[] {};
        }
    }

    public String getPublisherField() {
        return publisherField;
    }

    public void setPublisherField(String publisherField) {
        this.publisherField = publisherField;
    }

    public String getPublisherPlaceField() {
        return publisherPlaceField;
    }

    public void setPublisherPlaceField(String publisherPlaceField) {
        this.publisherPlaceField = publisherPlaceField;
    }

}
