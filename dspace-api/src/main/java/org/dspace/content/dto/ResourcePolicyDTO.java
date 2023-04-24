/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dto;

import java.util.Date;

import org.dspace.authorize.ResourcePolicy;

public class ResourcePolicyDTO {

    private String name;

    private String description;

    private Date startDate;

    private Date endDate;

    public ResourcePolicyDTO(ResourcePolicy resourcePolicy) {
        this(resourcePolicy.getRpName(), resourcePolicy.getRpDescription(),
            resourcePolicy.getStartDate(), resourcePolicy.getEndDate());
    }

    public ResourcePolicyDTO(String name, String description, Date startDate, Date endDate) {
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getName() {
        return name;
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public String getDescription() {
        return description;
    }

}
