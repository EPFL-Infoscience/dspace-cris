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
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.dspace.authorize.ResourcePolicy.TYPE_CUSTOM;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.dspace.app.bulkimport.exception.BulkImportException;
import org.dspace.app.bulkimport.model.BulkImportWorkbook;
import org.dspace.app.bulkimport.service.BulkImportWorkbookBuilder;
import org.dspace.authority.service.ItemSearchService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.dto.BitstreamDTO;
import org.dspace.content.dto.ItemDTO;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.dto.ResourcePolicyDTO;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Constants;
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
import org.dspace.submit.model.AccessConditionOption;
import org.dspace.submit.model.UploadConfiguration;
import org.dspace.submit.model.UploadConfigurationService;
import org.dspace.utils.DSpace;
import org.w3c.dom.Node;

public class ItemsImportFromS3Script
    extends DSpaceRunnable<ItemsImportFromS3ScriptConfiguration<ItemsImportFromS3Script>> {

    public static final String COLLECTION_PROPERTY_PREFIX = "epfl.items-import.collections";

    private CollectionService collectionService;

    private ConfigurationService configurationService;

    private ItemSearchService itemSearchService;

    private ItemService itemService;

    private BulkImportWorkbookBuilder workbookBuilder;

    private WorkspaceItemService workspaceItemService;

    private InstallItemService installItemService;

    private ItemsS3Service itemsS3Service;

    private ResourcePolicyService resourcePolicyService;

    private Map<String, AccessConditionOption> uploadAccessConditions;

    private MarcXmlParser marcXmlParser;

    private BitstreamUploadS3Service bitstreamUploadS3Service;
    private MimeTypes mimeRepository;
    private Tika tika;

    private Context context;

    private Integer limit;

    private String startAfter;

    private String keysFilename;

    private List<String> keys = new ArrayList<>();

    private Map<String, String> collectionIds;

    private BundleService bundleService;

    private Map<String, Long> typeCounts;

    private int importedItemsCount = 0;

    private int skippedItemsCount = 0;

    private int errorsCount = 0;

    private ItemsImportMapping mapping;

    private boolean skipBitstreamsUpload;

    private boolean workbookMode;

    private boolean overwriteBitstreams;

    private String creationDatesFileName;

    private BitstreamService bitstreamService;

    private UploadConfigurationService uploadConfigurationService;

    private BitstreamFormatService bitstreamFormatService;

    @Override
    public void setup() throws ParseException {

        this.collectionService = ContentServiceFactory.getInstance().getCollectionService();
        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        this.itemService = ContentServiceFactory.getInstance().getItemService();
        this.workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();
        this.installItemService = ContentServiceFactory.getInstance().getInstallItemService();
        this.workbookBuilder = new DSpace().getServiceManager()
            .getServicesByType(BulkImportWorkbookBuilder.class).get(0);
        this.itemsS3Service = new DSpace().getServiceManager()
            .getServicesByType(ItemsS3Service.class).get(0);
        this.marcXmlParser = new DSpace().getServiceManager()
            .getServicesByType(MarcXmlParser.class).get(0);
        this.bitstreamUploadS3Service = new DSpace().getServiceManager()
            .getServicesByType(BitstreamUploadS3Service.class).get(0);
        this.mimeRepository = TikaConfig.getDefaultConfig().getMimeRepository();
        this.tika = new Tika();
        this.itemSearchService = new DSpace().getSingletonService(ItemSearchService.class);
        this.bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        this.bundleService = ContentServiceFactory.getInstance().getBundleService();
        this.bitstreamFormatService = ContentServiceFactory.getInstance().getBitstreamFormatService();
        this.resourcePolicyService = AuthorizeServiceFactory.getInstance().getResourcePolicyService();
        this.uploadConfigurationService = AuthorizeServiceFactory.getInstance().getUploadConfigurationService();

        if (commandLine.hasOption('k')) {
            keys.addAll(asList(commandLine.getOptionValues('k')));
        }

        if (commandLine.hasOption('l')) {
            limit = Integer.valueOf(commandLine.getOptionValue('l'));
        }

        startAfter = commandLine.getOptionValue('a');

        keysFilename = commandLine.getOptionValue("kf");

        skipBitstreamsUpload = commandLine.hasOption("sbu");

        overwriteBitstreams = commandLine.hasOption("ob");
        workbookMode = commandLine.hasOption("w");
        creationDatesFileName = commandLine.getOptionValue("cd");

        String configuration = configurationService.getProperty("epfl.items-import.mapping-configuration.path");
        mapping = marcXmlParser.parseMapping(configuration);

        typeCounts = new HashMap<>();

    }

    @Override
    public void internalRun() throws Exception {

        if (workbookMode && isBlank(creationDatesFileName)) {
            context = new Context(Mode.READ_ONLY);
        } else {
            context = new Context();
        }
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        if (isNotBlank(creationDatesFileName)) {

            InputStream inputStream = handler.getFileStream(context, creationDatesFileName)
                .orElseThrow(() -> new IllegalArgumentException("Error reading file, the file couldn't be "
                    + "found for filename: " + creationDatesFileName));

            Integer count = itemsS3Service.importCreationDates(context, inputStream, handler);
            handler.logInfo("Imported " + count + " creation dates");

            context.complete();
            context.restoreAuthSystemState();

            return;
        }

        collectionIds = readCollectionIds();

        if (isNotBlank(keysFilename)) {
            keys.addAll(readKeysFile());
        }

        try {

            if (workbookMode) {
                Map<String, BulkImportWorkbook> workbooks = buildWorkbooks();
                writeWorkbooks(workbooks);
            } else {
                importItems();
            }

            context.complete();
            context.restoreAuthSystemState();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        }

    }

    private void importItems() throws Exception {

        Iterator<ItemImportDTO> items = readItems();

        int count = 0;

        while (items.hasNext()) {
            ItemImportDTO item = items.next();
            try {
                performItemImport(item);
                count++;
                if (count % 20 == 0) {
                    context.commit();
                    handler.logInfo("Imported " + count + " items");
                }
            } catch (Exception ex) {
                handler.logError("An error occurs importing item with ID " + item.getItem().getId(), ex);
                errorsCount++;
            }
        }

        context.commit();

    }

    private void performItemImport(ItemImportDTO itemImport) throws Exception {

        Item item = searchItemById(itemImport.getItem().getId());

        if (item != null) {
            item = updateItem(itemImport, item);
        } else {
            item = createItem(itemImport);
        }

        context.uncacheEntity(item);

    }

    private Item updateItem(ItemImportDTO itemImport, Item item) throws Exception {
        removeItemMetadataValuesAndBitstreams(item);

        addMetadataValues(itemImport, item);
        if (overwriteBitstreams) {
            addBitstreams(itemImport, item);
        }

        itemService.update(context, item);

        handler.logInfo(
            "Imported record with ID: " + itemImport.getItem().getId() + ". Updated item with UUID: " + item.getID());
        return item;

    }

    private void removeItemMetadataValuesAndBitstreams(Item item) throws Exception {
        removeItemMetadataValues(item);
        if (overwriteBitstreams) {
            removeItemBitstreams(item);
        }
    }

    private void removeItemMetadataValues(Item item) throws SQLException {

        Set<String> metadataFieldsToKeep = getMetadataFieldsToKeep();

        List<MetadataValue> metadataToRemove = item.getMetadata().stream()
            .filter(value -> !metadataFieldsToKeep.contains(value.getMetadataField().toString('.')))
            .collect(Collectors.toList());

        itemService.removeMetadataValues(context, item, metadataToRemove);

    }

    private Set<String> getMetadataFieldsToKeep() {
        return Set.of(configurationService.getArrayProperty("epfl.items-import.update.metadata-to-keep"));
    }

    private void removeItemBitstreams(Item item) throws Exception {
        itemService.removeAllBundles(context, item);
    }

    private Item createItem(ItemImportDTO itemImport) throws Exception {

        Collection collection = getCollection(collectionIds.get(itemImport.getType()));
        WorkspaceItem workspaceItem = workspaceItemService.create(context, collection, true);
        Item item = workspaceItem.getItem();

        addMetadataValues(itemImport, item);
        addBitstreams(itemImport, item);

        item = installItemService.installItem(context, workspaceItem);

        handler.logInfo(
            "Imported record with ID: " + itemImport.getItem().getId() + ". Created item with UUID: " + item.getID());

        return item;

    }

    private void addBitstreams(ItemImportDTO itemImport, Item item) throws Exception {

        List<BitstreamDTO> bitstreams = itemImport.getItem().getBitstreams();

        for (BitstreamDTO bitstreamDto : bitstreams) {

            String bundleName = bitstreamDto.getBundleName();
            List<MetadataValueDTO> metadataValues = bitstreamDto.getMetadataValues();
            List<ResourcePolicyDTO> resourcePolicies = bitstreamDto.getResourcePolicies();
            String checksum = bitstreamDto.getChecksum();
            InputStream inputStream = bitstreamDto.getContent();

            Bundle bundle = getBundleByName(item, bundleName)
                .orElseGet(() -> createBundle(item, bundleName));

            Bitstream bitstream = bitstreamService.create(context, bundle, inputStream);
            bitstream.setChecksum(checksum);
            setBitstreamFormat(bitstream);
            setBitstreamPolicies(bitstream, resourcePolicies);
            addBitstreamMetadataValues(bitstream, metadataValues);

            bitstreamService.update(context, bitstream);

        }

    }

    private void addBitstreamMetadataValues(Bitstream bitstream, List<MetadataValueDTO> metadataValues)
        throws SQLException {

        for (MetadataValueDTO metadataValue : metadataValues) {
            bitstreamService.addMetadata(context, bitstream, metadataValue.getSchema(), metadataValue.getElement(),
                metadataValue.getQualifier(), metadataValue.getLanguage(), metadataValue.getValue(),
                metadataValue.getAuthority(), metadataValue.getConfidence());
        }

    }

    private void setBitstreamPolicies(Bitstream bitstream, List<ResourcePolicyDTO> resourcePolicies) throws Exception {

        removeReadPolicies(bitstream, TYPE_CUSTOM);

        for (ResourcePolicyDTO policy : resourcePolicies) {

            String name = policy.getName();
            String description = policy.getDescription();
            Date startDate = policy.getStartDate();
            Date endDate = policy.getEndDate();

            for (AccessConditionOption aco : getUploadAccessConditions().values()) {
                if (aco.getName().equalsIgnoreCase(name)) {
                    aco.createResourcePolicy(context, bitstream, name, description, startDate, endDate);
                    break;
                }
            }

        }

    }

    private void removeReadPolicies(Bitstream bitstream, String type) {
        try {
            resourcePolicyService.removePolicies(context, bitstream, type, Constants.READ);
        } catch (SQLException | AuthorizeException e) {
            throw new BulkImportException(e);
        }
    }

    private Optional<Bundle> getBundleByName(Item item, String name) {
        return item.getBundles(name).stream().findFirst();
    }

    private Bundle createBundle(Item item, String bundleName) {
        try {
            return bundleService.create(context, item, bundleName);
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException(e);
        }
    }

    private void setBitstreamFormat(Bitstream bitstream) {
        try {
            BitstreamFormat bf = bitstreamFormatService.guessFormat(context, bitstream);
            bitstreamService.setFormat(context, bitstream, bf);
            bitstreamService.update(context, bitstream);
        } catch (SQLException | AuthorizeException e) {
            handler.logError(e.getMessage());
        }
    }

    private void addMetadataValues(ItemImportDTO itemImport, Item item) throws SQLException {

        for (MetadataValueDTO metadataValue : itemImport.getItem().getMetadataValues()) {
            itemService.addMetadata(context, item, metadataValue.getSchema(), metadataValue.getElement(),
                metadataValue.getQualifier(), metadataValue.getLanguage(), metadataValue.getValue(),
                metadataValue.getAuthority(), metadataValue.getConfidence());
        }

    }

    private Item searchItemById(String id) {
        return itemSearchService.search(context, id);
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
            File content = itemsS3Service.getObject(key);
            return parseZip(key, content);
        } catch (Exception ex) {
            handler.logError("An error occurs reading entry with key " + key, ex);
            errorsCount++;
            return Optional.empty();
        }

    }

    private Optional<ItemImportDTO> parseZip(String key, File data) throws Exception {

        try {
            ZipFile zipFile = parseZip(data);
            return readZipContent(key, zipFile);
        } finally {
            data.delete();
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

        if (workbookMode && !skipBitstreamsUpload) {
            uploadBitstreams(item, id, zipFile);
        }

        if (!workbookMode) {
            setBitstreamsContent(item, id, zipFile);
        }

        handler.logInfo("Entry with key " + key + " successfully read");

        String type = item.getType();
        Long currentCount = typeCounts.getOrDefault(type, 0L);
        typeCounts.put(type, currentCount + 1L);

        importedItemsCount++;

        return Optional.ofNullable(item);
    }

    private void setBitstreamsContent(ItemImportDTO item, String id, ZipFile zipFile) throws IOException {
        List<BitstreamDTO> bitstreams = item.getItem().getBitstreams();

        if (CollectionUtils.isEmpty(bitstreams)) {
            return;
        }

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().startsWith(id + File.separator + "files")) {

                String fileName = substringAfterLast(entry.getName(), File.separator);

                BitstreamDTO bitstreamDto = bitstreams.stream()
                    .filter(bitstream -> hasTitleEqualsTo(bitstream, fileName))
                    .findFirst()
                    .orElse(null);

                if (bitstreamDto != null) {
                    bitstreamDto.setContent(zipFile.getInputStream(entry));
                } else {
                    handler.logError("No content found for entry " + entry.getName());
                }
            }
        }
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
                handler.logError("Entry with id " + id + " skipped because no item type found");
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
                String fileExtension = getExtensionFromFile(zipFile.getInputStream(entry));
                if (!bitstreamName.endsWith(fileExtension)) {
                    bitstreams.stream().filter(bs -> hasTitleEqualsTo(bs, fileName))
                                  .findFirst().ifPresent(bs -> updateExtension(bs, fileExtension));


                    bitstreamName += fileExtension;
                }

                bitstreamUploadS3Service.upload(zipFile.getInputStream(entry), bitstreamName);

                handler.logInfo("Bitstream named " + bitstreamName + " uploaded with success");
            }
        }

    }

    private void updateExtension(BitstreamDTO bs, String fileExtension) {
        bs.updateLocationWithExtension(fileExtension);
        bs.getMetadataValues("dc.title").forEach(mv -> mv.setValue(mv.getValue() + fileExtension));
    }

    private String getExtensionFromFile(InputStream file) throws IOException, MimeTypeException {
        String detect = tika.detect(file);
        return mimeRepository.forName(detect).getExtension();
    }

    private String escapeBitstreamName(String name) {
        return name.replace(" ", "").replace("+", "");
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

    private Map<String, AccessConditionOption> getUploadAccessConditions() {

        if (uploadAccessConditions != null) {
            return uploadAccessConditions;
        }

        UploadConfiguration uploadConfiguration = uploadConfigurationService.getMap().get("upload");
        if (uploadConfiguration == null) {
            throw new IllegalStateException("No upload access conditions configuration found");
        }

        uploadAccessConditions = uploadConfiguration.getOptions().stream()
            .collect(Collectors.toMap(AccessConditionOption::getName, Function.identity()));

        return uploadAccessConditions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ItemsImportFromS3ScriptConfiguration<ItemsImportFromS3Script> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("items-import-from-s3",
            ItemsImportFromS3ScriptConfiguration.class);
    }

}
