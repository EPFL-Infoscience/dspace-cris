/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.metadatamapping.contributor;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.dspace.importer.external.metadatamapping.MetadataFieldConfig;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.jdom2.Element;
import org.jdom2.Namespace;

public class ReplaceStringXPathMetadataContributor extends SimpleXpathMetadatumContributor {

    private String stringToBeReplaced;

    private String stringToReplaceWith;

    @Override
    public Collection<MetadatumDTO> contributeMetadata(Element element) {
        List<MetadatumDTO> values = new LinkedList<>();
        for (String ns : prefixToNamespaceMapping.keySet()) {
            for (Element el : element.getChildren(query, Namespace.getNamespace(ns))) {
                values.add(getMetadata(field, el.getValue()));
            }
        }
        return values;
    }

    private MetadatumDTO getMetadata(MetadataFieldConfig field, String value) {
        if (Objects.isNull(field)) {
            return null;
        }
        MetadatumDTO dcValue = new MetadatumDTO();
        dcValue.setValue(value == null ? null : value.replace(stringToBeReplaced, stringToReplaceWith));
        dcValue.setElement(field.getElement());
        dcValue.setQualifier(field.getQualifier());
        dcValue.setSchema(field.getSchema());
        return dcValue;
    }

    public void setStringToBeReplaced(String stringToBeReplaced) {
        this.stringToBeReplaced = stringToBeReplaced;
    }

    public void setStringToReplaceWith(String stringToReplaceWith) {
        this.stringToReplaceWith = stringToReplaceWith;
    }
}
