/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.security;

import java.util.UUID;

import org.dspace.content.Item;
import org.dspace.content.logic.Filter;
import org.dspace.content.logic.LogicalStatementException;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResultIterator;
import org.springframework.beans.factory.annotation.Autowired;

public class ThematicCollectionDelegateFilter implements Filter {
    private String name;

    @Autowired
    ItemService itemService;

    @Override
    public Boolean getResult(Context context, Item item) throws LogicalStatementException {
        if (context.getCurrentUser() == null) {
            return false;
        }
        DiscoverQuery query = new DiscoverQuery();
        query.addFilterQueries("epfl.virtualCollection.curator_authority:" + context.getCurrentUser().getID());
        boolean isVirtualCollection = itemService.getEntityType(item).equals("VirtualCollection");
        if (isVirtualCollection) {
            query.addFilterQueries("search.resourceid:" + item.getID());
        }
        return new DiscoverResultIterator<Item, UUID>(context, query).hasNext();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setBeanName(String name) {
        this.name = name;
    }
}
