/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.bulkimport.model;

import java.util.List;

/*
 * @author Jurgen Mamani
 */
public class UploadDetails {

    private final String parentId;

    private final String filePath;

    private final String bundleName;

    private final MetadataGroup metadataGroup;

    private final String bitstreamId;

    private final List<AccessCondition> accessConditions;

    private final boolean additionalAccessCondition;

    public UploadDetails(String parentId, String filePath, String bundleName,
                         String bitstreamId, List<AccessCondition> accessConditions,
                         boolean additionalAccessCondition, MetadataGroup metadataGroup) {
        this.parentId = parentId;
        this.filePath = filePath;
        this.bundleName = bundleName;
        this.metadataGroup = metadataGroup;
        this.bitstreamId = bitstreamId;
        this.accessConditions = accessConditions;
        this.additionalAccessCondition = additionalAccessCondition;
    }

    public String getParentId() {
        return parentId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getBundleName() {
        return bundleName;
    }

    public MetadataGroup getMetadataGroup() {
        return metadataGroup;
    }

    public String getBitstreamId() {
        return bitstreamId;
    }

    public List<AccessCondition> getAccessConditions() {
        return accessConditions;
    }

    public boolean getAdditionalAccessCondition() {
        return additionalAccessCondition;
    }
}
