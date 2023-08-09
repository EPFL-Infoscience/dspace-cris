/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.versioning;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.security.AccessItemMode;
import org.dspace.content.security.service.CrisSecurityService;
import org.dspace.core.Context;
import org.dspace.eperson.service.GroupService;
import org.dspace.services.ConfigurationService;
import org.dspace.versioning.service.ViewStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;


public class ViewStatisticsServiceImpl implements ViewStatisticsService {

    @Autowired
    private CrisSecurityService crisSecurityService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private GroupService groupService;

    @Autowired
    @Qualifier("viewUsageStatisticsAccessModesList")
    private List<AccessItemMode> viewUsageStatisticsAccessModes;

    @Override
    public boolean canViewStatistics(Context context, DSpaceObject dso) {
        return viewUsageStatisticsAccessModes.stream()
                .anyMatch(am -> isHasAccess(context, dso, am));
    }

    private boolean isHasAccess(Context context, DSpaceObject dso, AccessItemMode accessItemMode) {
        try {
            if (dso instanceof Item) {
                return crisSecurityService.hasAccess(context, (Item) dso,
                                                     context.getCurrentUser(),
                                                     accessItemMode);
            } else if (dso instanceof Community) {
                return checkGroup(context, "usage-statistics.authorization.community.group");
            } else if (dso instanceof Collection) {
                return checkGroup(context, "usage-statistics.authorization.collection.group");
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean checkGroup(Context context, String propertyName) throws SQLException {
        String[] statsGroups =
            configurationService.getArrayProperty(propertyName);
        return statsGroups.length == 0 || Arrays.stream(statsGroups).anyMatch(g -> member(context, g));
    }

    private boolean member(Context context, String g) {
        try {
            return groupService.isMember(context, g);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
