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
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.core.Context;
import org.dspace.epfl.client.EpflItemsClient;
import org.dspace.epfl.script.model.ItemsImportCreationDate;
import org.dspace.epfl.script.service.ItemsImportCreationDateDao;
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
    private ConfigurationService configurationService;

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

}
