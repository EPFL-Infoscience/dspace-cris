/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service;

import org.dspace.core.Context;
import org.dspace.core.GenericDAO;
import org.dspace.epfl.script.model.ItemsImportModificationDate;

public interface ItemsImportModificationDateDao extends GenericDAO<ItemsImportModificationDate> {

    ItemsImportModificationDate find(Context context, String id);

    String findModificationDate(Context context, String id);
}
