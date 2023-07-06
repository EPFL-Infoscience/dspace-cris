/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.reader;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportIdentifierReader implements ItemsImportMetadataFieldReader {

    private XPath xPath = XPathFactory.newInstance().newXPath();

    private String qualifierXPath;

    private String valueXPath;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, NodeList nodeList) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (int i = 0; i < nodeList.getLength(); i++) {

            Node node = nodeList.item(i);

            String value = getSingleValue(node, xPath, valueXPath);

            String qualifier = getSingleValue(node, xPath, qualifierXPath);
            String identifierField = isNotBlank(qualifier) ? metadataField + "." + qualifier : metadataField;

            metadataValues.add(new MetadataValueDTO(identifierField, value));

        }

        return metadataValues;

    }

    @Override
    public String getReaderName() {
        return "identifier";
    }

    public String getQualifierXPath() {
        return qualifierXPath;
    }

    public void setQualifierXPath(String qualifierXPath) {
        this.qualifierXPath = qualifierXPath;
    }

    public String getValueXPath() {
        return valueXPath;
    }

    public void setValueXPath(String valueXPath) {
        this.valueXPath = valueXPath;
    }

}
