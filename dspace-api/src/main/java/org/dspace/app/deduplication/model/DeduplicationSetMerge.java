/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.deduplication.model;

import java.util.List;

import org.dspace.content.Bitstream;
import org.dspace.content.Item;

/**
 * Object representing a Deduplication Set Merge Items.
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 *
 */
public class DeduplicationSetMerge {

    private List<Item> mergedItems;
    private List<Bitstream> mergedBitstreams;
    private Item item;

    public DeduplicationSetMerge() {
    }

    public DeduplicationSetMerge(List<Item> mergedItems, List<Bitstream> mergedBitstreams, Item item) {
        this.mergedItems = mergedItems;
        this.mergedBitstreams = mergedBitstreams;
        this.item = item;
    }

    public List<Item> getMergedItems() {
        return mergedItems;
    }

    public void setMergedItems(List<Item> mergedItems) {
        this.mergedItems = mergedItems;
    }

    public List<Bitstream> getMergedBitstreams() {
        return mergedBitstreams;
    }

    public void setMergedBitstreams(List<Bitstream> mergedBitstreams) {
        this.mergedBitstreams = mergedBitstreams;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }
}
