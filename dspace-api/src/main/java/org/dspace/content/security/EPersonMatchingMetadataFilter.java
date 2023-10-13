/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.security;

import java.util.Objects;

import org.dspace.content.Item;
import org.dspace.content.logic.Filter;
import org.dspace.content.logic.LogicalStatementException;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.util.UUIDUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class EPersonMatchingMetadataFilter implements Filter {
    private String name;

    @Autowired
    private ItemService itemService;
    private final String metadataName;

    public EPersonMatchingMetadataFilter(String metadataName) {
        this.metadataName = metadataName;
    }


    @Override
    public Boolean getResult(Context context, Item item) throws LogicalStatementException {
        if (Objects.isNull(context.getCurrentUser())) {
            return false;
        }
        return itemService.getMetadataByMetadataString(item, metadataName).stream()
                          .anyMatch(
                              mv -> UUIDUtils.toString(context.getCurrentUser().getID()).equals(mv.getAuthority()));
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setBeanName(String name) {
        this.name = name;
    }
}
