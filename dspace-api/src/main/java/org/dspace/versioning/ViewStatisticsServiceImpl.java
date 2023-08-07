/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.versioning;

import java.sql.SQLException;
import java.util.List;

import org.dspace.content.Item;
import org.dspace.content.security.AccessItemMode;
import org.dspace.content.security.service.CrisSecurityService;
import org.dspace.core.Context;
import org.dspace.versioning.service.ViewStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;


public class ViewStatisticsServiceImpl implements ViewStatisticsService {

    @Autowired
    private CrisSecurityService crisSecurityService;

    @Autowired
    @Qualifier("viewUsageStatisticsAccessModesList")
    private List<AccessItemMode> viewUsageStatisticsAccessModes;

    @Override
    public boolean canViewStatistics(Context context, Item item) {
        return viewUsageStatisticsAccessModes.stream()
                .anyMatch(am -> isHasAccess(context, item, am));
    }

    private boolean isHasAccess(Context context, Item item, AccessItemMode accessItemMode) {
        try {
            return crisSecurityService.hasAccess(context, item,
                    context.getCurrentUser(),
                    accessItemMode);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
