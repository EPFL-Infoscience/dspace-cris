/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.versioning.service;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;

public interface ViewStatisticsService {

    /**
     * Checks whether or not current user can view usage statistics of a given item.
     * If user passes CRIS security checks defined in configuration file `view-usage-security.xml'
     *
     * @param context The relevant DSpace Context
     * @param item DSpace Item against which check is performed
     * @return
     */
    boolean canViewStatistics(Context context, DSpaceObject item);
}
