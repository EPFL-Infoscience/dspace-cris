/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dspace.app.rest.DeduplicationSetMergeRestController;

/**
 * The DeduplicationSetMerge REST Resource
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
public class DeduplicationSetMergeRest extends BaseObjectRest<String> {
    public static final String CATEGORY = "deduplications";
    public static final String NAME = "merge";
    public static final String PLURAL_NAME = "merge";

    private List<String> mergedItems;

    private List<String> mergedBitstreams;

    private ItemRest item;

    @Override
    @JsonIgnore
    public String getId() {
        return super.getId();
    }

    @Override
    @JsonIgnore
    public String getType() {
        return NAME;
    }

    @Override
    @JsonIgnore
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    @JsonIgnore
    public Class getController() {
        return DeduplicationSetMergeRestController.class;
    }

    public List<String> getMergedItems() {
        return mergedItems;
    }

    public void setMergedItems(List<String> mergedItems) {
        this.mergedItems = mergedItems;
    }

    public List<String> getMergedBitstreams() {
        return mergedBitstreams;
    }

    public void setMergedBitstreams(List<String> mergedBitstreams) {
        this.mergedBitstreams = mergedBitstreams;
    }

    public ItemRest getItem() {
        return item;
    }

    public void setItem(ItemRest item) {
        this.item = item;
    }
}
