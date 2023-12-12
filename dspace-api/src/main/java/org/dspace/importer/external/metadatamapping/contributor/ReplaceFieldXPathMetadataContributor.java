
/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.metadatamapping.contributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.dspace.importer.external.metadatamapping.MetadataFieldConfig;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.dspace.util.SimpleMapConverter;
import org.jdom2.Attribute;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

/**
 * This contributor replace metadata value
 * if this matched in mapConverter-openAccesFlag.properties file
 * 
 * @author Boychuk Mykhaylo (boychuk.mykhaylo at 4science.com)
 */
public class ReplaceFieldXPathMetadataContributor extends SimpleXpathMetadatumContributor {

    private static final String UNSPECIFIED = "Unspecified";

    private SimpleMapConverter simpleMapConverter;

    @Override
    public Collection<MetadatumDTO> contributeMetadata(Element element) {
        List<MetadatumDTO> values = new LinkedList<>();

        List<Namespace> namespaces = new ArrayList<>();
        for (String ns : prefixToNamespaceMapping.keySet()) {
            namespaces.add(Namespace.getNamespace(prefixToNamespaceMapping.get(ns), ns));
        }
        XPathExpression<Object> xpath = XPathFactory.instance().compile(query, Filters.fpassthrough(), null,namespaces);
        List<Object> nodes = xpath.evaluate(element);

        MetadatumDTO metadatum = null;

        for (Object el : nodes) {
            metadatum = getMetadatum(field, extractValue(el));
            if (Objects.nonNull(metadatum)) {
                values.add(metadatum);
            }
        }

        return values;
    }

    private MetadatumDTO getMetadatum(MetadataFieldConfig field, String value) {
        String convertedValue = simpleMapConverter.getValue(value);
        if (UNSPECIFIED.equals(convertedValue)) {
            return null;
        }
        MetadatumDTO dcValue = new MetadatumDTO();
        if (Objects.isNull(field)) {
            return null;
        }
        dcValue.setValue(convertedValue);
        dcValue.setElement(field.getElement());
        dcValue.setQualifier(field.getQualifier());
        dcValue.setSchema(field.getSchema());
        return dcValue;
    }

    private String extractValue(Object el) {
        String value = ((Attribute) el).getValue();
        return StringUtils.isNotBlank(value) ? value : ((Element) el).getValue().trim();
    }

    public SimpleMapConverter getSimpleMapConverter() {
        return simpleMapConverter;
    }

    public void setSimpleMapConverter(SimpleMapConverter simpleMapConverter) {
        this.simpleMapConverter = simpleMapConverter;
    }

}