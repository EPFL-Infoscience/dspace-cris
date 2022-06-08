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
 * but this one isn't saved in the DB. This can freely be used to represent data without it being saved in the database,
 * this will typically be used when transferring data
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
public class DeduplicationSetMergeDTO {

    private String setId;

    private List<String> mergedItems;

    private List<String> bitstreams;

    private List<DeduplicationMetadataDTO> metadata;

    public DeduplicationSetMergeDTO() {
    }

    public DeduplicationSetMergeDTO(String setId, List<String> mergedItems, List<String> bitstreams,
                                    List<DeduplicationMetadataDTO> metadata) {
        this.setId = setId;
        this.mergedItems = mergedItems;
        this.bitstreams = bitstreams;
        this.metadata = metadata;
    }

    public String getSetId() {
        return setId;
    }

    public void setSetId(String setId) {
        this.setId = setId;
    }

    public List<String> getMergedItems() {
        return mergedItems;
    }

    public void setMergedItems(List<String> mergedItems) {
        this.mergedItems = mergedItems;
    }

    public List<String> getBitstreams() {
        return bitstreams;
    }

    public void setBitstreams(List<String> bitstreams) {
        this.bitstreams = bitstreams;
    }

    public List<DeduplicationMetadataDTO> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<DeduplicationMetadataDTO> metadata) {
        this.metadata = metadata;
    }
}
