/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks.csl;

import java.text.ParseException;

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.itemupdate.MetadataUtilities;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.util.SimpleMapConverter;
import org.springframework.beans.factory.annotation.Autowired;

public class MetadataAuthorityRule {

    @Autowired
    private ItemService itemService;
    private String metadatafield;
    private SimpleMapConverter mapConverter;
    private boolean useSplit;
    private String splitSeparator;
    private String chunkPosition;


    public String getValue(Item item) {
        MetadataValue metadataValue = getMetadataValue(item);
        if (metadataValue == null) {
            return null;
        }

        String value = getAuthorityValue(metadataValue);
        if (StringUtils.isBlank(value)) {
            return null;
        }

        return mapConverter.getValue(value);
    }

    private MetadataValue getMetadataValue(Item item) {
        String[] metadataSplitted;
        try {
            metadataSplitted = MetadataUtilities.parseCompoundForm(metadatafield);
        } catch (ParseException e) {
            throw new RuntimeException("Failed to parse metadata field: " + metadatafield, e);
        }
        String schema = metadataSplitted[0];
        String element = metadataSplitted[1];
        String qualifier = metadataSplitted.length == 3 ? metadataSplitted[2] : null;

        return itemService.getMetadata(item, schema, element, qualifier, Item.ANY)
                .stream().filter(mv -> StringUtils.isNotBlank(mv.getAuthority()))
                .findFirst()
                .orElse(null);
    }

    private String getAuthorityValue(MetadataValue metadataValue) {
        String authority = metadataValue.getAuthority();
        if (useSplit && StringUtils.isNotBlank(splitSeparator)) {
            String[] chunks = authority.split(splitSeparator);
            authority = chunks[Integer.parseInt(chunkPosition)];
        }
        return authority;
    }

    public String getMetadatafield() {
        return metadatafield;
    }

    public void setMetadatafield(String metadatafield) {
        this.metadatafield = metadatafield;
    }

    public SimpleMapConverter getMapConverter() {
        return mapConverter;
    }

    public void setMapConverter(SimpleMapConverter converterNameFile) {
        this.mapConverter = converterNameFile;
    }

    public boolean isUseSplit() {
        return useSplit;
    }

    public void setUseSplit(boolean useSplit) {
        this.useSplit = useSplit;
    }

    public String getSplitSeparator() {
        return splitSeparator;
    }

    public void setSplitSeparator(String splitSeparator) {
        this.splitSeparator = splitSeparator;
    }

    public String getChunkPosition() {
        return chunkPosition;
    }

    public void setChunkPosition(String chunkPosition) {
        this.chunkPosition = chunkPosition;
    }

}
