/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.integration.crosswalks.virtualfields;

import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;

public class VirtualFieldConfigProperties implements VirtualField {

    private ConfigurationService configurationService;

    @Override
    public String[] getMetadata(Context context, Item item, String fieldName) {
        String value = configurationService.getProperty(fieldName.split("\\.")[2].replaceAll("-", "."));
        return StringUtils.isNotBlank(value) ? new String[] { value } : new String[0];
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
}
