/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.io.IOUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;

public class DuplicationFixScript extends DSpaceRunnable<DuplicationFixScriptConfiguration<DuplicationFixScript>> {

    private ItemService itemService;

    private String filename;

    private Context context;

    @Override
    @SuppressWarnings("unchecked")
    public DuplicationFixScriptConfiguration<DuplicationFixScript> getScriptConfiguration() {
        return new DSpace().getServiceManager()
            .getServiceByName("epfl-duplication-fix", DuplicationFixScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        itemService = ContentServiceFactory.getInstance().getItemService();
        filename = commandLine.getOptionValue('f');
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        InputStream inputStream = handler.getFileStream(context, filename)
            .orElseThrow(() -> new IllegalArgumentException("Error reading file, the file couldn't be "
                + "found for filename: " + filename));

        try {
            fixDuplication(inputStream);
            context.complete();
            context.restoreAuthSystemState();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        }
    }

    private void fixDuplication(InputStream inputStream) throws Exception {
        List<String> sciperIds = IOUtils.readLines(inputStream, Charset.defaultCharset());
        for (String sciperId : sciperIds) {
            sciperId = sciperId.trim().replace(",", "");
            fixDuplication(sciperId);
        }
    }

    private void fixDuplication(String sciperId) throws Exception {

        List<Item> items = findItemsBySciperId(sciperId);

        if (items.isEmpty()) {
            handler.logInfo("No items found by sciper " + sciperId);
            return;
        }

        if (items.size() == 1) {
            handler.logInfo("Only one item found by sciper " + sciperId);
            return;
        }

        handler.logInfo("Found " + items.size() + " items by sciper " + sciperId);
        fixDuplication(items);

        context.commit();

    }

    private void fixDuplication(List<Item> items) throws Exception {

        Item itemToKeep = items.get(0);
        handler.logInfo("Item " + itemToKeep.getID() + " will be kept");

        List<Item> itemsToBeDeleted = items.subList(1, items.size());
        handler.logInfo(itemsToBeDeleted.size() + " items will be deleted");

        for (Item itemToBeDeleted : itemsToBeDeleted) {

            int updatedAuthorityCount = 0;

            String itemToBeDeletedId = itemToBeDeleted.getID().toString();

            Iterator<Item> iterator = itemService.findRelatedItemsByAuthorityControlledFields(context, itemToBeDeleted,
                List.of(itemToBeDeletedId));

            while (iterator.hasNext()) {
                Item item = iterator.next();
                for (MetadataValue mv : item.getMetadata()) {
                    if (itemToBeDeletedId.equals(mv.getAuthority())) {
                        handler.logInfo("Item " + item.getID() + " has a reference and will be updated");
                        mv.setAuthority(itemToKeep.getID().toString());
                        updatedAuthorityCount++;
                    }
                }

                itemService.update(context, item);

            }

            itemService.delete(context, itemToBeDeleted);
            handler.logInfo("Deleted item " + itemToBeDeletedId +
                ". Updated " + updatedAuthorityCount + " authorities");

        }

    }

    private List<Item> findItemsBySciperId(String sciperId) throws SQLException, AuthorizeException {
        Iterator<Item> iterator = itemService.findArchivedByMetadataField(context, "epfl.sciperId", sciperId);
        return IteratorUtils.toList(iterator);
    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() throws SQLException {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }
}
