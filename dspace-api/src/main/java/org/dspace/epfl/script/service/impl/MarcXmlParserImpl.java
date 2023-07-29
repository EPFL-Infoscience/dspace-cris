/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service.impl;

import static java.util.stream.Collectors.toMap;
import static org.dspace.authorize.ResourcePolicy.TYPE_CUSTOM;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.dspace.content.authority.Choice;
import org.dspace.content.authority.ChoiceAuthority;
import org.dspace.content.authority.Choices;
import org.dspace.content.authority.service.ChoiceAuthorityService;
import org.dspace.content.dto.BitstreamDTO;
import org.dspace.content.dto.ItemDTO;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.dto.ResourcePolicyDTO;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.epfl.script.model.ItemsImportMapping;
import org.dspace.epfl.script.model.ItemsImportMapping.Bitstreams;
import org.dspace.epfl.script.model.ItemsImportMapping.MetadataField;
import org.dspace.epfl.script.reader.ItemsImportMetadataFieldReader;
import org.dspace.epfl.script.service.ItemsS3Service;
import org.dspace.epfl.script.service.MarcXmlParser;
import org.dspace.services.ConfigurationService;
import org.dspace.util.MultiFormatDateParser;
import org.dspace.utils.DSpace;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class MarcXmlParserImpl implements MarcXmlParser {

    public static final String TYPE_FILTER_PROPERTY_PREFIX = "epfl.items-import.types";

    public static final String TYPE_VOCABULARY_PROPERTY_PREFIX = "epfl.items-import.vocabulary";

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ItemsS3Service itemsS3Service;

    @Autowired
    private ChoiceAuthorityService choiceAuthorityService;

    private DocumentBuilder documentBuilder;

    private Map<String, ItemsImportMetadataFieldReader> readers;

    private Map<String, String> typeFilters;

    private Map<String, String> typeVocabularies;

    private DateFormat CREATION_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private XPath xPath;

    @PostConstruct
    private void setup() {

        try {
            this.documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }

        typeFilters = readAllTypeFilters();

        typeVocabularies = readAllTypeVocabularies();

        readers = new DSpace().getServiceManager().getServicesByType(ItemsImportMetadataFieldReader.class)
            .stream().collect(toMap(ItemsImportMetadataFieldReader::getReaderName, Function.identity()));

        xPath = XPathFactory.newInstance().newXPath();

    }

    @Override
    public Set<String> getAllRecordTypes() {
        return typeFilters.keySet();
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
    public Node parse(InputStream source, String recordXPath) {
        try {
            Document document = documentBuilder.parse(source);
            return getNode(document, recordXPath);
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<String> getRecordType(Node record) {
        for (String filterName : typeFilters.keySet()) {
            String filter = typeFilters.get(filterName);
            if (evaluateFilter(record, filter)) {
                return Optional.of(filterName);
            }
        }
        return Optional.empty();
    }

    @Override
    public ItemDTO readSingleItem(Context context, String id, Node record, ItemsImportMapping mapping) {

        String recordType = getRecordType(record)
            .orElseThrow(() -> new IllegalArgumentException("No type found for the given record"));

        return readSingleItem(context, id, recordType, record, mapping);
    }

    @Override
    public ItemDTO readSingleItem(Context context, String id, String recordType, Node record,
        ItemsImportMapping mapping) {

        List<MetadataValueDTO> metadataValues = readItemMetadataValues(context, record, mapping);

        metadataValues.addAll(getCreationDateMetadataValues(id));

        List<BitstreamDTO> bitstreams = readBitstreams(context, id, record, mapping);

        String submitter = readSubmitter(context, record, mapping);

        return new ItemDTO("LEGACY-ID::" + id, submitter, metadataValues, bitstreams);

    }

    private MetadataValueDTO getItemType(Context context, String recordType) {

        String vocabulary = typeVocabularies.get(recordType);

        if (StringUtils.isBlank(vocabulary)) {
            throw new IllegalStateException("No vocabulary defined for record type " + recordType);
        }

        ChoiceAuthority authority = choiceAuthorityService.getChoiceAuthorityByAuthorityName(vocabulary.split(":")[0]);

        Choice choice = authority.getChoice(vocabulary, context.getCurrentLocale().toString());
        if (choice == null) {
            throw new IllegalStateException("No choice found by vocabulary " + vocabulary);
        }

        return new MetadataValueDTO("dc.type", choice.value, vocabulary, Choices.CF_ACCEPTED);
    }

    @Override
    public List<MetadataValueDTO> readItemMetadataValues(Context context, InputStream source,
        ItemsImportMapping mapping) {

        try {
            Node record = parse(source, mapping.getItemXPath());
            return readItemMetadataValues(context, record, mapping);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public List<MetadataValueDTO> readItemMetadataValues(Context context, Node record, ItemsImportMapping mapping) {
        String recordType = getRecordType(record).orElse(null);
        return readItemMetadataValues(context, record, recordType, mapping);
    }

    @Override
    public List<BitstreamDTO> readBitstreams(Context context, String id, Node record, ItemsImportMapping mapping) {

        String bitstreamXPath = mapping.getBitstreams().getBitstreamXPath();
        if (StringUtils.isBlank(bitstreamXPath)) {
            return List.of();
        }

        NodeList bitstreamNodeList = getNodeList(record, bitstreamXPath);

        List<BitstreamDTO> bitstreams = new ArrayList<BitstreamDTO>();

        for (int i = 0; i < bitstreamNodeList.getLength(); i++) {
            Node bitstreamNode = bitstreamNodeList.item(i);
            readBitstream(context, id, bitstreamNode, i, mapping).ifPresent(bitstreams::add);
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

    private Optional<BitstreamDTO> readBitstream(Context context, String id, Node bitstreamNode, int position,
        ItemsImportMapping mapping) {

        Bitstreams bitstreamsMapping = mapping.getBitstreams();

        List<MetadataValueDTO> metadataValues = readMetadataValues(context, bitstreamNode, null,
            bitstreamsMapping.getMetadataFields().getMetadataFields());

        String fileName = getFileNameFromMetadataValues(metadataValues);
        if (StringUtils.isBlank(fileName)) {
            return Optional.empty();
        }

        List<ResourcePolicyDTO> policies = readResourcePolicies(bitstreamNode, bitstreamsMapping);

        String location = getBitstreamUrl() + id + "_" + fileName;
        String checksum = getSingleValue(bitstreamNode, bitstreamsMapping.getChecksumXPath());

        return Optional.of(new BitstreamDTO("ORIGINAL", location, checksum, metadataValues, policies));

    }

    private List<ResourcePolicyDTO> readResourcePolicies(Node bitstreamNode, Bitstreams bitstreamsMapping) {

        List<ResourcePolicyDTO> policies = new ArrayList<ResourcePolicyDTO>();

        String accessCondition = getSingleValue(bitstreamNode, bitstreamsMapping.getAccessConditionXPath());

        if (StringUtils.isEmpty(accessCondition)) {
            return policies;
        }

        String name = "openaccess";
        Date startDate = null;

        if (accessCondition.equalsIgnoreCase("Private") || accessCondition.equalsIgnoreCase("Role")) {
            name = "reserved";
        } else if (accessCondition.equalsIgnoreCase("Restricted")) {
            name = "restricted";
        } else if (accessCondition.toLowerCase().startsWith("embargo")) {
            name = "embargo";
            startDate = MultiFormatDateParser.parse(substringBetween(accessCondition, "(", ")"));
        }

        policies.add(new ResourcePolicyDTO(name, null, Constants.READ, TYPE_CUSTOM, startDate, null));

        return policies;
    }

    private String substringBetween(String str, String firstDelimiter, String secondDelimiter) {
        return str.substring(str.indexOf(firstDelimiter) + 1, str.indexOf(secondDelimiter)).trim();
    }

    private List<MetadataValueDTO> readItemMetadataValues(Context context, Node record, String recordType,
        ItemsImportMapping mapping) {
        return readMetadataValues(context, record, recordType, mapping.getMetadataFields().getMetadataFields());
    }

    private List<MetadataValueDTO> readMetadataValues(Context context, Node record, String recordType,
        List<MetadataField> fields) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        if (recordType != null) {
            metadataValues.add(getItemType(context, recordType));
        }

        for (ItemsImportMapping.MetadataField metadataField : fields) {

            ItemsImportMetadataFieldReader reader = readers.get(metadataField.getReader());

            NodeList nodeList = getNodeList(record, metadataField.getXPath());

            List<MetadataValueDTO> values = reader.readValues(context, metadataField.getField(), nodeList);

            metadataValues.addAll(values);
        }

        return metadataValues;

    }

    private List<MetadataValueDTO> getCreationDateMetadataValues(String id) {
        String value = itemsS3Service.getCreationDate(id);
        String[] metadataFields = getCreationDateMetadataFields();
        return Arrays.stream(metadataFields)
            .map(metadataField -> getCreationDateMetadataValue(metadataField, value))
            .collect(Collectors.toList());
    }

    private MetadataValueDTO getCreationDateMetadataValue(String metadataField, String value) {

        String dateFormat = getCreationDateFormat(metadataField);

        if (StringUtils.isBlank(dateFormat)) {
            return new MetadataValueDTO(metadataField, value);
        }

        Date creationDate = parseCreationDate(value);
        return new MetadataValueDTO(metadataField, new SimpleDateFormat(dateFormat).format(creationDate));

    }

    private Date parseCreationDate(String creationDate) {
        try {
            return CREATION_DATE_FORMAT.parse(creationDate);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private String getCreationDateFormat(String metadataField) {
        String field = StringUtils.replace(metadataField, ".", "-");
        return configurationService.getProperty("epfl.items-import.creation-date." + field + ".format");
    }

    private String[] getCreationDateMetadataFields() {
        return configurationService.getArrayProperty("epfl.items-import.creation-date.fields");
    }

    private String getBitstreamUrl() {
        return configurationService.getProperty("epfl.items-import.upload-aws.url");
    }

    private String getFileNameFromMetadataValues(List<MetadataValueDTO> metadataValues) {
        return metadataValues.stream()
            .filter(metadataValue -> "dc.title".equals(metadataValue.getMetadataField()))
            .map(MetadataValueDTO::getValue)
            .findFirst()
            .orElse(null);
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

    private Boolean evaluateFilter(Node node, String path) {
        try {
            return (Boolean) xPath.compile(path).evaluate(node, XPathConstants.BOOLEAN);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("An error occurs evaluating path " + path, e);
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

    private Map<String, String> readAllTypeFilters() {

        Map<String, String> filters = new HashMap<>();

        List<String> propertyKeys = configurationService.getPropertyKeys(TYPE_FILTER_PROPERTY_PREFIX);

        for (String propertyKey : propertyKeys) {

            String filter = configurationService.getProperty(propertyKey);
            String filterName = StringUtils.removeStart(propertyKey, TYPE_FILTER_PROPERTY_PREFIX + ".");

            filters.put(filterName, filter);

        }

        return filters;
    }

    private Map<String, String> readAllTypeVocabularies() {

        Map<String, String> vocabularies = new HashMap<>();

        for (String recordType : getAllRecordTypes()) {
            String vocabulary = configurationService.getProperty(TYPE_VOCABULARY_PROPERTY_PREFIX + "." + recordType);
            if (StringUtils.isNotBlank(vocabulary)) {
                vocabularies.put(recordType, vocabulary);
            }
        }

        return vocabularies;

    }

}
