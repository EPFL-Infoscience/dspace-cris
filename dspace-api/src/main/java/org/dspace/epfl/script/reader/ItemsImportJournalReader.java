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
import java.util.Optional;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang.StringUtils;
import org.dspace.content.authority.Choices;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportJournalReader implements ItemsImportMetadataFieldReader {

    @Autowired
    private ConfigurationService configurationService;

    private String isPartOfMetadataField;

    private XPath xPath = XPathFactory.newInstance().newXPath();

    private String issnNodeXpath;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {

        List<MetadataValueDTO> metadataValues = new ArrayList<>();

        Node issnNode = findHightParentNode(nodeList.item(0));

        Optional<String> issnValue = issnNode != null ? getIssnValue(issnNode) : Optional.empty();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            String value = node.getTextContent();
            if (StringUtils.isBlank(value)) {
                continue;
            }

            if (getJournalOrIsPartOfTypes().contains(type)) {
                if (issnValue.isPresent()) {
                    metadataValues.add(new MetadataValueDTO(metadataField, value,
                        configurationService.getProperty("epfl.issn.prefix") +
                            issnValue.get(), Choices.CF_UNSET));
                } else {
                    metadataValues.add(new MetadataValueDTO(metadataField, value));
                }
            } else {
                metadataValues.add(new MetadataValueDTO(isPartOfMetadataField, value));
            }
        }
        return metadataValues;
    }


    private Optional<String> getIssnValue(Node parentNode) {

        NodeList nodeList = getNodeList(parentNode, issnNodeXpath);

        Node item = nodeList.item(0);

        if (item == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(getSingleValue(item, xPath, "."));
    }

    private NodeList getNodeList(Object item, String expression) {
        try {
            return (NodeList) xPath.compile(expression).evaluate(item, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("An error occurs evaluating path " + expression, e);
        }
    }

    private Node findHightParentNode(Node node) {
        if (node != null) {
            Node parentNode = node.getParentNode();
            return parentNode != null ? parentNode.getParentNode() : null;
        }
        return null;
    }

    private List<String> getJournalOrIsPartOfTypes() {
        return Arrays.asList(configurationService.getArrayProperty("epfl.items-import.journal-or-is-part-of.types"));
    }

    @Override
    public String getReaderName() {
        return "journal";
    }

    public String getIsPartOfSeriesMetadataField() {
        return isPartOfMetadataField;
    }

    public void setIsPartOfSeriesMetadataField(String isPartOfSeriesMetadataField) {
        this.isPartOfMetadataField = isPartOfSeriesMetadataField;
    }

    public String getIsPartOfMetadataField() {
        return isPartOfMetadataField;
    }

    public void setIsPartOfMetadataField(String isPartOfMetadataField) {
        this.isPartOfMetadataField = isPartOfMetadataField;
    }

    public String getIssnNodeXpath() {
        return issnNodeXpath;
    }

    public void setIssnNodeXpath(String issnNodeXpath) {
        this.issnNodeXpath = issnNodeXpath;
    }
}