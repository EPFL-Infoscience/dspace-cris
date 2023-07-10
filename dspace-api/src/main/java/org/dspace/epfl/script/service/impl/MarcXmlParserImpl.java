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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.dto.BitstreamDTO;
import org.dspace.content.dto.ItemDTO;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.dspace.epfl.script.model.ItemsImportMapping;
import org.dspace.epfl.script.model.ItemsImportMapping.Bitstreams;
import org.dspace.epfl.script.model.ItemsImportMapping.MetadataField;
import org.dspace.epfl.script.reader.ItemsImportMetadataFieldReader;
import org.dspace.epfl.script.service.MarcXmlParser;
import org.dspace.utils.DSpace;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class MarcXmlParserImpl implements MarcXmlParser {

    private DocumentBuilder documentBuilder;

    private Map<String, ItemsImportMetadataFieldReader> readers;

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

        xPath = XPathFactory.newInstance().newXPath();

    }

    @Override
    public ItemsImportMapping parseMapping(String configuration) {

        if (StringUtils.isBlank(configuration)) {
            throw new IllegalArgumentException("No import mapping configuration defined");
        }

        if (!new File(configuration).exists()) {
            throw new IllegalStateException("No mapping file present for the import configuration");
        }

        ItemsImportMapping importMapping = readMappingConfiguration(configuration);
        validateMapping(importMapping);

        return importMapping;

    }

    @Override
    public Node parse(InputStream source, ItemsImportMapping mapping) {
        try {
            Document document = documentBuilder.parse(source);
            return getNode(document, mapping.getItemXPath());
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ItemDTO readSingleItem(Context context, String id, InputStream source, ItemsImportMapping mapping) {

        Node record = parse(source, mapping);

        printDocument(record, System.out);

        List<MetadataValueDTO> metadataValues = readItemMetadataValues(context, record, mapping);

        List<BitstreamDTO> bitstreams = readBitstreams(context, record, mapping);

        String submitter = readSubmitter(context, record, mapping);

        return new ItemDTO("LEGACY-ID::" + id, submitter, metadataValues, bitstreams);
    }

    @Override
    public List<List<MetadataValueDTO>> readItems (Context context, InputStream source,
                                                   ItemsImportMapping mapping, String expression) {
        try {
            Document document = documentBuilder.parse(source);
            NodeList nodeList = getNodeList(document, expression);
            List<List<MetadataValueDTO>> records = new ArrayList<List<MetadataValueDTO>>();
            for (int i = 0; i < nodeList.getLength(); i++) {
                records.add(readItemMetadataValues(context,nodeList.item(i),mapping));
            }
            return records;
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<MetadataValueDTO> readItemMetadataValues(Context context, InputStream source,
        ItemsImportMapping mapping) {

        try {
            Node record = parse(source, mapping);
            return readItemMetadataValues(context, record, mapping);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public List<MetadataValueDTO> readItemMetadataValues(Context context, Node record, ItemsImportMapping mapping) {
        return readMetadataValues(context, record, mapping.getMetadataFields().getMetadataFields());
    }

    @Override
    public List<BitstreamDTO> readBitstreams(Context context, Node record, ItemsImportMapping mapping) {

        String bitstreamXPath = mapping.getBitstreams().getBitstreamXPath();
        if (StringUtils.isBlank(bitstreamXPath)) {
            return List.of();
        }

        NodeList bitstreamNodeList = getNodeList(record, bitstreamXPath);

        List<BitstreamDTO> bitstreams = new ArrayList<BitstreamDTO>();

        for (int i = 0; i < bitstreamNodeList.getLength(); i++) {
            Node bitstreamNode = bitstreamNodeList.item(i);
            bitstreams.add(readBitstream(context, bitstreamNode, mapping));
        }

        return bitstreams;
    }

    @Override
    public String readSubmitter(Context context, Node record, ItemsImportMapping mapping) {
        return Optional.ofNullable(mapping.getSubmitterXPath())
            .filter(StringUtils::isNotBlank)
            .map(path -> getSingleValue(record, path))
            .filter(StringUtils::isNotBlank)
            .orElse(null);
    }

    private BitstreamDTO readBitstream(Context context, Node bitstreamNode, ItemsImportMapping mapping) {

        Bitstreams bitstreamsMapping = mapping.getBitstreams();

        String location = getSingleValue(bitstreamNode, bitstreamsMapping.getUriXPath());

        List<MetadataValueDTO> metadataValues = readMetadataValues(context, bitstreamNode,
            bitstreamsMapping.getMetadataFields().getMetadataFields());

        return new BitstreamDTO("ORIGINAL", location, metadataValues);
    }

    private List<MetadataValueDTO> readMetadataValues(Context context, Node node, List<MetadataField> fields) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (ItemsImportMapping.MetadataField metadataField : fields) {

            ItemsImportMetadataFieldReader reader = readers.get(metadataField.getReader());

            NodeList nodeList = getNodeList(node, metadataField.getXPath());

            List<MetadataValueDTO> values = reader.readValues(context, metadataField.getField(), nodeList);

            metadataValues.addAll(values);
        }

        return metadataValues;

    }

    private NodeList getNodeList(Object item, String expression) {
        try {
            return (NodeList) xPath.compile(expression).evaluate(item, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("An error occurs evaluating path " + expression, e);
        }
    }

    private Node getNode(Object item, String expression) {
        try {
            return (Node) xPath.compile(expression).evaluate(item, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("An error occurs evaluating path " + expression, e);
        }
    }

    private String getSingleValue(Node node, String path) {
        try {
            return (String) xPath.compile(path).evaluate(node, XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("An error occurs evaluating path " + path, e);
        }
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

    private void printDocument(Node record, OutputStream out) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            transformer.transform(new DOMSource(record),
                new StreamResult(new OutputStreamWriter(out, "UTF-8")));
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
