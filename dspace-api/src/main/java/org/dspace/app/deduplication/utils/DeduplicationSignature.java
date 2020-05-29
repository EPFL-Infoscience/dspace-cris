package org.dspace.app.deduplication.utils;

public class DeduplicationSignature {
    private String id;

    private String signatureType;

    private int groupReviewerCheck;

    private int groupSubmitterCheck;

    private int groupAdminstratorCheck;

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
