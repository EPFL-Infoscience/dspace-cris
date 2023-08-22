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
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.ItemServiceImpl;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResultIterator;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.identifier.VersionedHandleIdentifierProvider;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;


public class AddHandlerForAllBitstreamsScript
        extends DSpaceRunnable<AddHandlerForAllBitstreamsScriptConfiguration<AddHandlerForAllBitstreamsScript>> {

    private Context context;
    private ItemService itemService;
    private VersionedHandleIdentifierProvider versionedHandleIdentifierProvider;

    private String itemId;
    private Integer itemsToUpdate;


    @Override
    @SuppressWarnings("unchecked")
    public AddHandlerForAllBitstreamsScriptConfiguration<AddHandlerForAllBitstreamsScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("add-missing-handle-for-all-bitstreams",
                AddHandlerForAllBitstreamsScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        itemService = new DSpace().getSingletonService(ItemServiceImpl.class);
        versionedHandleIdentifierProvider = new DSpace().getSingletonService(VersionedHandleIdentifierProvider.class);
        itemId = commandLine.getOptionValue("i");
        if (commandLine.hasOption("m")) {
            itemsToUpdate = Integer.valueOf(commandLine.getOptionValue("m"));
        }
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        if (StringUtils.isBlank(itemId)) {
            addHandlerForAllBitstreams();
        } else {
            createHandlerForSingleItem();
        }

        context.complete();
        context.restoreAuthSystemState();
    }

    private void createHandlerForSingleItem() throws SQLException {
        Item item = itemService.find(context, UUIDUtils.fromString(itemId));
        if (Objects.isNull(item)) {
            handler.logInfo("Item with id " + itemId + " not found");
            return;
        }
        createHandlerForBitstreamsInItem(item);
    }

    private void addHandlerForAllBitstreams() throws SQLException {
        Iterator<Item> allItems = getAllItems();
        int updates = 0;

        while (allItems.hasNext() && underMaxUpdates(updates++)) {
            Item item = allItems.next();
            createHandlerForBitstreamsInItem(item);
        }
    }

    private boolean underMaxUpdates(int i) {
        if (Objects.isNull(itemsToUpdate)) {
            return true;
        }
        return i < itemsToUpdate;
    }

    private void createHandlerForBitstreamsInItem(Item item) throws SQLException {
        if (isItemHaveHandler(item)) {
            item = context.reloadEntity(item);
            Iterator<Bitstream> bitstreams = getOriginalBitstreams(item);
            boolean updated = false;
            while (bitstreams.hasNext()) {
                Bitstream bitstream = bitstreams.next();
                createHandlerForBitstream(bitstream, item.getHandle());
                updated = true;
            }
            if (updated) {
                handler.logInfo("Handler for ORIGINAL bundle bitstreams in item with uuid " + item.getID() + " added");
                context.commit();
            } else {
                handler.logInfo("Item with uuid " + item.getID() + " does not have an ORIGINAL bundle, " +
                                    "handle not created");
            }
        } else {
            handler.logInfo("Item with uuid " +  item.getID() + " dont have handle");
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


        DiscoverQuery discoveryQuery = new DiscoverQuery();
        discoveryQuery.setDSpaceObjectFilter(IndexableItem.TYPE);
        discoveryQuery.setMaxResults(20);
        discoveryQuery.addFilterQueries("archived:true",
                                        "dspace.entity.type:Publication OR " +
                                            "dspace.entitytype:Product OR dspace.entitytype:Patent");
        return new DiscoverResultIterator<Item, UUID>(context, discoveryQuery, false);

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
