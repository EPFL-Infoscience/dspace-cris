/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.authority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrDocument;
import org.dspace.content.Item;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.services.factory.DSpaceServicesFactory;


/**
 * 
 * Uri generator to work on nested/simple metadata
 * 
 * @author Mykhaylo Boychuk (4Science.it)
 *
 */
public class ItemUriAuthorityMetadataGenerator extends ItemSimpleAuthorityMetadataGenerator {

    @Override
    protected void buildSingleExtraByRP(SolrDocument solrDocument, Map<String, String> extras) {
        List<MetadataValueDTO> metadataValues =
                getMetadataValueDTOsFromSolr(getSchema(), getElement(), getQualifier(), solrDocument);
        if (metadataValues.isEmpty()) {
            buildSingleExtraByMetadata(null, extras);
        } else {
            buildSingleExtraByMetadata(metadataValues.get(0), extras);
        }
    }

    @Override
    protected List<MetadataValueDTO> getMetadataValueDTOsFromSolr(String schema, String element, String qualifier,
                                                                  SolrDocument solrDocument) {
        if (!projectionFieldsContain(schema, element, qualifier)) {
            return Collections.EMPTY_LIST;
        }
        String metadata = getMetadata(schema, element, qualifier);
        List<MetadataValueDTO> metadataValues =  new ArrayList<MetadataValueDTO>();
        ArrayList<String> fieldValue = (ArrayList<String>) solrDocument.getFieldValue(metadata);
        if (fieldValue != null) {
            for (String storedValue : fieldValue) {
                MetadataValueDTO dto = new MetadataValueDTO();
                dto.setSchema(schema);
                dto.setElement(element);
                dto.setQualifier(qualifier);
                dto.setValue(nullOrValue(storedValue));
                metadataValues.add(dto);
            }
        }
        return metadataValues;
    }

    private boolean projectionFieldsContain(String schema, String element, String qualifier) {
        String[] projectionFields = DSpaceServicesFactory.getInstance().getConfigurationService()
                .getArrayProperty("discovery.index.projection");
        return ArrayUtils.contains(projectionFields, getMetadata(schema, element, qualifier)) ||
                ArrayUtils.contains(projectionFields, getMetadata(schema, element, Item.ANY));
    }

    private String getMetadata(String schema, String element, String qualifier) {
        if (StringUtils.isNotBlank(qualifier)) {
            return StringUtils.join(new String[] {schema, element, qualifier}, ".");
        } else {
            return StringUtils.join(new String[] {schema, element}, ".");
        }
    }

    private String nullOrValue(String string) {
        if (StringUtils.equals(string, "null")) {
            return null;
        } else {
            return string;
        }
    }
}