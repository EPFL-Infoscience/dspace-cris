/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.logic.filter;

import static org.apache.commons.lang3.StringUtils.contains;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import org.dspace.content.Item;
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
        return isPublication(item) && isThesis(item) && hasNotDoiOrHasCustomerDoi(item);
    }

    private boolean isPublication(Item item) {
        return "Publication".equalsIgnoreCase(itemService.getEntityType(item));
    }

    private boolean isThesis(Item item) {
        String type = itemService.getMetadataFirstValue(item, "dc", "type", null, Item.ANY);
        if (isEmpty(type)) {
            return false;
        }
        return type.contains("thesis") || type.contains("::thèse::");
    }

    private boolean hasNotDoiOrHasCustomerDoi(Item item) {
        String doi = itemService.getMetadataFirstValue(item, "dc", "identifier", "doi", Item.ANY);
        if (isEmpty(doi)) {
            return true;
        }

        String doiPrefix = configurationService.getProperty("identifier.doi.prefix");
        return contains(doi, doiPrefix);
    }

    @Override
    public void setBeanName(String name) {

    }

    @Override
    public String getName() {
        return "epfl-doi-filter";
    }

}
