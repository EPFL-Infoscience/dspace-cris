package org.dspace.content.integration.crosswalks.virtualfields;

import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;

public class VirtualFieldConfigProperties implements VirtualField {

    private ConfigurationService configurationService;

    @Override
    public String[] getMetadata(Context context, Item item, String fieldName) {
        return new String[] { configurationService.getProperty(fieldName.split("\\.")[2].replaceAll("-", ".")) };
    }

}
