/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks.virtualfields;

import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;

/**
 * Implementation of {@link VirtualField} that returns the item ui url.
 *
 */
public class VirtualFieldItemURL implements VirtualField {

    @Override
    public String[] getMetadata(Context context, Item item, String fieldName) {
        ConfigurationService configurationService = new DSpace().getConfigurationService();
        final String baseURL = configurationService.getProperty("dspace.ui.url");
        return new String[] { baseURL + "/items/" + item.getID().toString() };
    }

}
