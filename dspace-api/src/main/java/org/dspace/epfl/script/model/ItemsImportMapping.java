/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.model;

import static org.dspace.epfl.script.reader.ItemsImportSimpleReader.DEFAULT_METADATAFIELDS_READER;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang3.StringUtils;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "mapping")
public class ItemsImportMapping {

    @XmlElement(name = "item-xpath")
    private String itemXPath;

    @XmlElement(name = "submitter-xpath")
    private String submitterXPath;

    @XmlElement(name = "metadata-fields")
    private MetadataFields metadataFields;

    public String getItemXPath() {
        return itemXPath;
    }

    public void setItemXPath(String itemXPath) {
        this.itemXPath = itemXPath;
    }

    public String getSubmitterXPath() {
        return submitterXPath;
    }

    public void setSubmitterXPath(String submitterXPath) {
        this.submitterXPath = submitterXPath;
    }

    public MetadataFields getMetadataFields() {
        if (metadataFields == null) {
            metadataFields = new MetadataFields();
        }
        return metadataFields;
    }

    public void setMetadataFields(MetadataFields metadataFields) {
        this.metadataFields = metadataFields;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class MetadataFields {

        @XmlElement(name = "metadata-field")
        private List<MetadataField> metadataFields;

        public List<MetadataField> getMetadataFields() {
            if (metadataFields == null) {
                metadataFields = new ArrayList<ItemsImportMapping.MetadataField>();
            }
            return metadataFields;
        }

        public void setMetadataFields(List<MetadataField> metadataFields) {
            this.metadataFields = metadataFields;
        }

    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class MetadataField {

        private String field;

        private String reader;

        private String xpath;

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getXPath() {
            return xpath;
        }

        public void setXPath(String xpath) {
            this.xpath = xpath;
        }

        public String getReader() {
            return StringUtils.isNotBlank(reader) ? reader : DEFAULT_METADATAFIELDS_READER;
        }

        public void setReader(String reader) {
            this.reader = reader;
        }

    }

}
