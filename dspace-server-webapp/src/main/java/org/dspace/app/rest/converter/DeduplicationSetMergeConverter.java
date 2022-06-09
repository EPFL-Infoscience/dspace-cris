/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import java.util.ArrayList;
import java.util.List;

import org.dspace.app.deduplication.model.DeduplicationSetMerge;
import org.dspace.app.rest.model.DeduplicationSetMergeRest;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.app.rest.utils.Utils;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This class provides the method to convert a DeduplicationSetMerge to its REST representation,
 * the DeduplicationSetMergeRest
 * 
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
@Component
public class DeduplicationSetMergeConverter
        implements DSpaceConverter<DeduplicationSetMerge, DeduplicationSetMergeRest> {

    @Autowired
    private ConverterService converter;

    @Autowired
    private Utils utils;

    @Override
    public DeduplicationSetMergeRest convert(DeduplicationSetMerge dedupSetMerge, Projection projection) {

        DeduplicationSetMergeRest dedupSetMergeRest = new DeduplicationSetMergeRest();
        dedupSetMergeRest.setProjection(projection);
        List<String> uris = new ArrayList<>();
        List<String> bitstreamsUris = new ArrayList<>();
        Item item;
        ItemRest itemRest = null;
        String itemRestUri = "";

        if (dedupSetMerge != null) {
            item = dedupSetMerge.getItem();

            if (item != null) {
                itemRest = converter.toRest(item, projection);
                itemRestUri = utils.linkToSingleResource(itemRest, "self").getHref();
            }

            dedupSetMergeRest.setItem(itemRest);
            dedupSetMergeRest.setTargetItem(itemRestUri);
            dedupSetMergeRest.setId(item != null ? item.getID().toString() : null);
            for (Item mergedItem : dedupSetMerge.getMergedItems()) {
                uris.add(utils.linkToSingleResource(
                    converter.toRest(mergedItem, projection), "self").getHref());
            }
            for (Bitstream mergedBitstream : dedupSetMerge.getMergedBitstreams()) {
                bitstreamsUris.add(utils.linkToSingleResource(
                    converter.toRest(mergedBitstream, projection), "self").getHref());
            }
            dedupSetMergeRest.setMergedItems(uris);
            dedupSetMergeRest.setMergedBitstreams(bitstreamsUris);
        }
        return dedupSetMergeRest;
    }

    @Override
    public Class<DeduplicationSetMerge> getModelClass() {
        return DeduplicationSetMerge.class;
    }
}
