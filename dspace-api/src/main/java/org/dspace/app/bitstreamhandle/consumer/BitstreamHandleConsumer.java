/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.bitstreamhandle.consumer;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.identifier.VersionedHandleIdentifierProvider;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BitstreamHandleConsumer implements Consumer {

    private static final Logger logger = LoggerFactory.getLogger(BitstreamHandleConsumer.class);
    private final Set<Bitstream> bitstreamsAlreadyProcessed = new HashSet<>();

    private VersionedHandleIdentifierProvider versionedHandleIdentifierProvider;

    private BitstreamService bitstreamService;
    @Override
    public void initialize() throws Exception {
        versionedHandleIdentifierProvider = new DSpace().getSingletonService(VersionedHandleIdentifierProvider.class);
        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        if (event.getSubjectType() != Constants.BITSTREAM) {
            return;
        }
        Bitstream bitstream = getBitstream(ctx, event);
        try {
            if (null == bitstream || bitstreamsAlreadyProcessed.contains(bitstream) || hasHandle(bitstream)) {
                return;
            }
            String itemHandle = itemHandle(bitstream);
            if (StringUtils.isBlank(itemHandle)) {
                logger.warn("Parent item of bitstream {} does not have an handle", event.getSubjectID());
                return;
            }
            versionedHandleIdentifierProvider.createAndPopulateHandlerForBitstreams(ctx, bitstream,
                                                                                    itemHandle);
        } finally {
            bitstreamsAlreadyProcessed.add(bitstream);
        }
    }

    private String itemHandle(Bitstream bitstream) throws SQLException {
        if (bitstream.getBundles().isEmpty() || bitstream.getBundles().get(0)
            .getItems().isEmpty()) {
            return null;
        }
        Item item = bitstream.getBundles().get(0).getItems().get(0);
        return item.getHandle();
    }

    private boolean hasHandle(Bitstream bitstream) {
        return !bitstreamService
            .getMetadata(bitstream, "dc", "identifier", "uri", Item.ANY).isEmpty();
    }

    private Bitstream getBitstream(Context ctx, Event event) throws SQLException {
        Bitstream bitstream = (Bitstream) event.getSubject(ctx);
        if (Objects.nonNull(bitstream)) {
            return bitstream;
        }
        return bitstreamService.find(ctx, event.getSubjectID());
    }

    @Override
    public void end(Context ctx) throws Exception {
        bitstreamsAlreadyProcessed.clear();
    }

    @Override
    public void finish(Context ctx) throws Exception {

    }
}
