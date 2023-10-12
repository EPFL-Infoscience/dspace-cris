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
import java.util.Optional;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportBitstreamRelationReader implements ItemsImportMetadataFieldReader {

    private XPath xPath = XPathFactory.newInstance().newXPath();

    private String relationNodeXPath;

    private String relationValueXPath;

    private String bitstreamXPath;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        Node bitstreamNode = nodeList.item(0);

        if (bitstreamNode == null) {
            return metadataValues;
        }

        Node parentNode = bitstreamNode.getParentNode();

        getBitstreamIndex(parentNode, bitstreamNode)
            .flatMap(index -> getRelation(parentNode, index))
            .ifPresent(value -> metadataValues.add(new MetadataValueDTO(metadataField, value)));

        return metadataValues;
    }

    private Optional<Integer> getBitstreamIndex(Node parentNode, Node bitstreamNode) {

        NodeList bitstreams = getNodeList(parentNode, bitstreamXPath);

        for (int i = 0; i < bitstreams.getLength(); i++) {
            if (bitstreams.item(i).equals(bitstreamNode)) {
                return Optional.of(i);
            }
        }

        return Optional.empty();
    }

    private Optional<String> getRelation(Node parentNode, int i) {

        NodeList nodeList = getNodeList(parentNode, relationNodeXPath);

        Node item = nodeList.item(i);

        if (item == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(getSingleValue(item, xPath, relationValueXPath));
    }

    private NodeList getNodeList(Object item, String expression) {
        try {
            return (NodeList) xPath.compile(expression).evaluate(item, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("An error occurs evaluating path " + expression, e);
        }
    }

    @Override
    public String getReaderName() {
        return "bitstreamRelation";
    }

    public String getRelationNodeXPath() {
        return relationNodeXPath;
    }

    public void setRelationNodeXPath(String relationNodeXPath) {
        this.relationNodeXPath = relationNodeXPath;
    }

    public String getRelationValueXPath() {
        return relationValueXPath;
    }

    public void setRelationValueXPath(String relationValueXPath) {
        this.relationValueXPath = relationValueXPath;
    }

    public String getBitstreamXPath() {
        return bitstreamXPath;
    }

    public void setBitstreamXPath(String bitstreamXPath) {
        this.bitstreamXPath = bitstreamXPath;
    }

}
