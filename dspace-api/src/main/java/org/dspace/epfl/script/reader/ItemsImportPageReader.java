/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.reader;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportPageReader implements ItemsImportMetadataFieldReader {

    private String endPageMetadataField;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            String value = node.getTextContent();

            if (StringUtils.isBlank(value)) {
                continue;
            }

            String[] pages = value.split("-");
            metadataValues.add(new MetadataValueDTO(metadataField, pages[0]));

            if (pages.length > 1) {
                metadataValues.add(new MetadataValueDTO(endPageMetadataField, pages[1]));
            }

        }

        return metadataValues;
    }

    @Override
    public String getReaderName() {
        return "page";
    }

    public String getEndPageMetadataField() {
        return endPageMetadataField;
    }

    public void setEndPageMetadataField(String endPageMetadataField) {
        this.endPageMetadataField = endPageMetadataField;
    }


}
