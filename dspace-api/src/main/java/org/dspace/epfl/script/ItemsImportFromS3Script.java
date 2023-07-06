/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

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

    private List<String> keys = new ArrayList<>();

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

        String configuration = configurationService.getProperty("epfl.items-import.mapping-configuration.path");

        this.mapping = marcXmlParser.parseMapping(configuration);

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

        ItemDTO item = null;

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().equals(id + File.separator + "metadata.xml")) {
                item = marcXmlParser.readSingleItem(context, id, zipFile.getInputStream(entry), mapping);
            } else if (entry.getName().startsWith(id + File.separator + "files")) {
                String bitstreamName = id + "_" + substringAfterLast(entry.getName(), File.separator);
                bitstreamUploadS3Service.upload(zipFile.getInputStream(entry), bitstreamName);
                handler.logInfo("Bitstream named " + bitstreamName + " uploaded with success");
            }
        }

        if (item == null || CollectionUtils.isEmpty(item.getMetadataValues())) {
            throw new IllegalStateException("No metadata read from entry with key " + key);
        }

        handler.logInfo("Entry with key " + key + " successfully read");

        importedItemsCount++;

        return item;
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
