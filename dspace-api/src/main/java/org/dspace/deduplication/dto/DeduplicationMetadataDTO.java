/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.deduplication.dto;

import java.util.List;

/**
 * This class acts as Data transfer object in which we can store data,
 * this will be used when transferring data
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
public class DeduplicationMetadataDTO {

    private String metadataField;
    private List<DeduplicationMetadataSourcesDTO> sources;

    public DeduplicationMetadataDTO() {
    }

    public DeduplicationMetadataDTO(String metadataField, List<DeduplicationMetadataSourcesDTO> sources) {
        this.metadataField = metadataField;
        this.sources = sources;
    }

    public String getMetadataField() {
        return metadataField;
    }

    public void setMetadataField(String metadataField) {
        this.metadataField = metadataField;
    }

    public List<DeduplicationMetadataSourcesDTO> getSources() {
        return sources;
    }

    public void setSources(List<DeduplicationMetadataSourcesDTO> sources) {
        this.sources = sources;
    }
}
