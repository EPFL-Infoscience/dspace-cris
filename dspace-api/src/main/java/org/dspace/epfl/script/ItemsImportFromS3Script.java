/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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
import org.dspace.epfl.script.service.ItemsS3Service;
import org.dspace.epfl.script.service.MarcXmlParser;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;
import org.w3c.dom.Node;

public class ItemsImportFromS3Script
    extends DSpaceRunnable<ItemsImportFromS3ScriptConfiguration<ItemsImportFromS3Script>> {


    private CollectionService collectionService;

    private BulkImportWorkbookBuilder workbookBuilder;

    private ItemsS3Service itemsS3Service;

    private MarcXmlParser marcXmlParser;


    private Context context;

    private String collectionId;

    private Integer limit;

    private String startAfter;

    private List<String> keys = new ArrayList<>();

    private int importedItemsCount = 0;

    private int errorsCount = 0;


    @Override
    public void setup() throws ParseException {

        this.collectionService = ContentServiceFactory.getInstance().getCollectionService();
        this.workbookBuilder = new DSpace().getServiceManager()
            .getServicesByType(BulkImportWorkbookBuilder.class).get(0);
        this.itemsS3Service = new DSpace().getServiceManager()
            .getServicesByType(ItemsS3Service.class).get(0);
        this.marcXmlParser = new DSpace().getServiceManager()
            .getServicesByType(MarcXmlParser.class).get(0);


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

        String submitter = null;

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().equals(id + File.separator + "metadata.xml")) {

                Node record = marcXmlParser.parse(zipFile.getInputStream(entry));

                printDocument(record, System.out);

                metadataValues.addAll(marcXmlParser.readMetadataValues(context, record));

                submitter = marcXmlParser.readSubmitter(context, record);

            }
        }

        if (metadataValues.isEmpty()) {
            throw new IllegalStateException("No metadata read from entry with key " + key);
        }

        System.out.println("---------------------------------");

        metadataValues.forEach(value -> System.out.println(value));

        System.out.println("---------------------------------");
        System.out.println("---------------------------------");

        String legacyId = getCrisLegacyId(metadataValues)
            .orElse(id);

        importedItemsCount++;

        return new ItemDTO("LEGACY-ID::" + legacyId, submitter, metadataValues, bitstreams);
    }

    private Optional<String> getCrisLegacyId(List<MetadataValueDTO> metadataValues) {
        return metadataValues.stream()
            .filter(metadata -> metadata.getMetadataField().equals("cris.legacyId"))
            .map(MetadataValueDTO::getValue)
            .findFirst();
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

    @Override
    @SuppressWarnings("unchecked")
    public ItemsImportFromS3ScriptConfiguration<ItemsImportFromS3Script> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("items-import-from-s3",
            ItemsImportFromS3ScriptConfiguration.class);
    }

}
