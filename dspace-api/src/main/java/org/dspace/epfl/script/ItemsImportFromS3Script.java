/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static java.util.Arrays.asList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.io.IOUtils.readLines;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.IOUtils;
import org.dspace.app.bulkimport.exception.BulkImportException;
import org.dspace.app.bulkimport.model.BulkImportWorkbook;
import org.dspace.app.bulkimport.service.BulkImportWorkbookBuilder;
import org.dspace.content.Collection;
import org.dspace.content.dto.BitstreamDTO;
import org.dspace.content.dto.ItemDTO;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.core.Context;
import org.dspace.core.Context.Mode;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.epfl.script.model.ItemImportDTO;
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

    public static final String COLLECTION_PROPERTY_PREFIX = "epfl.items-import.collections";

    private CollectionService collectionService;

    private ConfigurationService configurationService;

    private BulkImportWorkbookBuilder workbookBuilder;

    private ItemsS3Service itemsS3Service;

    private MarcXmlParser marcXmlParser;

    private BitstreamUploadS3Service bitstreamUploadS3Service;

    private Context context;

    private Integer limit;

    private String startAfter;

    private String keysFilename;

    private List<String> keys = new ArrayList<>();

    private Map<String, String> collectionIds;

    private Map<String, Long> typeCounts;

    private int importedItemsCount = 0;

    private int skippedItemsCount = 0;

    private int errorsCount = 0;

    private ItemsImportMapping mapping;

    private boolean skipBitstreamsUpload;


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

        if (commandLine.hasOption('k')) {
            keys.addAll(asList(commandLine.getOptionValues('k')));
        }

        if (commandLine.hasOption('l')) {
            limit = Integer.valueOf(commandLine.getOptionValue('l'));
        }

        startAfter = commandLine.getOptionValue('a');

        keysFilename = commandLine.getOptionValue("kf");

        skipBitstreamsUpload = commandLine.hasOption("sbu");

        String configuration = configurationService.getProperty("epfl.items-import.mapping-configuration.path");
        mapping = marcXmlParser.parseMapping(configuration);

        typeCounts = new HashMap<>();

    }

    @Override
    public void internalRun() throws Exception {

        context = new Context(Mode.READ_ONLY);
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        collectionIds = readCollectionIds();

        if (isNotBlank(keysFilename)) {
            keys.addAll(readKeysFile());
        }

        try {

            Map<String, BulkImportWorkbook> workbooks = buildWorkbooks();

            writeWorkbooks(workbooks);

            context.complete();
            context.restoreAuthSystemState();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        }

    }

    private List<String> readKeysFile() throws Exception {

        InputStream inputStream = handler.getFileStream(context, keysFilename)
            .orElseThrow(() -> new IllegalArgumentException("Error reading file, the file couldn't be "
                + "found for filename: " + keysFilename));

        return readLines(inputStream, StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .collect(Collectors.toList());
    }

    private Map<String, BulkImportWorkbook> buildWorkbooks() {

        Map<String, BulkImportWorkbook> workbooks = new HashMap<String, BulkImportWorkbook>();

        Iterator<ItemImportDTO> items = readItems();

        while (items.hasNext()) {
            ItemImportDTO item = items.next();
            BulkImportWorkbook workbook = workbooks.computeIfAbsent(item.getType(), this::buildEmptyWorkbook);
            workbookBuilder.writeWorkbookContent(item.getItem(), workbook);
        }

        handler.logInfo("Import completed. Written " + importedItemsCount
            + " items with success. Skipped " + skippedItemsCount + " items. Errors: " + errorsCount);

        handler.logInfo("Created " + workbooks.size() + " workbooks for the following types: ");
        for (String type : typeCounts.keySet()) {
            handler.logInfo(type + " - Items count: " + typeCounts.get(type));
        }

        return workbooks;

    }

    private Iterator<ItemImportDTO> readItems() {
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

    private Optional<ItemImportDTO> getObject(String key) {

        try {
            InputStream content = itemsS3Service.getObject(key);
            return parseZip(key, content);
        } catch (Exception ex) {
            handler.logError("An error occurs reading entry with key " + key, ex);
            errorsCount++;
            return Optional.empty();
        }

    }

    private Optional<ItemImportDTO> parseZip(String key, InputStream data) throws Exception {

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

    private Optional<ItemImportDTO> readZipContent(String key, ZipFile zipFile) throws Exception {

        String id = StringUtils.removeEnd(key, ".zip");

        ItemImportDTO item = readItem(id, zipFile);

        if (item == null) {
            return Optional.empty();
        }

        if (isEmpty(item.getItem().getMetadataValues())) {
            skippedItemsCount++;
            handler.logWarning("No metadata read from entry with key " + key + ". Entry skipped");
            return Optional.empty();
        }

        if (!skipBitstreamsUpload) {
            uploadBitstreams(item, id, zipFile);
        }

        handler.logInfo("Entry with key " + key + " successfully read");

        String type = item.getType();
        Long currentCount = typeCounts.getOrDefault(type, 0L);
        typeCounts.put(type, currentCount + 1L);

        importedItemsCount++;

        return Optional.ofNullable(item);
    }

    private ItemImportDTO readItem(String id, ZipFile zipFile) throws Exception {
        return findSingleZipEntryByName(zipFile, id + File.separator + "metadata.xml")
            .map(zipEntry -> readSingleItem(id, zipFile, zipEntry))
            .orElse(null);
    }

    private ItemImportDTO readSingleItem(String id, ZipFile zipFile, ZipEntry zipEntry) {

        try {

            InputStream inputStream = zipFile.getInputStream(zipEntry);

            Node record = marcXmlParser.parse(inputStream, mapping.getItemXPath());

            if (record == null) {
                skippedItemsCount++;
                handler.logWarning("Entry with id " + id + " skipped because no record was found");
                return null;
            }

            String recordType = marcXmlParser.getRecordType(record).orElse(null);

            if (isEmpty(recordType)) {
                skippedItemsCount++;
                handler.logWarning("Entry with id " + id + " skipped because no item type found");
                return null;
            }

            ItemDTO item = marcXmlParser.readSingleItem(context, id, record, mapping);

            return new ItemImportDTO(recordType, item);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void uploadBitstreams(ItemImportDTO item, String id, ZipFile zipFile) throws Exception {

        List<BitstreamDTO> bitstreams = item.getItem().getBitstreams();

        if (CollectionUtils.isEmpty(bitstreams)) {
            return;
        }

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().startsWith(id + File.separator + "files")) {

                String fileName = substringAfterLast(entry.getName(), File.separator);

                verifyBitstreamChecksum(fileName, bitstreams, zipFile.getInputStream(entry));

                String bitstreamName = id + "_" + escapeBitstreamName(fileName);
                bitstreamUploadS3Service.upload(zipFile.getInputStream(entry), bitstreamName);

                handler.logInfo("Bitstream named " + bitstreamName + " uploaded with success");
            }
        }

    }

    private String escapeBitstreamName(String name) {
        return name.replace("+", "");
    }

    private void verifyBitstreamChecksum(String fileName, List<BitstreamDTO> bitstreams, InputStream document) {
        bitstreams.stream()
            .filter(bitstream -> hasTitleEqualsTo(bitstream, fileName))
            .map(bitstream -> bitstream.getChecksum())
            .findFirst()
            .ifPresent(checksum -> verifyChecksum(fileName, checksum, document));
    }

    private void verifyChecksum(String fileName, String checksum, InputStream document) {
        try {
            String md5Hex = DigestUtils.md5Hex(document);
            if (!checksum.equals(md5Hex)) {
                throw new IllegalArgumentException("The validation of the checksum of file named "
                    + fileName + " fails. Expected " + checksum + ", found " + md5Hex);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasTitleEqualsTo(BitstreamDTO bitstream, String fileName) {
        return bitstream.getMetadataValues("dc.title").stream()
            .anyMatch(metadataValue -> metadataValue.getValue().equals(fileName));
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

    private BulkImportWorkbook buildEmptyWorkbook(String type) {
        Collection collection = getCollection(collectionIds.get(type));
        return workbookBuilder.buildEmptyWorkbook(context, collection);
    }

    private void writeWorkbooks(Map<String, BulkImportWorkbook> workbooks) throws Exception {
        context.setMode(Mode.READ_WRITE);
        for (String workbookName : workbooks.keySet()) {
            writeWorkbook(workbooks.get(workbookName).getWorkbook(), workbookName);
        }

    }

    private void writeWorkbook(Workbook workbook, String name) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        InputStream is = new ByteArrayInputStream(bos.toByteArray());
        handler.writeFilestream(context, name + ".xls", is, "bulk-import-excel-" + UUID.randomUUID(), false);
    }

    private Map<String, String> readCollectionIds() {

        Map<String, String> collectionIds = new HashMap<>();

        for (String filterName : marcXmlParser.getAllRecordTypes()) {
            String propertyKey = COLLECTION_PROPERTY_PREFIX + "." + filterName;
            String collectionId = configurationService.getProperty(propertyKey);
            if (StringUtils.isBlank(collectionId)) {
                throw new IllegalStateException("No collection defined for filter " + filterName);
            }

            Collection collection = getCollection(collectionId);
            if (collection == null) {
                throw new IllegalStateException("No collection found by uuid " + collectionId);
            }

            collectionIds.put(filterName, collectionId);

        }

        return collectionIds;
    }

    private Collection getCollection(String collectionId) {
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
