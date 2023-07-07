/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.endsWith;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.xpath.XPath;
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
import org.dspace.content.dto.ItemDTO;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.core.Context;
import org.dspace.core.Context.Mode;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.epfl.script.model.ItemsImportMapping;
import org.dspace.epfl.script.service.BitstreamUploadS3Service;
import org.dspace.epfl.script.service.ItemsS3Service;
import org.dspace.epfl.script.service.MarcXmlParser;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.w3c.dom.Node;

public class ItemsImportFromS3Script
    extends DSpaceRunnable<ItemsImportFromS3ScriptConfiguration<ItemsImportFromS3Script>> {


    private CollectionService collectionService;

    private ConfigurationService configurationService;

    private BulkImportWorkbookBuilder workbookBuilder;

    private ItemsS3Service itemsS3Service;

    private MarcXmlParser marcXmlParser;

    private BitstreamUploadS3Service bitstreamUploadS3Service;


    private Context context;

    private String collectionId;

    private Integer limit;

    private String startAfter;

    private String filter;

    private String recordXPath;

    private List<String> keys = new ArrayList<>();

    private int downloadedItemsCount = 0;

    private int importedItemsCount = 0;

    private int errorsCount = 0;

    private ItemsImportMapping mapping;


    @Override
    public void setup() throws ParseException {

        this.collectionService = ContentServiceFactory.getInstance().getCollectionService();
        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        this.workbookBuilder = new DSpace().getServiceManager()
            .getServicesByType(BulkImportWorkbookBuilder.class).get(0);
        this.itemsS3Service = new DSpace().getServiceManager()
            .getServicesByType(ItemsS3Service.class).get(0);
        this.marcXmlParser = new DSpace().getServiceManager()
            .getServicesByType(MarcXmlParser.class).get(0);
        this.bitstreamUploadS3Service = new DSpace().getServiceManager()
            .getServicesByType(BitstreamUploadS3Service.class).get(0);


        collectionId = commandLine.getOptionValue('c');

        if (commandLine.hasOption('k')) {
            keys = Arrays.asList(commandLine.getOptionValues('k'));
        }

        if (commandLine.hasOption('l')) {
            limit = Integer.valueOf(commandLine.getOptionValue('l'));
        }

        startAfter = commandLine.getOptionValue('a');

        filter = commandLine.getOptionValue('f');

        String configuration = configurationService.getProperty("epfl.items-import.mapping-configuration.path");
        mapping = marcXmlParser.parseMapping(configuration);

        recordXPath = composeRecordXPath();

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

        validateExpression(recordXPath);

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

    private void validateExpression(String expression) {
        try {
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();
            xpath.compile(expression);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("The provided record expression is not valid: " + recordXPath);
        }
    }

    private Workbook buildWorkbook() {

        handler.logInfo("Import started with record xPath equals to " + recordXPath);

        Iterator<ItemDTO> items = readItems();

        Workbook workbook = workbookBuilder.build(context, getCollection(), items);

        handler.logInfo("Import completed. Downloaded " + downloadedItemsCount
            + ". Written " + importedItemsCount + " items with success. Errors: " + errorsCount);

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
            downloadedItemsCount++;
            System.out.println(downloadedItemsCount);
            return parseZip(key, content);
        } catch (Exception ex) {
            handler.handleException("An error occurs reading entry with key " + key, ex);
            errorsCount++;
            return Optional.empty();
        }

    }

    private Optional<ItemDTO> parseZip(String key, InputStream data) throws Exception {

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

    private Optional<ItemDTO> readZipContent(String key, ZipFile zipFile) throws Exception {

        handler.logInfo("Reading entry with key " + key);

        String id = StringUtils.removeEnd(key, ".zip");

        ItemDTO item = readItem(id, zipFile);

        if (item == null) {
            return Optional.empty();
        }

        if (isEmpty(item.getMetadataValues())) {
            handler.logWarning("No metadata read from entry with key " + key + ". Entry skipped");
            return Optional.empty();
        }

        uploadBitstreams(id, zipFile);

        handler.logInfo("Entry with key " + key + " successfully read");

        importedItemsCount++;

        return Optional.ofNullable(item);
    }

    private ItemDTO readItem(String id, ZipFile zipFile) throws Exception {
        return findSingleZipEntryByName(zipFile, id + File.separator + "metadata.xml")
            .map(zipEntry -> readSingleItem(id, zipFile, zipEntry))
            .orElse(null);
    }

    private ItemDTO readSingleItem(String id, ZipFile zipFile, ZipEntry zipEntry) {

        try {

            InputStream inputStream = zipFile.getInputStream(zipEntry);

            Node record = marcXmlParser.parse(inputStream, recordXPath);

            if (record == null) {
                handler.logInfo("Entry with id " + id + " skipped");
                return null;
            }

            return marcXmlParser.readSingleItem(context, id, record, mapping);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private String composeRecordXPath() {

        String baseRecordXPath = mapping.getItemXPath();
        if (StringUtils.isBlank(filter)) {
            return baseRecordXPath;
        }

        String filterToApply = getFilterByName()
            .orElse(filter);

        if (!startsWith(filterToApply, "[") && !endsWith(filterToApply, "[")) {
            filterToApply = "[" + filterToApply + "]";
        }

        return baseRecordXPath + filterToApply;
    }

    private Optional<String> getFilterByName() {
        return Optional.ofNullable(filter)
            .map(value -> configurationService.getProperty("epfl.items-import.filters." + value));
    }

    private void uploadBitstreams(String id, ZipFile zipFile) throws Exception {

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().startsWith(id + File.separator + "files")) {
                String bitstreamName = id + "_" + substringAfterLast(entry.getName(), File.separator);
                bitstreamUploadS3Service.upload(zipFile.getInputStream(entry), bitstreamName);
                handler.logInfo("Bitstream named " + bitstreamName + " uploaded with success");
            }
        }

    }

    private Optional<ZipEntry> findSingleZipEntryByName(ZipFile zipFile, String name) {

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().equals(name)) {
                return Optional.of(entry);
            }
        }

        return Optional.empty();
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
