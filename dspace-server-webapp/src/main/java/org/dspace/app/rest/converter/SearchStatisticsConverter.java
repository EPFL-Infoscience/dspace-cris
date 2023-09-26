/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.SearchStatisticsRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.statistics.SearchStatistics;
import org.springframework.stereotype.Component;

@Component
public class SearchStatisticsConverter implements DSpaceConverter<SearchStatistics, SearchStatisticsRest> {

    @Override
    public SearchStatisticsRest convert(SearchStatistics modelObject, Projection projection) {
        SearchStatisticsRest rest = new SearchStatisticsRest();
        rest.setCount(modelObject.getCount());
        rest.setId("search");
        rest.setTopSearches(modelObject.getTopSearches());
        return rest;
    }

    @Override
    public Class<SearchStatistics> getModelClass() {
        return SearchStatistics.class;
    }

}
