/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

public class SolrServiceCaseSensitiveIndexPlugin implements SolrServiceIndexPlugin {

    @Autowired
    private ConfigurationService configurationService;

    @Override
    public void additionalIndex(Context context, IndexableObject dso, SolrInputDocument document) {

        if (!(dso.getIndexedObject() instanceof Item)) {
            return;
        }

        Item item = (Item) dso.getIndexedObject();

        Set<String> caseSensitiveMetadataFields = getCaseSensitiveMetadataFields();

        for (MetadataValue metadataValue : item.getMetadata()) {
            String metadataField = metadataValue.getMetadataField().toString('.');
            if (caseSensitiveMetadataFields.contains(metadataField)) {
                document.addField(metadataField + "_cs", metadataValue.getValue());
            }
        }

    }

    private Set<String> getCaseSensitiveMetadataFields() {
        String[] arrayProperty = configurationService.getArrayProperty("epfl.discovery.case-sensitive-metadata-fields");
        return new HashSet<String>(Arrays.asList(arrayProperty));
    }

}
