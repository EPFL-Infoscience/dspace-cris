/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.deduplication.dto;

public class DeduplicationItemsDTO {

    private String uri;
    private boolean target;

    public DeduplicationItemsDTO() {
    }

    public DeduplicationItemsDTO(String uri, boolean target) {
        this.uri = uri;
        this.target = target;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public boolean isTarget() {
        return target;
    }

    public void setTarget(boolean target) {
        this.target = target;
    }
}
