/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.reader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportPageReader implements ItemsImportMetadataFieldReader {

    @Autowired
    private ConfigurationService configurationService;

    private String endPageMetadataField;

    private String articleNumberMetadataField;

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            String value = node.getTextContent();

            if (StringUtils.isBlank(value)) {
                continue;
            }

            String[] pages = value.contains("–") ? value.split("–") : value.split("-");

            if (pages.length == 1 && isJournalArticle(type) && value.length() > 3) {
                metadataValues.add(new MetadataValueDTO(articleNumberMetadataField, value));
            } else if (pages.length > 1) {
                metadataValues.add(new MetadataValueDTO(metadataField, pages[0].trim()));
                metadataValues.add(new MetadataValueDTO(endPageMetadataField, pages[1].trim()));
            } else {
                metadataValues.add(new MetadataValueDTO(metadataField, pages[0].trim()));
            }


        }

        return metadataValues;
    }

    private boolean isJournalArticle(String type) {
        return Arrays.asList(configurationService.getArrayProperty("epfl.items-import.journal-or-is-part-of.types"))
            .contains(type);
    }

    @Override
    public String getReaderName() {
        return "page";
    }

    public String getEndPageMetadataField() {
        return endPageMetadataField;
    }

    public void setEndPageMetadataField(String endPageMetadataField) {
        this.endPageMetadataField = endPageMetadataField;
    }

    public String getArticleNumberMetadataField() {
        return articleNumberMetadataField;
    }

    public void setArticleNumberMetadataField(String articleNumberMetadataField) {
        this.articleNumberMetadataField = articleNumberMetadataField;
    }

}
