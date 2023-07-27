/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.security;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.security.service.CrisSecurityService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.RelationshipManagementService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class RelationshipManagementServiceImpl implements RelationshipManagementService {

    @Autowired
    private ItemService itemService;

    @Autowired
    @Qualifier("relationshipsManagementAccessPolicies")
    private Map<String, List<AccessItemMode>> accessModes;

    @Autowired
    private CrisSecurityService crisSecurityService;

    @Override
    public boolean canManageRelationships(Context context, DSpaceObject dSpaceObject) {
        return hasAccessMode(context, dSpaceObject);
    }

    private boolean hasAccessMode(Context context, DSpaceObject dSpaceObject) {
        Item item = (Item) dSpaceObject;
        String entityType = itemService.getEntityType(item);
        return accessModes.get(entityType.toLowerCase()).stream().anyMatch(am -> hasAccess(context, am, item));
    }

    private boolean hasAccess(Context context, AccessItemMode am, Item item) {
        try {
            return crisSecurityService.hasAccess(context, item, context.getCurrentUser(), am);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
