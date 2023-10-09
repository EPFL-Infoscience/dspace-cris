/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.reader;

import static org.apache.commons.lang3.StringUtils.startsWith;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportKeywordsReader implements ItemsImportMetadataFieldReader {

    private String alternativeMetadataField;

    private String prefix;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (int i = 0; i < nodeList.getLength(); i++) {

            Node node = nodeList.item(i);
            String value = node.getTextContent();

            String field = startsWith(value, prefix) ? alternativeMetadataField : metadataField;

            if (StringUtils.isNotBlank(value)) {
                metadataValues.add(new MetadataValueDTO(field, value.trim()));
            }

        }

        return metadataValues;

    }

    public String getAlternativeMetadataField() {
        return alternativeMetadataField;
    }

    public void setAlternativeMetadataField(String alternativeMetadataField) {
        this.alternativeMetadataField = alternativeMetadataField;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getReaderName() {
        return "keywords";
    }

}
