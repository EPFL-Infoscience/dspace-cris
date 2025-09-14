/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.policy;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.CollectionUtils;
import org.dspace.access.status.factory.AccessStatusServiceFactory;
import org.dspace.access.status.service.AccessStatusService;
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
    private Context context;
    private int pageSize;
    private String index;
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
        pageSize = Integer.parseInt(commandLine.getOptionValue("ps", "20"));
        index = commandLine.getOptionValue('i');
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
                UUID uuid;
                try {
                    uuid = UUID.fromString(index);
                } catch (IllegalArgumentException e) {
                    handler.logError("The given index is not a valid UUID: " + index);
                    return;
                }
                Item item = itemService.find(context, uuid);
                if (item != null) {
                    updateItem(item);
                    itemService.update(context, item);
                    context.commit();
                } else {
                    handler.logError("The given index does not refer to any item: " + index);
                }
            } else {
                // Update all items
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
            context.restoreAuthSystemState();
        } finally {
            context.complete();
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
