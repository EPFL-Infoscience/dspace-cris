/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.security;

import org.dspace.content.Item;
import org.dspace.content.logic.Filter;
import org.dspace.content.logic.LogicalStatementException;
import org.dspace.core.Context;

/**
 * Simple implementation checking if current user is logged in, not an Anonymous
 */
public class LoggedInFilter implements Filter {
    private String name;

    @Override
    public Boolean getResult(Context context, Item item) throws LogicalStatementException {
        return context.getCurrentUser() != null;
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
