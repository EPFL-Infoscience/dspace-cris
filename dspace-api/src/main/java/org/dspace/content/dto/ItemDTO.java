/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dto;

import java.util.List;
import java.util.stream.Collectors;

public class ItemDTO {

    private final String id;

    private final boolean discoverable;

    private final List<MetadataValueDTO> metadataValues;

    private final List<BitstreamDTO> bitstreams;

    public ItemDTO(String id, List<MetadataValueDTO> metadataValues) {
        this(id, true, metadataValues, List.of());
    }

    public ItemDTO(String id, boolean discoverable, List<MetadataValueDTO> metadataValues,
        List<BitstreamDTO> bitstreams) {
        this.id = id;
        this.discoverable = discoverable;
        this.metadataValues = metadataValues;
        this.bitstreams = bitstreams;
    }

    public List<MetadataValueDTO> getMetadataValues(String metadataField) {
        return metadataValues.stream()
            .filter(metadataValue -> metadataValue.getMetadataField().equals(metadataField))
            .collect(Collectors.toList());
    }

    public String getId() {
        return id;
    }

    public boolean isDiscoverable() {
        return discoverable;
    }

    public List<MetadataValueDTO> getMetadataValues() {
        return metadataValues;
    }

    public List<BitstreamDTO> getBitstreams() {
        return bitstreams;
    }

}
