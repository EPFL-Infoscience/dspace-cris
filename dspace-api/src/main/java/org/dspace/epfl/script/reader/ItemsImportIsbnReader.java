/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.reader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportIsbnReader implements ItemsImportMetadataFieldReader {

    @Autowired
    private ConfigurationService configurationService;

    private String containerMetadataField;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            String value = node.getTextContent();
            String field = isContainerType(type) ? containerMetadataField : metadataField;
            if (StringUtils.isNotBlank(value)) {
                metadataValues.add(new MetadataValueDTO(field, value));
            }
        }

        return metadataValues;
    }

    private boolean isContainerType(String type) {
        return getContainerTypes().contains(type);
    }

    private List<String> getContainerTypes() {
        return Arrays.asList(configurationService.getArrayProperty("epfl.items-import.container-types"));
    }

    @Override
    public String getReaderName() {
        return "isbn";
    }

    public String getContainerMetadataField() {
        return containerMetadataField;
    }

    public void setContainerMetadataField(String containerMetadataField) {
        this.containerMetadataField = containerMetadataField;
    }


}
