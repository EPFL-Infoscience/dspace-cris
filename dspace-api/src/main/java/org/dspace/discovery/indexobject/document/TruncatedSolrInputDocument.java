/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery.indexobject.document;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

public class TruncatedSolrInputDocument extends SolrInputDocument {
    private static final int FIELD_MAX_LENGTH = 32764;

    private ConfigurationService configurationService
            = DSpaceServicesFactory.getInstance().getConfigurationService();

    @Override
    public void addField(String name, Object value) {
        if (configurationService.getBooleanProperty("discovery.index.value.truncate", false)) {
            super.addField(name, truncateValue(value));
        } else {
            super.addField(name, value);
        }
    }

    private Object truncateValue(Object value) {
        if (value instanceof String) {
            String stringValue = (String) value;
            return StringUtils.length(stringValue) > FIELD_MAX_LENGTH ?
                    stringValue.substring(0, FIELD_MAX_LENGTH - 1) + "…" : stringValue;
        }
        return value;
    }
}
