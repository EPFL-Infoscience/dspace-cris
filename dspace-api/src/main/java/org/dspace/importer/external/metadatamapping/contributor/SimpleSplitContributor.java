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
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

public class SimpleSplitContributor extends SimpleXpathMetadatumContributor {
    private final static Logger log = LogManager.getLogger();
    private String delimiter;

    public SimpleSplitContributor(String delimiter) {
        this.delimiter = delimiter;
    }

    @Override
    public Collection<MetadatumDTO> contributeMetadata(Element t) {
        List<MetadatumDTO> values = new LinkedList<>();

        List<Namespace> namespaces = new ArrayList<>();
        for (String ns : prefixToNamespaceMapping.keySet()) {
            namespaces.add(Namespace.getNamespace(prefixToNamespaceMapping.get(ns), ns));
        }
        XPathExpression<Object> xpath = XPathFactory.instance().compile(query, Filters.fpassthrough(), null,namespaces);
        List<Object> nodes = xpath.evaluate(t);
        for (Object el : nodes) {
            if (el instanceof Element) {
                String value = extractValue(el);
                if (StringUtils.isNotBlank(value)) {
                    String[] tokens = value.split(Pattern.quote(delimiter));
                    for (String token : tokens) {
                        values.add(metadataFieldMapping.toDCValue(field, token.trim()));
                    }
                }
            } else {
                log.error("Encountered unsupported XML node of type: {}. Skipped that node.", el.getClass());
            }
        }
        return values;
    }
        
    private String extractValue(Object el) {
        String value = ((Element) el).getText();
        return StringUtils.isNotBlank(value) ? value : ((Element) el).getValue().trim();
    }

    // Getter and setter for delimiter property
    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }
}
