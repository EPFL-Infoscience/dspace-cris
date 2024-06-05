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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang.StringUtils;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportSeriesReader implements ItemsImportMetadataFieldReader {

    @Autowired
    private ConfigurationService configurationService;

    private String isPartOfSeriesMetadataField;

    private XPath xPath = XPathFactory.newInstance().newXPath();

    private String titleXPath;

    private String numberXPath;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);

            String title = getSingleValue(node, xPath, titleXPath);
            String number = getSingleValue(node, xPath, numberXPath);

            String value = Stream.of(title, number)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("; "));

            if (StringUtils.isNotBlank(value)) {
                String field = metadataField;
                if (getIsPartOfSeriesTypes().contains(type)) {
                    field = isPartOfSeriesMetadataField;
                }
                metadataValues.add(new MetadataValueDTO(field, value));
            }

        }

        return metadataValues;
    }

    private List<String> getIsPartOfSeriesTypes() {
        return Arrays.asList(configurationService.getArrayProperty("epfl.items-import.is-part-of.series.types"));
    }

    @Override
    public String getReaderName() {
        return "serie";
    }

    public String getTitleXPath() {
        return titleXPath;
    }

    public void setTitleXPath(String titleXPath) {
        this.titleXPath = titleXPath;
    }

    public String getNumberXPath() {
        return numberXPath;
    }

    public void setNumberXPath(String numberXPath) {
        this.numberXPath = numberXPath;
    }

    public String getIsPartOfSeriesMetadataField() {
        return isPartOfSeriesMetadataField;
    }

    public void setIsPartOfSeriesMetadataField(String isPartOfSeriesMetadataField) {
        this.isPartOfSeriesMetadataField = isPartOfSeriesMetadataField;
    }

}
