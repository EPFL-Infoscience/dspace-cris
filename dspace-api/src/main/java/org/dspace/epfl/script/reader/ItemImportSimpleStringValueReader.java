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

/**
 * Implementation of {@link ItemsImportMetadataFieldReader} that threats the value 
 * of the metadata field as a simple String object
 * 
 * @author Aliaksei Bykau  
 *
 */
public class ItemImportSimpleStringValueReader implements ItemsImportMetadataFieldReader {

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            String value = node.getTextContent();
            if (StringUtils.isNotBlank(value)) {
                metadataValues.add(new MetadataValueDTO(metadataField, value));
            }
        }

        return metadataValues;
    }

    @Override
    public String getReaderName() {
        return "simpleStringValue";
    }

}
