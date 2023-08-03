/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.statistics;

import static java.util.Optional.of;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.solr.client.solrj.SolrServerException;
import org.dspace.core.Context;
import org.dspace.statistics.SolrLoggerServiceImpl.StatisticsType;
import org.dspace.statistics.content.filter.StatisticsSolrDateFilter;
import org.dspace.statistics.service.SearchStatisticsService;
import org.dspace.statistics.service.SolrLoggerService;
import org.springframework.beans.factory.annotation.Autowired;

public class SearchStatisticsServiceImpl implements SearchStatisticsService {

    @Autowired
    private SolrLoggerService solrLoggerService;

    @Override
    public SearchStatistics findSearchStatistics(Context context, Date startDate, Date endDate) {

        ObjectCount[] objectCounts = getObjectCounts(startDate, endDate);

        SearchStatistics statistics = new SearchStatistics();
        statistics.setCount(getTotalCount(objectCounts));
        statistics.setTopSearches(getTopSearches(objectCounts));

        return statistics;
    }

    private String composeFilter(Date startDate, Date endDate) {
        return Stream.of(of(getWorkflowTypeFilter()), getDateFilter(startDate, endDate))
            .flatMap(Optional::stream)
            .collect(Collectors.joining(" AND "));
    }

    private ObjectCount[] getObjectCounts(Date startDate, Date endDate) {
        String filter = composeFilter(startDate, endDate);
        try {
            return solrLoggerService.queryFacetField("*:*", filter, "query", 20, true, List.of(), 1);
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private long getTotalCount(ObjectCount[] objectCounts) {
        return objectCounts.length != 0 ? objectCounts[objectCounts.length - 1].getCount() : 0L;
    }

    private Map<String, Long> getTopSearches(ObjectCount[] objectCounts) {
        return Arrays.stream(objectCounts)
            .filter(objectCount -> !"total".equalsIgnoreCase(objectCount.getValue()))
            .collect(toMap(ObjectCount::getValue, ObjectCount::getCount, (x, y) -> y, LinkedHashMap::new));
    }

    private Optional<String> getDateFilter(Date startDate, Date endDate) {
        if (startDate == null && endDate == null) {
            return Optional.empty();
        }

        StatisticsSolrDateFilter dateFilter = new StatisticsSolrDateFilter();
        dateFilter.setStartDate(startDate != null ? startDate : new Date(0L));
        dateFilter.setEndDate(endDate != null ? endDate : new Date());
        return Optional.of(dateFilter.toQuery());
    }

    private String getWorkflowTypeFilter() {
        return "statistics_type:" + StatisticsType.SEARCH.text();
    }

}
