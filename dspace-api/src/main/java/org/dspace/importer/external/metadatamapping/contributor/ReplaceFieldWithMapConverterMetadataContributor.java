/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.metadatamapping.contributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.dspace.importer.external.metadatamapping.MetadataFieldConfig;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.dspace.util.SimpleMapConverter;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class ReplaceFieldWithMapConverterMetadataContributor extends SimpleJsonPathMetadataContributor {

    private static final String UNSPECIFIED = "Unspecified";

    private SimpleMapConverter simpleMapConverter;

    @Override
    public Collection<MetadatumDTO> contributeMetadata(String fullJson) {
        Collection<MetadatumDTO> metadata = new ArrayList<>();
        Collection<String> metadataValue = new ArrayList<>();
        if (Objects.nonNull(metadataProcessor)) {
            metadataValue = metadataProcessor.processMetadata(fullJson);
        } else {
            JsonNode jsonNode = convertStringJsonToJsonNode(fullJson);
            JsonNode node = jsonNode.at(query);
            if (!node.isNull() && StringUtils.isNotBlank(node.toString())) {
                String nodeValue = getStringValue(node);
                if (StringUtils.isNotBlank(nodeValue)) {
                    metadataValue.add(nodeValue);
                }
            }
        }
        metadataValue.forEach(v -> metadata.add(getMetadatum(field, v)));
        return metadata;
    }

    private MetadatumDTO getMetadatum(MetadataFieldConfig field, String value) {
        String convertedValue = simpleMapConverter.getValue(value);
        if (UNSPECIFIED.equals(convertedValue) || Objects.isNull(field)) {
            return null;
        }
        MetadatumDTO dcValue = new MetadatumDTO();
        dcValue.setValue(convertedValue);
        dcValue.setElement(field.getElement());
        dcValue.setQualifier(field.getQualifier());
        dcValue.setSchema(field.getSchema());
        return dcValue;
    }

    public void setSimpleMapConverter(SimpleMapConverter simpleMapConverter) {
        this.simpleMapConverter = simpleMapConverter;
    }

}