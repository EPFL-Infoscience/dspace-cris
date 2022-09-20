/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model.wrapper;

import java.util.List;

/**
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
public class SubmissionRepeatableFields {

    private String itemId;
    private List<String> repeatableFields;

    public SubmissionRepeatableFields(String itemId, List<String> repeatableFields) {
        this.itemId = itemId;
        this.repeatableFields = repeatableFields;
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
