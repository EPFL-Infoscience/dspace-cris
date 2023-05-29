/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.rest.security;

import java.sql.SQLException;

import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.core.Context;
import org.dspace.eperson.service.GroupService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("groupsSecurity")
public class GroupsSecurityBean {

    private static final Logger logger = LoggerFactory.getLogger(GroupsSecurityBean.class);
    @Autowired
    private GroupService groupService;

    @Autowired
    private RequestService requestService;

    @Autowired
    private ConfigurationService configurationService;

    public boolean isCurator() {

        Context context = ContextUtil.obtainContext(
            requestService.getCurrentRequest().getHttpServletRequest());

        try {
            return groupService.isMember(context, configurationService.getProperty("epfl.curators-group.name"));
        } catch (SQLException e) {
            logger.error("Unable to check authorization based on membership to curators group", e);
            return false;
        }
    }
}
