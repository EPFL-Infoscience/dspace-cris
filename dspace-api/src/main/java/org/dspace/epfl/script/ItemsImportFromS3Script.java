/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static java.util.stream.Collectors.toMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.bind.JAXBContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.cli.ParseException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.IOUtils;
import org.dspace.app.bulkimport.exception.BulkImportException;
import org.dspace.app.bulkimport.service.BulkImportWorkbookBuilder;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.dto.BitstreamDTO;
import org.dspace.content.dto.ItemDTO;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.core.Context;
import org.dspace.core.Context.Mode;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.epfl.script.model.ItemsImportMapping;
import org.dspace.epfl.script.reader.ItemsImportMetadataFieldReader;
import org.dspace.epfl.script.service.ItemsS3Service;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportFromS3Script
    extends DSpaceRunnable<ItemsImportFromS3ScriptConfiguration<ItemsImportFromS3Script>> {


    private CollectionService collectionService;

    private ConfigurationService configurationService;

    private BulkImportWorkbookBuilder workbookBuilder;

    private ItemsS3Service itemsS3Service;

    private DocumentBuilder documentBuilder;


    private Map<String, ItemsImportMetadataFieldReader> readers;

    private ItemsImportMapping mapping;

    private XPath xPath;


    private Context context;

    private String collectionId;

    private Integer limit;

    private String startAfter;

    private List<String> keys = new ArrayList<>();

    private int importedItemsCount = 0;

    private int errorsCount = 0;


    @Override
    public void setup() throws ParseException {

        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        this.collectionService = ContentServiceFactory.getInstance().getCollectionService();
        this.workbookBuilder = new DSpace().getServiceManager()
            .getServicesByType(BulkImportWorkbookBuilder.class).get(0);
        this.itemsS3Service = new DSpace().getServiceManager()
            .getServicesByType(ItemsS3Service.class).get(0);

        readers = new DSpace().getServiceManager().getServicesByType(ItemsImportMetadataFieldReader.class)
            .stream().collect(toMap(ItemsImportMetadataFieldReader::getReaderName, Function.identity()));

        mapping = parseMapping();

        try {
            this.documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }

        xPath = XPathFactory.newInstance().newXPath();

        collectionId = commandLine.getOptionValue('c');

        if (commandLine.hasOption('k')) {
            keys = Arrays.asList(commandLine.getOptionValues('k'));
        }

        if (commandLine.hasOption('l')) {
            limit = Integer.valueOf(commandLine.getOptionValue('l'));
        }

        startAfter = commandLine.getOptionValue('a');

    }

    @Override
    public void internalRun() throws Exception {

        context = new Context(Mode.READ_ONLY);
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        Collection collection = getCollection();
        if (collection == null) {
            throw new IllegalArgumentException("No collection found with id " + collectionId);
        }

        try {

            Workbook workbook = buildWorkbook();
            writeWorkbook(workbook);

            context.complete();
            context.restoreAuthSystemState();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        }

    }

    private Workbook buildWorkbook() {

        Iterator<ItemDTO> items = readItems();

        Workbook workbook = workbookBuilder.build(context, getCollection(), items);

        handler.logInfo("Import completed. Written " + importedItemsCount
            + " items with success. Errors: " + errorsCount);

        return workbook;

    }

    private Iterator<ItemDTO> readItems() {
        return getItemsKeys()
            .flatMap(key -> getObject(key).stream())
            .iterator();
    }

    private Stream<String> getItemsKeys() {

        if (CollectionUtils.isNotEmpty(keys)) {
            return keys.stream();
        }

        if (limit != null) {
            return itemsS3Service.getItemsKeys(limit, startAfter);
        }

        return itemsS3Service.getAllItemsKeys();
    }

    private Optional<ItemDTO> getObject(String key) {

        try {
            InputStream content = itemsS3Service.getObject(key);
            return Optional.of(parseZip(key, content));
        } catch (Exception ex) {
            handler.handleException("An error occurs reading entry with key " + key, ex);
            errorsCount++;
            return Optional.empty();
        }

    }

    private ItemDTO parseZip(String key, InputStream data) throws Exception {

        File tempFile = createTempFile(key, data);

        try {
            ZipFile zipFile = parseZip(tempFile);
            return readZipContent(key, zipFile);
        } finally {
            tempFile.delete();
        }

    }

    private File createTempFile(String key, InputStream data) {
        try {
            File tempFile = Files.createTempFile(key, ".temp").toFile();
            IOUtils.copy(data, tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ItemDTO readZipContent(String key, ZipFile zipFile) throws Exception {

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        handler.logInfo("Reading entry with key " + key);

        String id = StringUtils.removeEnd(key, ".zip");

        List<MetadataValueDTO> metadataValues = new ArrayList<>();
        List<BitstreamDTO> bitstreams = new ArrayList<>();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().equals(id + File.separator + "metadata.xml")) {
                metadataValues.addAll(readMetadata(zipFile.getInputStream(entry)));
            }
        }

        if (metadataValues.isEmpty()) {
            throw new IllegalStateException("No metadata read from entry with key " + key);
        }

        String legacyId = getCrisLegacyId(metadataValues)
            .orElse(id);

        return new ItemDTO("LEGACY-ID::" + legacyId, metadataValues, bitstreams);
    }

    private Optional<String> getCrisLegacyId(List<MetadataValueDTO> metadataValues) {
        return metadataValues.stream()
            .filter(metadata -> metadata.getMetadataField().equals("cris.legacyId"))
            .map(MetadataValueDTO::getValue)
            .findFirst();
    }

    private List<? extends MetadataValueDTO> readMetadata(InputStream metadataXml) throws Exception {

        Document document = documentBuilder.parse(metadataXml);

        Node record = getNode(document, mapping.getItemXPath());

        return getMetadataValues(record);

    }

    private List<MetadataValueDTO> getMetadataValues(Node node) throws Exception {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (ItemsImportMapping.MetadataField metadataField : mapping.getMetadataFields().getMetadataFields()) {

            ItemsImportMetadataFieldReader reader = readers.get(metadataField.getReader());

            NodeList nodeList = getNodeList(node, metadataField.getXPath());

            List<MetadataValueDTO> values = reader.readValues(context, metadataField.getField(), nodeList);

            metadataValues.addAll(values);
        }

        metadataValues.forEach(System.out::println);

        System.out.println("---------------------------");

        return metadataValues;

    }

    private NodeList getNodeList(Object item, String expression) throws XPathExpressionException {
        return (NodeList) xPath.compile(expression).evaluate(item, XPathConstants.NODESET);
    }

    private ZipFile parseZip(File file) throws Exception {
        return new ZipFile(file);
    }

    private void writeWorkbook(Workbook workbook) throws IOException, SQLException, AuthorizeException {
        context.setMode(Mode.READ_WRITE);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        InputStream is = new ByteArrayInputStream(bos.toByteArray());
        handler.writeFilestream(context, "items.xls", is, "application/vnd.ms-excel", false);
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

        try (FileReader mappingReader = new FileReader(config)) {
            JAXBContext jaxbContext = JAXBContext.newInstance(ItemsImportMapping.class);
            return (ItemsImportMapping) jaxbContext.createUnmarshaller().unmarshal(mappingReader);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    private Collection getCollection() {
        try {
            return collectionService.find(context, UUID.fromString(collectionId));
        } catch (SQLException e) {
            throw new BulkImportException(e);
        }
    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() throws SQLException {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ItemsImportFromS3ScriptConfiguration<ItemsImportFromS3Script> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("items-import-from-s3",
            ItemsImportFromS3ScriptConfiguration.class);
    }

}
