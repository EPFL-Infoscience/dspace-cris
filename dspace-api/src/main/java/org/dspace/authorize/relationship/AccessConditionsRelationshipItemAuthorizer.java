/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.authorize.relationship;

import org.dspace.content.Item;
import org.dspace.content.service.RelationshipManagementService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

public class AccessConditionsRelationshipItemAuthorizer
    implements RelationshipItemAuthorizer {

    @Autowired
    private RelationshipManagementService relationshipManagementService;

    @Override
    public boolean canHandleRelationshipOnItem(Context context, Item item) {
        return relationshipManagementService.canManageRelationships(context, item);
    }
}
