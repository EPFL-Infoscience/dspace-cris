/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model.wrapper;

import java.util.List;
import java.util.Map;

/**
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
public class SubmissionFields {

    private String itemId;
    private List<String> repeatableFields;
    private Map<String, List<String>> nestedFields;

    public SubmissionFields(String itemId,
                            List<String> repeatableFields,
                            Map<String, List<String>> nestedFields) {
        this.itemId = itemId;
        this.repeatableFields = repeatableFields;
        this.nestedFields = nestedFields;
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
