/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dspace.app.rest.DeduplicationSetMergeRestController;

/**
 * The DeduplicationMergeTarget REST Resource
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
public class DeduplicationMergeTargetRest extends BaseObjectRest<String> {
    public static final String CATEGORY = "deduplications";
    public static final String NAME = "merge";
    public static final String PLURAL_NAME = "merge";

    private List<String> allowedTargets;

    @Override
    @JsonIgnore
    public String getId() {
        return super.getId();
    }

    @Override
    @JsonIgnore
    public String getType() {
        return NAME;
    }

    @Override
    @JsonIgnore
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    @JsonIgnore
    public Class getController() {
        return DeduplicationSetMergeRestController.class;
    }

    public List<String> getAllowedTargets() {
        return allowedTargets;
    }

    public void setAllowedTargets(List<String> allowedTargets) {
        this.allowedTargets = allowedTargets;
    }
}
