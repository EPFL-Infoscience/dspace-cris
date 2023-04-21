/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.bulkimport.model;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.vo.MetadataValueVO;

public class MetadataGroup implements ChildRow {

    private final String parentId;

    private final String name;

    private final Map<String, MetadataValueVO> metadata;

    private final String validationError;

    public MetadataGroup(String parentId, String name, String validationError) {
        super();
        this.metadata = new HashMap<>();
        this.parentId = parentId;
        this.name = name;
        this.validationError = validationError;
    }

    public MetadataGroup(String parentId, String name, Map<String, MetadataValueVO> metadata) {
        super();
        this.metadata = metadata;
        this.parentId = parentId;
        this.name = name;
        this.validationError = null;
    }

    public Map<String, MetadataValueVO> getMetadata() {
        return metadata;
    }

    public ListValuedMap<String, MetadataValueVO> getMetadataAsMultiValuedMap() {
        return new ArrayListValuedHashMap<String, MetadataValueVO>(metadata);
    }

    public String getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public boolean isNotValid() {
        return StringUtils.isNotEmpty(validationError);
    }

    public String getValidationError() {
        return validationError;
    }

}
