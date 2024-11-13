/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.external.provider.impl;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.external.model.ExternalDataObject;
import org.dspace.external.provider.AbstractExternalDataProvider;
import org.dspace.importer.external.crossref.CrossRefImportMetadataSourceServiceImpl;
import org.dspace.importer.external.datamodel.ImportRecord;
import org.dspace.importer.external.exception.MetadataSourceException;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.dspace.importer.external.service.components.QuerySource;
import org.dspace.scripts.handler.DSpaceRunnableHandler;

/**
 * This class allows to configure a Live Import Provider as an External Data Provider
 * 
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 *
 */
public class LiveImportDataProvider extends AbstractExternalDataProvider {

    private static final Logger log = LogManager.getLogger(LiveImportDataProvider.class);

    /**
     * The {@link QuerySource} live import provider
     */
    private QuerySource querySource;

    private DSpaceRunnableHandler handler;

    /**
     * An unique human readable identifier for this provider
     */
    private String sourceIdentifier;

    private String recordIdMetadata;

    private String displayMetadata = "dc.title";

    @Override
    public String getSourceIdentifier() {
        return sourceIdentifier;
    }

    public QuerySource getQuerySource() {
        return querySource;
    }

    public void setQuerySource(QuerySource querySource) {
        this.querySource = querySource;
    }

    /**
     * This method set the SourceIdentifier for the ExternalDataProvider
     * @param sourceIdentifier   The UNIQUE sourceIdentifier to be set on any LiveImport data provider
     */
    public void setSourceIdentifier(String sourceIdentifier) {
        this.sourceIdentifier = sourceIdentifier;
    }

    /**
     * This method set the MetadataSource for the ExternalDataProvider
     * @param querySource Source {@link org.dspace.importer.external.service.components.QuerySource} implementation used to process the input data
     */
    public void setMetadataSource(QuerySource querySource) {
        this.querySource = querySource;
    }

    /**
     * This method set dublin core identifier to use as metadata id
     * @param recordIdMetadata dublin core identifier to use as metadata id
     */
    public void setRecordIdMetadata(String recordIdMetadata) {
        this.recordIdMetadata = recordIdMetadata;
    }

    /**
     * This method set the dublin core identifier to display the title
     * @param displayMetadata metadata to use as title
     */
    public void setDisplayMetadata(String displayMetadata) {
        this.displayMetadata = displayMetadata;
    }

    /**
     * This method set the handler to properly display logs in process output
     * @param handler DspaceRunnableHandler instance
     */
    public void setHandler(DSpaceRunnableHandler handler) {
        this.handler = handler;
    }

    @Override
    public Optional<ExternalDataObject> getExternalDataObject(String id) {
        try {
            logInfo("Getting record by id: " + getActualQuery(id));
            return Optional.ofNullable(getExternalDataObject(querySource.getRecord(id)));
        } catch (MetadataSourceException e) {
            throw new RuntimeException(
                    "The live import provider " + querySource.getImportSource() + " throws an exception", e);
        }
    }

    @Override
    public List<ExternalDataObject> searchExternalDataObjects(String query, int start, int limit) {
        try {
            logInfo("Getting records from " + start + " to " + (start + limit - 1) +
                    " by query: " + getActualQuery(query));
            List<ExternalDataObject> result = querySource.getRecords(query, start, limit).stream()
                    .map(this::getExternalDataObject)
                    .collect(Collectors.toList());
            logInfo("...retrieved " + result.size() + " records");
            return result;
        } catch (MetadataSourceException e) {
            throw new RuntimeException(
                    "The live import provider " + querySource.getImportSource() + " throws an exception", e);
        }
    }

    @Override
    public boolean supports(String source) {
        return StringUtils.equalsIgnoreCase(sourceIdentifier, source);
    }

    @Override
    public int getNumberOfResults(String query) {
        try {
            logInfo("Getting number of records by query: " + getActualQuery(query));
            return querySource.getRecordsCount(query);
        } catch (MetadataSourceException e) {
            throw new RuntimeException(
                    "The live import provider " + querySource.getImportSource() + " throws an exception", e);
        }
    }

    /**
     * Internal method to convert an ImportRecord to an ExternalDataObject
     * 
     * FIXME it would be useful to remove ImportRecord at all in favor of the
     * ExternalDataObject
     * 
     * @param record
     * @return
     */
    private ExternalDataObject getExternalDataObject(ImportRecord record) {
        if (Objects.isNull(record)) {
            return null;
        }
        ExternalDataObject externalDataObject = new ExternalDataObject(sourceIdentifier);
        String id = getFirstValue(record, recordIdMetadata);
        String display = getFirstValue(record, displayMetadata);
        externalDataObject.setId(id);
        externalDataObject.setDisplayValue(display);
        externalDataObject.setValue(display);
        for (MetadatumDTO dto : record.getValueList()) {
            // FIXME it would be useful to remove MetadatumDTO in favor of MetadataValueDTO
            MetadataValueDTO mvDTO = new MetadataValueDTO();
            mvDTO.setSchema(dto.getSchema());
            mvDTO.setElement(dto.getElement());
            mvDTO.setQualifier(dto.getQualifier());
            mvDTO.setValue(dto.getValue());
            externalDataObject.addMetadata(mvDTO);
        }
        return externalDataObject;
    }

    private String getFirstValue(ImportRecord record, String metadata) {
        String id = null;
        String[] split = StringUtils.split(metadata, ".", 3);
        Collection<MetadatumDTO> values = record.getValue(split[0], split[1], split.length == 3 ? split[2] : null);
        if (!values.isEmpty()) {
            id = (values.iterator().next().getValue());
        }
        return id;
    }

    private String getActualQuery(String query) {
        // in current implementation of CrossRefImportMetadataSourceServiceImpl
        // query can consist of orcid id and search query itself (i.e. "0000-0000-1234-1234 text")
        // and if orcid id is present then search is performed only by orcid id
        if (querySource instanceof CrossRefImportMetadataSourceServiceImpl) {
            CrossRefImportMetadataSourceServiceImpl crossRefSourceService =
                    ((CrossRefImportMetadataSourceServiceImpl) querySource);
            return crossRefSourceService.getSelector(query);
        }
        return query;
    }

    private void logInfo(String info) {
        if (this.handler != null) {
            this.handler.logInfo(info);
        } else {
            log.info(info);
        }
    }

}
