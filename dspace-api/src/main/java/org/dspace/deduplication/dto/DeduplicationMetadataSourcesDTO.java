/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.deduplication.dto;

/**
 * This class acts as Data transfer object in which we can store data,
 * this will be used when transferring data
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
public class DeduplicationMetadataSourcesDTO {

    private String item;
    private int position;

    public DeduplicationMetadataSourcesDTO() {
    }

    public DeduplicationMetadataSourcesDTO(String item, int position) {
        this.item = item;
        this.position = position;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
