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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportAuthorityReader implements ItemsImportMetadataFieldReader {

    private XPath xPath = XPathFactory.newInstance().newXPath();

    private String authorityPrefix;

    private String readerName;

    private String valueXPath;

    private String authorityXPath;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, NodeList nodeList) {
        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();
        for (int i = 0; i < nodeList.getLength(); i++) {

            Node node = nodeList.item(i);

            String value = getSingleValue(node, xPath, valueXPath);

            if (StringUtils.isNotBlank(value)) {
                String authority = getSingleValue(node, xPath, authorityXPath);
                metadataValues.add(buildMetadata(metadataField, value, authority));
            }

        }
        return metadataValues;
    }

    private MetadataValueDTO buildMetadata(String metadataField, String value, String authority) {
        MetadataValueDTO metadataValue = new MetadataValueDTO(metadataField, value);
        if (StringUtils.isNotBlank(authority)) {
            metadataValue.setAuthority(authorityPrefix + "::" + authority);
            metadataValue.setConfidence(600);
        }
        return metadataValue;
    }

    @Override
    public String getReaderName() {
        return readerName;
    }

    public void setReaderName(String readerName) {
        this.readerName = readerName;
    }

    public String getAuthorityPrefix() {
        return authorityPrefix;
    }

    public void setAuthorityPrefix(String authorityPrefix) {
        this.authorityPrefix = authorityPrefix;
    }

    public String getValueXPath() {
        return valueXPath;
    }

    public String getAuthorityXPath() {
        return authorityXPath;
    }

    public void setAuthorityXPath(String authorityXPath) {
        this.authorityXPath = authorityXPath;
    }

    public void setValueXPath(String valueXPath) {
        this.valueXPath = valueXPath;
    }

}
