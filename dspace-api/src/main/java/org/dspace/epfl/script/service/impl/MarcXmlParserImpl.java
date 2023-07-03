/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service.impl;

import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.dspace.epfl.script.model.ItemsImportMapping;
import org.dspace.epfl.script.model.ItemsImportMapping.MetadataField;
import org.dspace.epfl.script.reader.ItemsImportMetadataFieldReader;
import org.dspace.epfl.script.service.MarcXmlParser;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MarcXmlParserImpl implements MarcXmlParser {

    @Autowired
    private ConfigurationService configurationService;

    private DocumentBuilder documentBuilder;

    private Map<String, ItemsImportMetadataFieldReader> readers;

    private ItemsImportMapping mapping;

    private XPath xPath;

    @PostConstruct
    private void setup() {

        try {
            this.documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }

        readers = new DSpace().getServiceManager().getServicesByType(ItemsImportMetadataFieldReader.class)
            .stream().collect(toMap(ItemsImportMetadataFieldReader::getReaderName, Function.identity()));

        mapping = parseMapping();

        xPath = XPathFactory.newInstance().newXPath();

    }

    @Override
    public List<MetadataValueDTO> parse(Context context, InputStream source) {

        try {

            Document document = documentBuilder.parse(source);

            Node record = getNode(document, mapping.getItemXPath());

            return getMetadataValues(context, record);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private List<MetadataValueDTO> getMetadataValues(Context context, Node node) throws Exception {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (ItemsImportMapping.MetadataField metadataField : mapping.getMetadataFields().getMetadataFields()) {

            ItemsImportMetadataFieldReader reader = readers.get(metadataField.getReader());

            NodeList nodeList = getNodeList(node, metadataField.getXPath());

            List<MetadataValueDTO> values = reader.readValues(context, metadataField.getField(), nodeList);

            metadataValues.addAll(values);
        }

        return metadataValues;

    }

    private NodeList getNodeList(Object item, String expression) throws XPathExpressionException {
        return (NodeList) xPath.compile(expression).evaluate(item, XPathConstants.NODESET);
    }

    private Node getNode(Object item, String expression) throws XPathExpressionException {
        return (Node) xPath.compile(expression).evaluate(item, XPathConstants.NODE);
    }

    private ItemsImportMapping parseMapping() {

        String config = configurationService
            .getProperty("epfl.items-import.mapping-configuration.path");

        if (StringUtils.isBlank(config)) {
            throw new IllegalArgumentException("No import mapping configuration defined");
        }

        if (!new File(config).exists()) {
            throw new IllegalStateException("No mapping file present for the import configuration");
        }

        ItemsImportMapping importMapping = readMappingConfiguration(config);
        validateMapping(importMapping);
        return importMapping;

    }

    private ItemsImportMapping readMappingConfiguration(String config) {
        try (FileReader mappingReader = new FileReader(config)) {
            JAXBContext jaxbContext = JAXBContext.newInstance(ItemsImportMapping.class);
            return (ItemsImportMapping) jaxbContext.createUnmarshaller().unmarshal(mappingReader);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void validateMapping(ItemsImportMapping importMapping) {
        List<String> unknownReaders = importMapping.getMetadataFields().getMetadataFields().stream()
            .map(MetadataField::getReader)
            .filter(reader -> !readers.containsKey(reader))
            .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(unknownReaders)) {
            throw new IllegalStateException("The following configured readers are not defined: " + unknownReaders);
        }
    }

}
