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
 * Object representing a Deduplication Set Merge Items.
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 *
 */
public class DeduplicationMergeTarget {

    private List<String> allowedTargets;

    public DeduplicationMergeTarget() {
    }

    public DeduplicationMergeTarget(List<String> allowedTargets) {
        this.allowedTargets = allowedTargets;
    }

    public List<String> getAllowedTargets() {
        return allowedTargets;
    }

    public void setAllowedTargets(List<String> allowedTargets) {
        this.allowedTargets = allowedTargets;
    }
}
