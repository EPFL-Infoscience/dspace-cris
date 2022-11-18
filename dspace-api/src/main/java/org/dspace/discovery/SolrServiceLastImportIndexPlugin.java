/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import static org.dspace.content.Item.ANY;

import java.util.List;

import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.metrics.MetricsExternalServices;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link SolrServiceIndexPlugin} to index the
 * cris.lastimport.{provider} metadata fields for sorting.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class SolrServiceLastImportIndexPlugin implements SolrServiceIndexPlugin {

    @Autowired
    private List<MetricsExternalServices> metricsExternalServices;

    @Autowired
    private ItemService itemService;

    @Override
    @SuppressWarnings("rawtypes")
    public void additionalIndex(Context context, IndexableObject dso, SolrInputDocument document) {
        if (!(dso instanceof IndexableItem)) {
            return;
        }

        Item item = ((IndexableItem) dso).getIndexedObject();
        for (MetricsExternalServices metricsExternalService : metricsExternalServices) {
            String lastImportMetadataField = getLastImportMetadataField(metricsExternalService);
            String lastImportValue = getMetadataFirstValue(item, lastImportMetadataField);
            if (lastImportValue != null) {
                addLastImportSortIndex(document, lastImportMetadataField, lastImportValue);
            }
        }

    }

    private void addLastImportSortIndex(SolrInputDocument document, String lastImportField, String lastImportValue) {
        document.addField(lastImportField + "_dt", lastImportValue);
    }

    private String getMetadataFirstValue(Item item, String metadataField) {
        return itemService.getMetadataFirstValue(item, new MetadataFieldName(metadataField), ANY);

    }

    private String getLastImportMetadataField(MetricsExternalServices metricsExternalService) {
        return "cris.lastimport." + metricsExternalService.getServiceName();
    }

}
