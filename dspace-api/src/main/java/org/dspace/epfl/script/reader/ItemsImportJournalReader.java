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

public class ItemsImportJournalReader implements ItemsImportMetadataFieldReader {

    @Autowired
    private ConfigurationService configurationService;

    private String isPartOfSeriesMetadataField;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            String value = node.getTextContent();
            if (StringUtils.isBlank(value)) {
                continue;
            }

            if (getJournalOrIsPartOfTypes().contains(type)) {
                metadataValues.add(new MetadataValueDTO(metadataField, value));
            } else {
                metadataValues.add(new MetadataValueDTO(isPartOfSeriesMetadataField, value));
            }

        }

        return metadataValues;
    }

    private List<String> getJournalOrIsPartOfTypes() {
        return Arrays.asList(configurationService.getArrayProperty("epfl.items-import.journal-or-is-part-of.types"));
    }

    @Override
    public String getReaderName() {
        return "journal";
    }

    public String getIsPartOfSeriesMetadataField() {
        return isPartOfSeriesMetadataField;
    }

    public void setIsPartOfSeriesMetadataField(String isPartOfSeriesMetadataField) {
        this.isPartOfSeriesMetadataField = isPartOfSeriesMetadataField;
    }


}
