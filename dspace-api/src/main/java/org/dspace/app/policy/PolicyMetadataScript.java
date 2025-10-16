/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.policy;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.CollectionUtils;
import org.dspace.access.status.factory.AccessStatusServiceFactory;
import org.dspace.access.status.service.AccessStatusService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.Context.Mode;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;

public class PolicyMetadataScript extends DSpaceRunnable<PolicyMetadataScriptConfiguration<PolicyMetadataScript>> {
    private static final int DEFAULT_PAGE_SIZE = 20;

    private Context context;
    private int pageSize;
    private String index;
    private String metadata;
    protected ItemService itemService;
    protected BitstreamService bitstreamService;
    protected AccessStatusService accessStatusService;

    @SuppressWarnings("unchecked")
    @Override
    public PolicyMetadataScriptConfiguration<PolicyMetadataScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("access-status-metadata",
                PolicyMetadataScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        context = new Context(Mode.BATCH_EDIT);
        pageSize = Integer.parseInt(commandLine.getOptionValue("ps", String.valueOf(DEFAULT_PAGE_SIZE)));
        index = commandLine.getOptionValue('i');
        metadata = commandLine.getOptionValue('m');
        this.bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        this.itemService = ContentServiceFactory.getInstance().getItemService();
        this.accessStatusService = AccessStatusServiceFactory.getInstance().getAccessStatusService();
    }

    @Override
    public void internalRun() throws Exception {
        try {
            context.turnOffAuthorisationSystem();
            if (index != null) {
                // Update one item
                updateSingleItem();
            } else if (metadata != null) {
                // Update items filtered
                updateItemsFiltered();
            } else {
                // Update all items
                updateAllItems();
            }
            context.restoreAuthSystemState();
        } finally {
            context.complete();
        }
    }

    private void updateSingleItem() throws SQLException, AuthorizeException {
        UUID uuid;
        try {
            uuid = UUID.fromString(index);
        } catch (IllegalArgumentException e) {
            handler.logError("The given index is not a valid UUID: " + index);
            throw e;
        }
        Item item = itemService.find(context, uuid);
        if (item != null) {
            updateItem(item);
            itemService.update(context, item);
            context.commit();
        } else {
            handler.logError("The given index does not refer to any item: " + index);
        }
    }

    private void updateItemsFiltered() throws SQLException, AuthorizeException, IOException {
        Iterator<Item> iterator = itemService.findByMetadataField(context, "datacite", "rights", null, metadata);
        int count = 0;
        while (iterator.hasNext()) {
            Item item = iterator.next();
            updateItem(item);
            itemService.update(context, item);
            count++;
            if (count % pageSize == 0) {
                handler.logInfo("Processed " + count + " items");
                context.commit();
            }
        }
        if (count % pageSize != 0) {
            handler.logInfo("Processed " + count + " items");
            context.commit();
        }

    }

    private void updateAllItems() throws SQLException, AuthorizeException {
        boolean hasMoreItems = true;
        int offset = 0;
        while (hasMoreItems) {
            Iterator<Item> iterator = findItems(offset);
            if (!iterator.hasNext()) {
                hasMoreItems = false;
                continue;
            }
            while (iterator.hasNext()) {
                Item item = iterator.next();
                updateItem(item);
                itemService.update(context, item);
            }
            offset += pageSize;
            handler.logInfo("Processed " + offset + " items");
            context.commit();
        }
    }

    private void updateItem(Item item) {
        handler.logInfo("Updating item " + item.getID());
        List<Bundle> bundles = item.getBundles();
        if (CollectionUtils.isNotEmpty(bundles)) {
            bundles.stream()
                    .map(Bundle::getBitstreams)
                    .flatMap(Collection::stream)
                    .forEach(bitstream -> {
                        try {
                            handler.logInfo("Updating bitstream " + bitstream.getID() + " of item " + item.getID());
                            PolicyMetadataUtils.handleBitstream(context, bitstream, false);
                        } catch (Exception e) {
                            throw new RuntimeException("an error occurred", e);
                        }
                    });
        }
    }

    private Iterator<Item> findItems(int offset) {
        try {
            return itemService.findAll(context, pageSize, offset);
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }
    }


}
