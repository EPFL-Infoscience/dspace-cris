/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submit.service;

import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.core.Context;

public interface ChangeSubmitterService {

    /**
     * Updates the submitter given an item and the submitter identifier
     * 
     * @param context       DSpace context
     * @param item          Whose submitter will be updated
     * @param submitterName A string (uuid, mail, netid...)
     * @throws SQLException
     * @throws AuthorizeException
     */
    void setUpSubmitter(Context context, Item item, String submitterName) throws SQLException, AuthorizeException;
}
