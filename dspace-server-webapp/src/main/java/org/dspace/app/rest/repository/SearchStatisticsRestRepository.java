/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;


import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.model.SearchStatisticsRest;
import org.dspace.core.Context;
import org.dspace.statistics.SearchStatistics;
import org.dspace.statistics.service.SearchStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component(SearchStatisticsRest.CATEGORY + "." + SearchStatisticsRest.NAME)
public class SearchStatisticsRestRepository extends DSpaceRestRepository<SearchStatisticsRest, String> {

    public static final DateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private SearchStatisticsService searchStatisticsService;

    @Override
    public SearchStatisticsRest findOne(Context context, String id) {
        throw new RepositoryMethodNotImplementedException("No implementation found; Method not allowed!", "");
    }

    @Override
    public Page<SearchStatisticsRest> findAll(Context context, Pageable pageable) {
        throw new RepositoryMethodNotImplementedException("No implementation found; Method not allowed!", "");
    }

    @SearchRestMethod(name = "byDateRange")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Page<SearchStatisticsRest> findByDateRange(Pageable pageable,
        @Parameter(value = "startDate") String startDate, @Parameter(value = "endDate") String endDate) {

        Context context = obtainContext();
        SearchStatistics searchStatistics = searchStatisticsService.findSearchStatistics(context,
            parseDate(startDate), parseDate(endDate));

        return converter.toRestPage(List.of(searchStatistics), pageable, utils.obtainProjection());

    }

    private Date parseDate(String date) {
        try {
            return isNotBlank(date) ? DATE_FORMATTER.parse(date) : null;
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Class<SearchStatisticsRest> getDomainClass() {
        return SearchStatisticsRest.class;
    }

}
