/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.policy.consumer;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.dspace.app.policy.PolicyMetadataUtils;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code Consumer} that transforms the policy inside a {@code BitStream} to target metadatas.
 * 
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 *
 */
public class PolicyMetadataEnhancerConsumer implements Consumer {

    private static final Logger logger = LoggerFactory.getLogger(PolicyMetadataEnhancerConsumer.class);

    private final Set<Bitstream> bitstreamAlreadyProcessed = new HashSet<>();
    private final Set<Item> itemsToProcess = new HashSet<>();
    private final Set<Item> itemsToUpdate = new HashSet<>();


    @Override
    public void initialize() throws Exception {

    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        if (Constants.BITSTREAM == event.getSubjectType()) {
            PolicyMetadataUtils.handleBitstream(
                    ctx,
                    Optional.ofNullable((Bitstream) event.getObject(ctx))
                            .orElse(PolicyMetadataUtils.loadBitstream(ctx, event)),
                    event, true, bitstreamAlreadyProcessed, itemsToProcess
            );
        } else if (Constants.ITEM == event.getSubjectType() && (Event.CREATE == event.getEventType() ||
                Event.MODIFY == event.getEventType())) {
            Item loadedItem = PolicyMetadataUtils.handleItem(
                    ctx,
                    Optional.ofNullable((Item) event.getObject(ctx))
                            .orElse(PolicyMetadataUtils.loadItem(ctx, event)),
                    true
            );
            if (loadedItem != null) {
                itemsToUpdate.add(loadedItem);
            }
        } else {
            logger.warn(
                "Can't consume the DSPaceObject with id {} and type {}," +
                        "only BITSTREAM and ITEMS' CREATION events are consumable!",
                event.getSubjectID(), event.getSubjectType()
            );
        }
    }

    @Override
    public void end(Context ctx) throws Exception {
        bitstreamAlreadyProcessed.clear();
        this.itemsToProcess
            .forEach(item -> PolicyMetadataUtils.handleItem(ctx, item, false));
        itemsToProcess.clear();

        itemsToUpdate.forEach(item -> PolicyMetadataUtils.updateItem(ctx, item));
        itemsToUpdate.clear();
    }

    @Override
    public void finish(Context ctx) throws Exception {

    }
}
