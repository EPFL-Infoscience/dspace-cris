/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Root rest object for the /api/config/submissionfields endpoint
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.com)
 */
public class SubmissionFieldsRest extends BaseObjectRest<UUID> {

    public static final String NAME = "submissionfield";
    public static final String CATEGORY = "config";
    public static final String PLURAL = "submissionfields";

    private String itemId;
    private List<String> repeatableFields;
    private Map<String, List<String>> nestedFields;

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
        return SubmissionFieldsRest.class;
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

    public Map<String, List<String>> getNestedFields() {
        return nestedFields;
    }

    public void setNestedFields(Map<String, List<String>> nestedFields) {
        this.nestedFields = nestedFields;
    }
}
