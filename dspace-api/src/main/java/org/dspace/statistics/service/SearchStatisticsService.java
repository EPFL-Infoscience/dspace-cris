/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.statistics.service;

import java.util.Date;

import org.dspace.core.Context;
import org.dspace.statistics.SearchStatistics;

public interface SearchStatisticsService {

    SearchStatistics findSearchStatistics(Context context, Date startDate, Date endDate);
}
