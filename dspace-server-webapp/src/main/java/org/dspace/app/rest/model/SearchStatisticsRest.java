/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.Map;

import org.dspace.app.rest.RestResourceController;

public class SearchStatisticsRest extends BaseObjectRest<String> {

    public static final String NAME = "search";

    public static final String CATEGORY = RestModel.STATISTICS;

    private long count;

    private Map<String, Long> topSearches;

    @Override
    public String getType() {
        return NAME;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Class<?> getController() {
        return RestResourceController.class;
    }

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
