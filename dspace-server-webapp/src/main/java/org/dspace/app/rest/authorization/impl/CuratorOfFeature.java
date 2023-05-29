/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.authorization.impl;

import java.sql.SQLException;

import org.dspace.app.rest.authorization.AuthorizationFeature;
import org.dspace.app.rest.authorization.AuthorizationFeatureDocumentation;
import org.dspace.app.rest.model.BaseObjectRest;
import org.dspace.app.rest.model.SiteRest;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@AuthorizationFeatureDocumentation(name = CuratorOfFeature.NAME, description = "Curator Authorization Feature")
public class CuratorOfFeature implements AuthorizationFeature {

    public static final String NAME = "curatorOf";

    private static final String CURATORS_GROUP_NAME = "Curators";

    @Autowired
    AuthorizeService authService;

    @Override
    public boolean isAuthorized(Context context, BaseObjectRest object) throws SQLException {
        return object instanceof SiteRest && context.getCurrentUser()
                                                    .getGroups()
                                                    .stream()
                                                    .anyMatch(group -> group.getName().equals(CURATORS_GROUP_NAME));
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[] {
            SiteRest.CATEGORY + "." + SiteRest.NAME
        };
    }
}
