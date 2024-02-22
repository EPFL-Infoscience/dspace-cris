/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service.impl;

import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;
import static org.apache.commons.lang3.StringUtils.removeStart;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.TimeZone;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.dspace.epfl.client.EpflItemsClient;
import org.dspace.epfl.script.model.ItemImportDTO;
import org.dspace.epfl.script.model.ItemsImportCreationDate;
import org.dspace.epfl.script.model.ItemsImportModificationDate;
import org.dspace.epfl.script.service.ItemsImportCreationDateDao;
import org.dspace.epfl.script.service.ItemsImportModificationDateDao;
import org.dspace.epfl.script.service.ItemsS3Service;
import org.dspace.scripts.handler.DSpaceRunnableHandler;
import org.dspace.services.ConfigurationService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

public class ItemsS3ServiceImpl implements ItemsS3Service {

    @Autowired
    private EpflItemsClient itemsClient;

    @Autowired
    private ItemsImportCreationDateDao itemsImportCreationDateDao;

    @Autowired
    private ItemsImportModificationDateDao itemsImportModificationDateDao;

    @Autowired
    private ConfigurationService configurationService;

    private DateFormat dateFormat;

    public ItemsS3ServiceImpl() {
        dateFormat = new SimpleDateFormat("yyyyMMddHHmmss.S");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public Stream<String> getAllItemsKeys() {
        return streamOf(itemsClient.iterateObjects())
            .map(S3ObjectSummary::getKey);
    }

    @Override
    public Stream<String> getItemsKeys(Integer limit, String startAfter) {
        return itemsClient.getObjects(limit, startAfter).stream()
            .map(S3ObjectSummary::getKey);
    }

    @Override
    public File getObject(String key) {
        return itemsClient.get(key);
    }

    @Override
    public String getCreationDate(Context context, String id) {
        String creationDate = itemsImportCreationDateDao.findCreationDate(context, id);
        if (StringUtils.isBlank(creationDate)) {
            return itemsClient.getCreationDate(id);
        } else {
            return creationDate;
        }
    }

    @Override
    public Integer importCreationDates(Context context, InputStream is, DSpaceRunnableHandler handler)
        throws Exception {

        int count = 0;

        File creationDateZip = File.createTempFile("creation_date", "zip");

        IOUtils.copy(is, new FileOutputStream(creationDateZip));

        try (ZipFile zipFile = new ZipFile(creationDateZip)) {

            String creationDateDirectory = getCreationDateDirectory();
            String creationDateField = getCreationDateField();

            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".json")) {
                    continue;
                }

                JSONArray json = parseJson(zipFile.getInputStream(entry));

                String id = StringUtils.removeEnd(removeStart(name, creationDateDirectory + File.separator), ".json");
                String creationDate = ((JSONObject) json.get(0)).getString(creationDateField);

                createOrUpdate(context, id, creationDate);

                count++;

                if (count % 10000 == 0) {
                    context.commit();
                    handler.logInfo("Stored " + count + " creation dates");
                }
            }

        } finally {
            creationDateZip.delete();
        }

        context.commit();

        return count;
    }

    @Override
    public Integer importModificationDates(Context context, Iterator<ItemImportDTO> items,
            DSpaceRunnableHandler handler) throws SQLException {
        int count = 0;
        while (items.hasNext()) {
            ItemImportDTO item = items.next();
            createOrUpdateModificationDate(context, item, handler);
            count++;
            if (count % 100 == 0) {
                context.commit();
                handler.logInfo("Stored " + count + " modification dates");
            }
        }
        context.commit();
        return count;
    }

    @Override
    public ItemsImportModificationDate createOrUpdateModificationDate(Context context, ItemImportDTO item,
            DSpaceRunnableHandler handler) {
        // this metadata is required, so it is ok to throw a runtime exception NPE or
        // array out
        String id = item.getItem().getMetadataValues("cris.legacyId").get(0).getValue();

        String modificationDate = null;
        List<MetadataValueDTO> modifiedMetadataValues = item.getItem().getMetadataValues("dc.date.modified");
        if (modifiedMetadataValues != null
                && modifiedMetadataValues.size() > 0) {
            modificationDate = modifiedMetadataValues.get(0).getValue();
            try {
                Date date = dateFormat.parse(modificationDate);
                modificationDate = ZonedDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_INSTANT);
            } catch (ParseException e) {
                handler.logError("Modification date has a wrong format " + modificationDate + " for the record " + id
                        + " . It has been discarded and replaced by the current time");
                modificationDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
            }
        } else {
            handler.logWarning("Modification date is missing for " + id + " using current time");
            modificationDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        }
        ItemsImportModificationDate date = itemsImportModificationDateDao.find(context, id);
        if (date == null) {
            date = new ItemsImportModificationDate();
            date.setModificationDate(modificationDate);
            date.setId(id);
            try {
                date = itemsImportModificationDateDao.create(context, date);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            date.setModificationDate(modificationDate);
        }
        return date;
    }

    private ItemsImportCreationDate createOrUpdate(Context context, String id, String creationDate) {
        ItemsImportCreationDate date = itemsImportCreationDateDao.find(context, id);
        if (date == null) {
            date = new ItemsImportCreationDate();
            date.setCreationDate(creationDate);
            date.setId(id);
            try {
                date = itemsImportCreationDateDao.create(context, date);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            date.setCreationDate(creationDate);
        }
        return date;
    }

    private String getCreationDateDirectory() {
        return configurationService.getProperty("epfl.items-import.creation-date-aws.directory");
    }

    private String getCreationDateField() {
        return configurationService.getProperty("epfl.items-import.creation-date-aws.field");
    }

    private JSONArray parseJson(InputStream inputStream) {
        try {
            return new JSONArray(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
        } catch (JSONException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> Stream<T> streamOf(Iterator<T> iterator) {
        return stream(spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    @Override
    public String getModificationDate(Context context, String id) {
        return itemsImportModificationDateDao.findModificationDate(context, id);
    }

    @Override
    public void deleteModificationDate(Context context, String id) {
        ItemsImportModificationDate find = itemsImportModificationDateDao.find(context, id);
        if (find != null) {
            try {
                itemsImportModificationDateDao.delete(context, find);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
