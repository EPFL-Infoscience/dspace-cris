package org.dspace.importer.external.metadatamapping.contributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.importer.external.metadatamapping.MetadataFieldConfig;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;

/**
 * A MetadataContributor implementation that extracts page range metadata from a
 * JSON object using JSONPath.
 * It extends the SimpleJsonPathMetadataContributor class.
 */
public class PageRangeJsonPathMetadataContributor extends SimpleJsonPathMetadataContributor {

    private static final Logger log = LogManager.getLogger();

    private MetadataFieldConfig startPageMetadata;
    private MetadataFieldConfig endPageMetadata;

    /**
     * Retrieve the metadata associated with the given JSON object.
     *
     * @param fullJson The JSON object from which to retrieve metadata.
     * @return A collection of import records. Only the start page and end page
     *         metadata are included in the record.
     */
    @Override
    public Collection<MetadatumDTO> contributeMetadata(String fullJson) {
        List<MetadatumDTO> values = new LinkedList<>();
        List<MetadatumDTO> metadatums = null;
        // Extract page range from JSON
        String pageRange = extractPageRangeFromJson(fullJson);
        metadatums = getMetadatum(pageRange);
        if (Objects.nonNull(metadatums)) {
            for (MetadatumDTO metadatum : metadatums) {
                values.add(metadatum);
            }
        }
        return values;
    }

    private  List<MetadatumDTO> getMetadatum(String value) {
        List<MetadatumDTO> metadatums = new ArrayList<MetadatumDTO>();
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String [] range = value.split("-");
        if (range.length == 2) {
            metadatums.add(setStartPage(range));
            metadatums.add(setEndPage(range));
        } else if (range.length != 0) {
            metadatums.add(setStartPage(range));
        }
        return metadatums;
    }

    private MetadatumDTO setEndPage(String[] range) {
        MetadatumDTO endPage = new MetadatumDTO();
        endPage.setValue(range[1]);
        endPage.setElement(endPageMetadata.getElement());
        endPage.setQualifier(endPageMetadata.getQualifier());
        endPage.setSchema(endPageMetadata.getSchema());
        return endPage;
    }

    private MetadatumDTO setStartPage(String[] range) {
        MetadatumDTO startPage = new MetadatumDTO();
        startPage.setValue(range[0]);
        startPage.setElement(startPageMetadata.getElement());
        startPage.setQualifier(startPageMetadata.getQualifier());
        startPage.setSchema(startPageMetadata.getSchema());
        return startPage;
    }

    /**
     * Extracts the page range from the JSON object.
     *
     * @param json The JSON object.
     * @return The page range extracted from the JSON object.
     */
    private String extractPageRangeFromJson(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readTree(json);
            JsonNode pageNode = rootNode.path(query);
            String pageRange = pageNode.asText();
            return pageRange;
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON: {}", e.getMessage());
        }
        return null;
    }

    // Setters for startPageMetadata and endPageMetadata
    public void setStartPageMetadata(MetadataFieldConfig startPageMetadata) {
        this.startPageMetadata = startPageMetadata;
    }

    public void setEndPageMetadata(MetadataFieldConfig endPageMetadata) {
        this.endPageMetadata = endPageMetadata;
    }
}
