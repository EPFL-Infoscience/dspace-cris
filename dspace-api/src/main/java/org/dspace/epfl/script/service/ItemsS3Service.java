/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service;

import java.io.File;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.stream.Stream;

import org.dspace.core.Context;
import org.dspace.epfl.script.model.ItemImportDTO;
import org.dspace.epfl.script.model.ItemsImportModificationDate;
import org.dspace.scripts.handler.DSpaceRunnableHandler;

public interface ItemsS3Service {

    Stream<String> getAllItemsKeys();

    Stream<String> getItemsKeys(Integer limit, String startAfter);

    File getObject(String key);

    String getCreationDate(Context context, String id);

    Integer importCreationDates(Context context, InputStream is, DSpaceRunnableHandler handler) throws Exception;

    Integer importModificationDates(Context context, Iterator<ItemImportDTO> items, DSpaceRunnableHandler handler)
            throws SQLException;

    ItemsImportModificationDate createOrUpdateModificationDate(Context context, ItemImportDTO item,
            DSpaceRunnableHandler handler);

    String getModificationDate(Context context, String id);

    void deleteModificationDate(Context context, String string);


}
