/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.deduplication.model;

import java.util.List;

/**
 * Object representing a Deduplication Merge Items.
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 *
 */
public class DeduplicationMerge {

    private String targetItem;
    private List<String> mergedItems;
    private List<String> replacedNotEmptyMetadata;
    private List<String> replacedMetadata;
    private List<String> appendedMetadata;
    private boolean delete;
    private boolean exclude;

    public DeduplicationMerge() {
    }

    public DeduplicationMerge(String item, List<String> mergedItems,
                              List<String> replacedNotEmptyMetadata, List<String> replacedMetadata,
                              List<String> appendedMetadata, boolean delete, boolean exclude) {
        this.targetItem = item;
        this.mergedItems = mergedItems;
        this.replacedNotEmptyMetadata = replacedNotEmptyMetadata;
        this.replacedMetadata = replacedMetadata;
        this.appendedMetadata = appendedMetadata;
        this.delete = delete;
        this.exclude = exclude;
    }

    public String getTargetItem() {
        return targetItem;
    }

    public void setTargetItem(String targetItem) {
        this.targetItem = targetItem;
    }

    public List<String> getMergedItems() {
        return mergedItems;
    }

    public void setMergedItems(List<String> mergedItems) {
        this.mergedItems = mergedItems;
    }

    public List<String> getReplacedNotEmptyMetadata() {
        return replacedNotEmptyMetadata;
    }

    public void setReplacedNotEmptyMetadata(List<String> replacedNotEmptyMetadata) {
        this.replacedNotEmptyMetadata = replacedNotEmptyMetadata;
    }

    public List<String> getReplacedMetadata() {
        return replacedMetadata;
    }

    public void setReplacedMetadata(List<String> replacedMetadata) {
        this.replacedMetadata = replacedMetadata;
    }

    public List<String> getAppendedMetadata() {
        return appendedMetadata;
    }

    public void setAppendedMetadata(List<String> appendedMetadata) {
        this.appendedMetadata = appendedMetadata;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public boolean isExclude() {
        return exclude;
    }

    public void setExclude(boolean exclude) {
        this.exclude = exclude;
    }
}
