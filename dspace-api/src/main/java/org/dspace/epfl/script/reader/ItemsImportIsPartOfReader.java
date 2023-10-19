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

public class ItemsImportIsPartOfReader implements ItemsImportMetadataFieldReader {

    @Autowired
    private ConfigurationService configurationService;

    private String isPartOfSeriesMetadataField;

    private String journalMetadataField;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            String value = node.getTextContent();
            if (StringUtils.isBlank(value)) {
                continue;
            }

            metadataValues.add(new MetadataValueDTO(metadataField, value));

            if (isContainerType("series", type)) {
                metadataValues.add(new MetadataValueDTO(isPartOfSeriesMetadataField, value));
            }

            if (isContainerType("journal", type)) {
                metadataValues.add(new MetadataValueDTO(journalMetadataField, value));
            }

        }

        return metadataValues;
    }

    private boolean isContainerType(String field, String type) {
        return getContainerTypes(field).contains(type);
    }

    private List<String> getContainerTypes(String field) {
        return Arrays.asList(configurationService.getArrayProperty("epfl.items-import.is-part-of." + field + ".types"));
    }

    @Override
    public String getReaderName() {
        return "isPartOf";
    }

    public String getJournalMetadataField() {
        return journalMetadataField;
    }

    public void setJournalMetadataField(String journalMetadataField) {
        this.journalMetadataField = journalMetadataField;
    }

    public String getIsPartOfSeriesMetadataField() {
        return isPartOfSeriesMetadataField;
    }

    public void setIsPartOfSeriesMetadataField(String isPartOfSeriesMetadataField) {
        this.isPartOfSeriesMetadataField = isPartOfSeriesMetadataField;
    }


}
