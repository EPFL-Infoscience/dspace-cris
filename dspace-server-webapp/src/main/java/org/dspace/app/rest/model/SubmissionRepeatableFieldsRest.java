/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Root rest object for the /api/config/submissionrepeatablefields endpoint
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.com)
 */
public class SubmissionRepeatableFieldsRest extends BaseObjectRest<UUID> {

    public static final String NAME = "submissionrepeatablefield";
    public static final String CATEGORY = "config";
    public static final String PLURAL = "submissionrepeatablefields";

    private String itemId;
    private List<String> repeatableFields;

    @JsonIgnore
    @Override
    public UUID getId() {
        return super.getId();
    }

    public String getCategory() {
        return CATEGORY;
    }

    public String getType() {
        return NAME;
    }

    public Class getController() {
        return SubmissionRepeatableFieldsRest.class;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public List<String> getRepeatableFields() {
        return repeatableFields;
    }

    public void setRepeatableFields(List<String> repeatableFields) {
        this.repeatableFields = repeatableFields;
    }
}
