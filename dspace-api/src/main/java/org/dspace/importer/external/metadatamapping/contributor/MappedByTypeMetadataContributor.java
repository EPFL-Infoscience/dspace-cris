/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.importer.external.metadatamapping.contributor;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.dspace.importer.external.metadatamapping.MetadataFieldConfig;
import org.dspace.importer.external.metadatamapping.MetadataFieldMapping;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.dspace.importer.external.service.components.dto.PlainMetadataKeyValueItem;
import org.dspace.importer.external.service.components.dto.PlainMetadataSourceDto;

/**
 * Metadata contributor that takes an PlainMetadataSourceDto instance and turns it into a
 * collection of metadata, having returned value converted according to a mapping
 * passed to class instance
 *
 * @author Aliaksei Bykau
 */
public class MappedByTypeMetadataContributor implements MetadataContributor<PlainMetadataSourceDto> {
    private static final String BIB_TEXT_TYPE_METADATA = "type";
    private static final String DEFAULT_TYPE = "default";

    private Map<String, MetadataFieldConfig> fieldMap;

    private String key;

    private MetadataFieldMapping<PlainMetadataSourceDto,
            MetadataContributor<PlainMetadataSourceDto>> metadataFieldMapping;

    public MappedByTypeMetadataContributor(Map<String, MetadataFieldConfig> fieldMap, String key) {
        this.fieldMap = fieldMap;
        this.key = key;
    }

    public MappedByTypeMetadataContributor() { }

    /**
     * Set the metadataFieldMapping of this SimpleMetadataContributor
     *
     * @param metadataFieldMapping the new mapping.
     */
    @Override
    public void setMetadataFieldMapping(
            MetadataFieldMapping<PlainMetadataSourceDto,
                    MetadataContributor<PlainMetadataSourceDto>> metadataFieldMapping) {
        this.metadataFieldMapping = metadataFieldMapping;
    }

    @Override
    public Collection<MetadatumDTO> contributeMetadata(PlainMetadataSourceDto t) {
        List<MetadatumDTO> values = new LinkedList<>();
        for (PlainMetadataKeyValueItem metadatum : t.getMetadata()) {
            if (key.equals(metadatum.getKey())) {
                MetadataFieldConfig field = fieldMap.get(DEFAULT_TYPE);

                for (PlainMetadataKeyValueItem metadata : t.getMetadata()) {
                    if (BIB_TEXT_TYPE_METADATA.equals(metadata.getKey())) {
                        field = fieldMap.get(metadata.getValue());
                    }
                }

                MetadatumDTO dcValue = new MetadatumDTO();
                dcValue.setValue(metadatum.getValue());
                dcValue.setElement(field.getElement());
                dcValue.setQualifier(field.getQualifier());
                dcValue.setSchema(field.getSchema());
                values.add(dcValue);
            }
        }
        return values;
    }

    /**
     * Method to inject field map item
     *
     * @param fieldMap the {@link Map<String, MetadataFieldConfig>} to use in this contributor
     */
    public void setFieldMap(Map<String, MetadataFieldConfig> fieldMap) {
        this.fieldMap = fieldMap;
    }

    /**
     * Method to inject key value
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Method to retrieve field item
     */
    public String getKey() {
        return key;
    }

    /**
     * Method to retrieve the {@link Map<String, MetadataFieldConfig>} used in this contributor
     */
    public Map<String, MetadataFieldConfig> getFieldMap() {
        return fieldMap;
    }
}
