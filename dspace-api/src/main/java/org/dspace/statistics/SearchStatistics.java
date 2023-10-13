/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.statistics;

import java.util.Map;

public class SearchStatistics {

    private long count;

    private Map<String, Long> topSearches;

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public Map<String, Long> getTopSearches() {
        return topSearches;
    }

    public void setTopSearches(Map<String, Long> topSearches) {
        this.topSearches = topSearches;
    }

}
