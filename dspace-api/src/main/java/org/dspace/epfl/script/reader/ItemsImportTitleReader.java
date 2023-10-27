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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportTitleReader implements ItemsImportMetadataFieldReader {

    private XPath xPath = XPathFactory.newInstance().newXPath();

    private String titleXPath;

    private String subTitleXPath;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {
        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();
        for (int i = 0; i < nodeList.getLength(); i++) {

            Node node = nodeList.item(i);

            String title = getSingleValue(node, xPath, titleXPath);
            String subTitle = getSingleValue(node, xPath, subTitleXPath);

            String metadataValue = Stream.of(title, subTitle)
                .filter(value -> StringUtils.isNotBlank(value))
                .collect(Collectors.joining(": "));

            if (StringUtils.isNotBlank(metadataValue)) {
                metadataValues.add(new MetadataValueDTO(metadataField, metadataValue));
            }

        }
        return metadataValues;
    }

    @Override
    public String getReaderName() {
        return "title";
    }

    public String getTitleXPath() {
        return titleXPath;
    }

    public void setTitleXPath(String titleXPath) {
        this.titleXPath = titleXPath;
    }

    public String getSubTitleXPath() {
        return subTitleXPath;
    }

    public void setSubTitleXPath(String subTitleXPath) {
        this.subTitleXPath = subTitleXPath;
    }


}
