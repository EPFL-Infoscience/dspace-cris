/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.bulkimport.service;

import static org.apache.commons.lang3.StringUtils.split;
import static org.dspace.app.bulkedit.BulkImport.BITSTREAMS_SHEET_NAME;
import static org.dspace.app.bulkedit.BulkImport.DISCOVERABLE_HEADER;
import static org.dspace.app.bulkedit.BulkImport.ID_HEADER;
import static org.dspace.app.bulkedit.BulkImport.LANGUAGE_SEPARATOR_PREFIX;
import static org.dspace.app.bulkedit.BulkImport.LANGUAGE_SEPARATOR_SUFFIX;
import static org.dspace.app.bulkedit.BulkImport.METADATA_ATTRIBUTES_SEPARATOR;
import static org.dspace.app.bulkedit.BulkImport.METADATA_SEPARATOR;
import static org.dspace.app.bulkedit.BulkImport.PARENT_ID_HEADER;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

import com.google.common.collect.Iterators;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dspace.app.bulkedit.BulkImport;
import org.dspace.app.bulkimport.converter.EntityRowConverter;
import org.dspace.app.bulkimport.model.BulkImportSheet;
import org.dspace.app.bulkimport.model.BulkImportWorkbook;
import org.dspace.app.bulkimport.model.EntityRow;
import org.dspace.app.bulkimport.model.MetadataGroup;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.content.Collection;
import org.dspace.content.vo.MetadataValueVO;
import org.dspace.core.Context;
import org.dspace.core.CrisConstants;

/**
 * Implementation of {@link BulkImportWorkbookBuilder}.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4Science)
 *
 */
public class BulkImportWorkbookBuilderImpl implements BulkImportWorkbookBuilder {

    private DCInputsReader reader;

    @PostConstruct
    private void postConstruct() {
        try {
            this.setReader(new DCInputsReader());
        } catch (DCInputsReaderException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> Workbook build(Context context, Collection collection, Iterator<T> sources,
        EntityRowConverter<T> converter) {

        Iterator<EntityRow> entityRowIterator = Iterators.transform(sources,
            source -> converter.convert(context, collection, source));

        return build(context, collection, entityRowIterator);
    }

    @Override
    public Workbook build(Context context, Collection collection, Iterator<EntityRow> entities) {

        try (Workbook workbook = new XSSFWorkbook()) {

            BulkImportSheet mainSheet = writeMainSheetHeader(context, collection, workbook);
            List<BulkImportSheet> nestedMetadataSheets = writeNestedMetadataSheetsHeader(collection, workbook);
            BulkImportSheet bitstreamSheet = writeBitstreamSheetHeader(collection, workbook);

            BulkImportWorkbook bulkImportWorkbook = new BulkImportWorkbook(mainSheet,
                nestedMetadataSheets, bitstreamSheet);

            writeWorkbookContent(context, entities, bulkImportWorkbook);

            autoSizeColumns(bulkImportWorkbook.getAllSheets());

            return workbook;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private BulkImportSheet writeMainSheetHeader(Context context, Collection collection, Workbook workbook) {
        BulkImportSheet mainSheet = new BulkImportSheet(workbook, "items", false, collection);
        mainSheet.appendHeader(ID_HEADER);
        mainSheet.appendHeader(DISCOVERABLE_HEADER);
        List<String> metadataFields = getSubmissionFormMetadata(collection);
        for (String metadataField : metadataFields) {
            mainSheet.appendHeaderIfNotPresent(metadataField);
        }
        return mainSheet;
    }

    private List<BulkImportSheet> writeNestedMetadataSheetsHeader(Collection collection, Workbook workbook) {
        return getSubmissionFormMetadataGroups(collection).stream()
            .map(metadataGroup -> writeNestedMetadataSheetHeader(collection, workbook, metadataGroup))
            .collect(Collectors.toList());
    }

    private BulkImportSheet writeNestedMetadataSheetHeader(Collection collection, Workbook workbook, String field) {
        BulkImportSheet nestedMetadataSheet = new BulkImportSheet(workbook, field, true, collection);
        List<String> nestedMetadataFields = getSubmissionFormMetadataGroup(collection, field);
        nestedMetadataSheet.appendHeader(PARENT_ID_HEADER);
        for (String metadataField : nestedMetadataFields) {
            nestedMetadataSheet.appendHeader(metadataField);
        }
        return nestedMetadataSheet;
    }

    private BulkImportSheet writeBitstreamSheetHeader(Collection collection, Workbook workbook) {
        BulkImportSheet bitstreamSheet = new BulkImportSheet(workbook, BITSTREAMS_SHEET_NAME, true, collection);

        for (String bitstreamSheetHeader : BulkImport.BITSTREAMS_SHEET_HEADERS) {
            bitstreamSheet.appendHeader(bitstreamSheetHeader);
        }

        List<String> metadataFields = getUploadMetadaFields(collection);
        for (String metadataField : metadataFields) {
            bitstreamSheet.appendHeaderIfNotPresent(metadataField);
        }

        return bitstreamSheet;
    }

    private void writeWorkbookContent(Context context, Iterator<EntityRow> entities, BulkImportWorkbook workbook) {

        while (entities.hasNext()) {

            EntityRow entity = entities.next();

            writeMainSheet(context, entity, workbook.getMainSheet());
            writeNestedMetadataSheets(context, entity, workbook);
            writeBitstreamSheet(context, entity, workbook.getBitstreamSheet());

        }

    }

    private void writeMainSheet(Context context, EntityRow entity, BulkImportSheet mainSheet) {

        mainSheet.appendRow();

        List<String> headers = mainSheet.getHeaders();
        for (String header : headers) {

            if (header.equals(ID_HEADER)) {
                mainSheet.setValueOnLastRow(header, entity.getId().toString());
                continue;
            }

            if (header.equals(DISCOVERABLE_HEADER)) {
                mainSheet.setValueOnLastRow(header, entity.getDiscoverableValue());
                continue;
            }

            entity.getMetadata(header).forEach(value -> writeMetadataValue(mainSheet, header, value));
        }

    }

    private void writeNestedMetadataSheets(Context context, EntityRow entity, BulkImportWorkbook workbook) {

        for (MetadataGroup metadataGroup : entity.getMetadataGroups()) {

            String metadataField = metadataGroup.getName();

            BulkImportSheet nestedSheet = workbook.getNestedMetadataSheetByName(metadataField)
                .orElseThrow(() -> new IllegalArgumentException("The entity " + entity.getId() +
                    " has a metadata field group " + metadataField + " not supported for the given collection"));

            writeNestedMetadataSheet(context, metadataGroup, nestedSheet);

        }

    }

    private void writeNestedMetadataSheet(Context context, MetadataGroup metadataGroup, BulkImportSheet nestedSheet) {

        nestedSheet.appendRow();

        Map<String, MetadataValueVO> metadata = metadataGroup.getMetadata();

        for (String metadataField : metadata.keySet()) {
            writeMetadataValue(nestedSheet, metadataField, metadata.get(metadataField));
        }

    }

    private void writeBitstreamSheet(Context context, EntityRow entity, BulkImportSheet bitstreamSheet) {
        // TODO Auto-generated method stub

    }

    private void writeMetadataValue(BulkImportSheet sheet, String header, MetadataValueVO metadataValue) {

        String language = getMetadataLanguage(header);
        if (StringUtils.isBlank(language)) {
            sheet.appendValueOnLastRow(header, formatMetadataValue(metadataValue), METADATA_SEPARATOR);
            return;
        }

        if (isLanguageSupported(sheet.getCollection(), language, header, sheet.isNestedMetadata())) {
            String headerWithLanguage = header + LANGUAGE_SEPARATOR_PREFIX + language + LANGUAGE_SEPARATOR_SUFFIX;
            sheet.appendHeaderIfNotPresent(headerWithLanguage);
            sheet.appendValueOnLastRow(headerWithLanguage, formatMetadataValue(metadataValue), METADATA_SEPARATOR);
        }

    }

    private String getMetadataLanguage(String field) {
        if (field.contains(LANGUAGE_SEPARATOR_PREFIX)) {
            return split(field, LANGUAGE_SEPARATOR_PREFIX)[1].replace(LANGUAGE_SEPARATOR_SUFFIX, "");
        }
        return null;
    }

    private String formatMetadataValue(MetadataValueVO metadata) {

        String value = metadata.getValue();

        String authority = metadata.getAuthority();
        int confidence = metadata.getConfidence();
        Integer securityLevel = metadata.getSecurityLevel();

        String formattedValue = CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE.equals(value) ? "" : value;

        if (StringUtils.isNotBlank(authority)) {
            formattedValue += METADATA_ATTRIBUTES_SEPARATOR + authority + METADATA_ATTRIBUTES_SEPARATOR + confidence;
        }

        if (securityLevel != null) {
            formattedValue += METADATA_ATTRIBUTES_SEPARATOR + BulkImport.SECURITY_LEVEL_PREFIX + securityLevel;
        }

        return formattedValue;
    }

    private List<String> getSubmissionFormMetadataGroup(Collection collection, String groupName) {
        try {
            return this.reader.getAllNestedMetadataByGroupName(collection, groupName);
        } catch (DCInputsReaderException e) {
            throw new RuntimeException("An error occurs reading the input configuration "
                + "by group name " + groupName, e);
        }
    }

    private List<String> getSubmissionFormMetadata(Collection collection) {
        try {
            return this.reader.getSubmissionFormMetadata(collection);
        } catch (DCInputsReaderException e) {
            throw new RuntimeException("An error occurs reading the input configuration by collection", e);
        }
    }

    private List<String> getSubmissionFormMetadataGroups(Collection collection) {
        try {
            return this.reader.getSubmissionFormMetadataGroups(collection);
        } catch (DCInputsReaderException e) {
            throw new RuntimeException("An error occurs reading the input configuration by collection", e);
        }
    }

    private boolean isLanguageSupported(Collection collection, String language, String metadataField, boolean group) {
        try {
            List<String> languages = this.reader.getLanguagesForMetadata(collection, metadataField, group);
            return CollectionUtils.isNotEmpty(languages) ? languages.contains(language) : false;
        } catch (DCInputsReaderException e) {
            throw new RuntimeException("An error occurs reading the input configuration by collection", e);
        }
    }

    private List<String> getUploadMetadaFields(Collection collection) {
        List<String> metadataFields;
        try {
            metadataFields = reader.getUploadMetadataFieldsFromCollection(collection);
        } catch (DCInputsReaderException e) {
            throw new RuntimeException(e);
        }
        return metadataFields;
    }

    private void autoSizeColumns(List<BulkImportSheet> sheets) {
        sheets.forEach(sheet -> autoSizeColumns(sheet.getSheet()));
    }

    private void autoSizeColumns(Sheet sheet) {
        if (sheet.getPhysicalNumberOfRows() > 0) {
            Row row = sheet.getRow(sheet.getFirstRowNum());
            for (Cell cell : row) {
                int columnIndex = cell.getColumnIndex();
                sheet.autoSizeColumn(columnIndex);
            }
        }
    }

    public void setReader(DCInputsReader reader) {
        this.reader = reader;
    }

}
