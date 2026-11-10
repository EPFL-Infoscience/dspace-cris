/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks;

import static org.apache.commons.collections4.IteratorUtils.singletonListIterator;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

import org.apache.poi.ss.usermodel.Workbook;
import org.dspace.app.bulkedit.BulkImport;
import org.dspace.app.bulkimport.service.BulkImportWorkbookBuilder;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.CrosswalkMode;
import org.dspace.content.crosswalk.CrosswalkObjectNotSupported;
import org.dspace.content.crosswalk.StreamDisseminationCrosswalk;
import org.dspace.content.service.CollectionService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.scripts.handler.DSpaceRunnableHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link StreamDisseminationCrosswalk} to export a single
 * item in the xls format. This format is the same expected by the import
 * performed with {@link BulkImport}.
 *
 * <p>Unlike {@link XlsCollectionCrosswalk}, which operates at collection/search
 * level and dumps all the items of a collection (or search result), this
 * crosswalk is scoped to the single item export (item level). The owning
 * collection of the item is used only to derive the column schema of the
 * workbook from its submission form.</p>
 *
 * @author Giulia Barucca (giulia.barucca at 4science.com)
 *
 */
public class XlsItemCrosswalk implements ItemExportCrosswalk {

    private static final Logger LOGGER = LoggerFactory.getLogger(XlsItemCrosswalk.class);

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private BulkImportWorkbookBuilder bulkImportWorkbookBuilder;

    private String entityType;
    private List<String> configurations;

    private DSpaceRunnableHandler handler;

    @Override
    public boolean canDisseminate(Context context, DSpaceObject dso) {
        return dso.getType() == Constants.ITEM;
    }

    @Override
    public String getMIMEType() {
        return "application/vnd.ms-excel";
    }

    @Override
    public String getFileName() {
        return "items.xls";
    }

    @Override
    public CrosswalkMode getCrosswalkMode() {
        return CrosswalkMode.SINGLE;
    }

    @Override
    public void disseminate(Context context, DSpaceObject dso, OutputStream out)
            throws CrosswalkException, IOException, SQLException, AuthorizeException {

        if (!canDisseminate(context, dso)) {
            throw new CrosswalkObjectNotSupported("Can only crosswalk an Item");
        }

        Item item = (Item) dso;
        Collection collection = findCollection(context, item);

        writeWorkbook(context, collection, singletonListIterator(item), out);
    }

    @Override
    public void disseminate(Context context, Iterator<? extends DSpaceObject> dsoIterator, Integer total,
            Integer offset, Integer size, OutputStream out)
            throws CrosswalkException, IOException, SQLException, AuthorizeException {
        throw new CrosswalkObjectNotSupported(
            "XlsItemCrosswalk supports only the single item export. "
                + "Use XlsCollectionCrosswalk for the collection/search level export.");
    }

    @Override
    public Optional<String> getEntityType() {
        return Optional.ofNullable(entityType);
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public void setConfigurations(List<String> configurations) {
        this.configurations = configurations;
    }

    @Override
    public Optional<List<String>> getConfigurations() {
        return Optional.ofNullable(configurations);
    }

    private void writeWorkbook(Context context, Collection collection, Iterator<Item> itemIterator, OutputStream out)
            throws IOException {
        this.setHandler(this.handler);
        try (Workbook workbook = bulkImportWorkbookBuilder.buildForItems(
                context, collection, itemIterator, this::logMessage)) {
            workbook.write(out);
        }
    }

    private Collection findCollection(Context context, Item item) throws SQLException {
        Collection collection = collectionService.findByItem(context, item);
        if (collection == null) {
            throw new IllegalArgumentException("No collection found for item with id: " + item.getID());
        }
        return collection;
    }

    protected void logMessage(Level level, String message) {

        if (handler != null) {
            switch (level.getName()) {
                case "OFF":
                    LOGGER.info(message);
                    break;
                case "SEVERE":
                    handler.logError(message);
                    break;
                case "WARNING":
                    handler.logWarning(message);
                    break;
                case "INFO":
                case "CONFIG":
                case "FINE":
                    handler.logInfo(message);
                    break;
                case "FINER":
                case "FINEST":
                case "ALL":
                default:
                    handler.logDebug(message);
                    break;
            }
        }
    }

    public void setHandler(DSpaceRunnableHandler handler) {
        this.handler = handler;
    }
}
