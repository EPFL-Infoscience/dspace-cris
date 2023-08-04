/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.ItemServiceImpl;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.identifier.VersionedHandleIdentifierProvider;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;


public class AddHandlerForAllBitstreamsScript
        extends DSpaceRunnable<AddHandlerForAllBitstreamsScriptConfiguration<AddHandlerForAllBitstreamsScript>> {

    private Context context;
    private ItemService itemService;
    private VersionedHandleIdentifierProvider versionedHandleIdentifierProvider;


    @Override
    @SuppressWarnings("unchecked")
    public AddHandlerForAllBitstreamsScriptConfiguration<AddHandlerForAllBitstreamsScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("add-handler-for-all-bitstreams",
                AddHandlerForAllBitstreamsScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        itemService = new DSpace().getSingletonService(ItemServiceImpl.class);
        versionedHandleIdentifierProvider = new DSpace().getSingletonService(VersionedHandleIdentifierProvider.class);
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        addHandlerForAllBitstreams();

        context.complete();
        context.restoreAuthSystemState();
    }

    private void addHandlerForAllBitstreams() {
        Iterator<Item> allItems = getAllItems();

        while (allItems.hasNext()) {
            Item item = allItems.next();
            createHandlerForBitstreamsInItem(item);
        }
    }

    private void createHandlerForBitstreamsInItem(Item item) {
        if (isItemHaveHandler(item)) {
            Iterator<Bitstream> bitstreams = getOriginalBitstreams(item);
            while (bitstreams.hasNext()) {
                Bitstream bitstream = bitstreams.next();
                createHandlerForBitstream(bitstream, item.getHandle());
            }
            handler.logInfo("Handlers for bitstreams in item with uuid " + item.getID() + " is created");
        } else {
            handler.logInfo("Item with uuid " +  item.getID() + " dont have handler");
        }
    }

    private void createHandlerForBitstream(Bitstream bitstream, String itemHandler) {
        if (!isBitstreamHaveHandler(bitstream)) {
            try {
                versionedHandleIdentifierProvider.createAndPopulateHandlerForBitstreams(context, bitstream,itemHandler);
            } catch (SQLException | AuthorizeException | IOException e) {
                handler.logError("Error during creating handler for bitstream with uuid: "
                        + bitstream.getID());
                throw new RuntimeException(e);
            }
        }
    }

    private boolean isBitstreamHaveHandler(Bitstream bitstream) {
        return bitstream.getMetadata().stream()
                .anyMatch(metadataValue -> metadataValue.getMetadataField().toString().equals("dc_identifier_uri"));
    }

    private boolean isItemHaveHandler(Item item) {
        return !itemService.getMetadataFirstValue(item, "dc", "identifier", "uri", "en").isEmpty();
    }

    private Iterator<Item> getAllItems() {
        try {
            return itemService.findAll(context);
        } catch (SQLException e) {
            handler.logError("Error during getting all items");
            throw new RuntimeException(e);
        }
    }

    private Iterator<Bitstream> getOriginalBitstreams (Item item) {
        try {
            return  itemService.getBundles(item, "ORIGINAL")
                    .stream().flatMap(bundle -> bundle.getBitstreams().stream()).iterator();
        } catch (SQLException e) {
            handler.logError("Error during getting original bitstreams of item with uuid: " + item.getID());
            throw new RuntimeException(e);
        }
    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }
}
