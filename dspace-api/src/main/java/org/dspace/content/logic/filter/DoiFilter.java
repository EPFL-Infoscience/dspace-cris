/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.logic.filter;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;

import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.logic.Filter;
import org.dspace.content.logic.LogicalStatementException;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

public class DoiFilter implements Filter {

    @Autowired
    private ItemService itemService;
    @Autowired
    private ConfigurationService configurationService;

    @Override
    public Boolean getResult(Context context, Item item) throws LogicalStatementException {
        return isLegacyItem(item) && isPublication(item) && isThesis(item) && isWrittenEPFL(item) &&
               hasPublisher(item) && hasNotDoiOrHasCustomerDoi(item);
    }

    private boolean hasPublisher(Item item) {
        String dcPublisher = itemService.getMetadataFirstValue(item, "dc", "publisher", null, Item.ANY);
        return isNotBlank(dcPublisher);
    }

    private boolean isWrittenEPFL(Item item) {
        String type = itemService.getMetadataFirstValue(item, "epfl", "writtenAt", null, Item.ANY);
        if (isBlank(type)) {
            return false;
        }
        return type.equalsIgnoreCase("EPFL");
    }

    private boolean isLegacyItem(Item item) {
        String legacyId = itemService.getMetadataFirstValue(item, "cris", "legacyId", null, Item.ANY);
        if (isNotBlank(legacyId)) {
            return true;
        }
        return false;
    }

    private boolean isPublication(Item item) {
        return "Publication".equalsIgnoreCase(itemService.getEntityType(item));
    }

    private boolean isThesis(Item item) {
        List<MetadataValue> values = itemService.getMetadata(item, "dc.type", "thesis-coar-types:c_db06");
        return values.size() > 0;
    }

    private boolean hasNotDoiOrHasCustomerDoi(Item item) {
        String doi = itemService.getMetadataFirstValue(item, "dc", "identifier", "doi", Item.ANY);
        if (isBlank(doi)) {
            return true;
        }
        String doiPrefix = configurationService.getProperty("identifier.doi.prefix") + "/"
                + configurationService.getProperty("identifier.doi.namespaceseparator");
        return doi.contains(doiPrefix);
    }

    @Override
    public void setBeanName(String name) { }

    @Override
    public String getName() {
        return "epfl-doi-filter";
    }

}
