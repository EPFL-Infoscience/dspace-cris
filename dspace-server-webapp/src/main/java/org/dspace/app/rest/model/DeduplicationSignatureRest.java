/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import org.dspace.app.rest.RestResourceController;

/**
 * The DeduplicationSignature REST Resource
 *
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */
public class DeduplicationSignatureRest extends BaseObjectRest<String> {
    public static final String CATEGORY = "deduplications";
    public static final String NAME = "signature";

    private String id;

    private String signatureType;

    private int groupReviewerCheck;

    private int groupSubmitterCheck;

    private int groupAdminstratorCheck;

    @Override
    @JsonProperty(access = Access.READ_ONLY)
    public String getType() {
        return NAME;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Class getController() {
        return RestResourceController.class;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSignatureType() {
        return signatureType;
    }

    public void setSignatureType(String signatureType) {
        this.signatureType = signatureType;
    }

    public Integer getGroupReviewerCheck() {
        return groupReviewerCheck;
    }

    public void setGroupReviewerCheck(Integer groupReviewerCheck) {
        this.groupReviewerCheck = groupReviewerCheck;
    }

    public Integer getGroupSubmitterCheck() {
        return groupSubmitterCheck;
    }

    public void setGroupSubmitterCheck(Integer groupSubmitterCheck) {
        this.groupSubmitterCheck = groupSubmitterCheck;
    }

    public Integer getGroupAdminstratorCheck() {
        return groupAdminstratorCheck;
    }

    public void setGroupAdminstratorCheck(Integer groupAdminstratorCheck) {
        this.groupAdminstratorCheck = groupAdminstratorCheck;
    }
}
