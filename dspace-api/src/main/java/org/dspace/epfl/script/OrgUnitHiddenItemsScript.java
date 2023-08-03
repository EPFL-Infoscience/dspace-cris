/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.RelationshipType;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.RelationshipService;
import org.dspace.content.service.RelationshipTypeService;
import org.dspace.core.Context;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;

public class OrgUnitHiddenItemsScript
    extends DSpaceRunnable<OrgUnitHiddenItemsScriptConfiguration<OrgUnitHiddenItemsScript>> {

    private ItemService itemService;
    private RelationshipService relationshipService;
    private RelationshipTypeService relationshipTypeService;
    private Context context;

    @Override
    public void setup() throws ParseException {
        itemService = ContentServiceFactory.getInstance().getItemService();
        relationshipService = ContentServiceFactory.getInstance().getRelationshipService();
        relationshipTypeService = ContentServiceFactory.getInstance().getRelationshipTypeService();
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        context.turnOffAuthorisationSystem();

        try {
            createHiddenRelationships();
            context.complete();
            context.restoreAuthSystemState();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        }
    }

    private void createHiddenRelationships() throws SQLException, AuthorizeException {
        Iterator<Item> items = itemService
            .findUnfilteredByMetadataField(context, "epfl", "relation", "rejectedOrgUnit", Item.ANY);

        while (items.hasNext()) {
            Item item = items.next();

            String rejectedOrgUnitAuthority =
                itemService.getMetadata(item, "epfl", "relation", "rejectedOrgUnit", Item.ANY)
                           .get(0)
                           .getAuthority();

            Item orgUnit = itemService.find(context, UUID.fromString(rejectedOrgUnitAuthority));

            if (orgUnit != null) {
                List<RelationshipType> relationshipType = getRelationshipType(item);
                for (RelationshipType type : relationshipType) {
//                    relationshipService.create(context, item, orgUnit, type, false);
                    relationshipService.create(context, item, orgUnit, type,
                                               0, 0, type.getLeftwardType(), type.getRightwardType());
                }
            }
        }
    }

    private List<RelationshipType> getRelationshipType(Item item) throws SQLException {
        switch (itemService.getEntityType(item)) {
            case "Publication":
                return List.of(getRelationshipType("isPublicationsHiddenFor"),
                               getRelationshipType("isRppublicationsHiddenFor"));
            case "Product":
                return List.of(getRelationshipType("isProductsHiddenFor"));
            case "Patent":
                return List.of(getRelationshipType("isPatentsHiddenFor"));
            default:
                return List.of();
        }
    }

    private RelationshipType getRelationshipType(String leftwardTypeName) throws SQLException {
        return relationshipTypeService
            .findByLeftwardOrRightwardTypeName(context, leftwardTypeName)
            .stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException(
                "No RelationshipType found for leftwardTypeName: " + leftwardTypeName));
    }

    private void assignCurrentUserInContext() throws SQLException {
        if (getEpersonIdentifier() != null) {
            context.setCurrentUser(EPersonServiceFactory.getInstance()
                                                        .getEPersonService()
                                                        .find(context, getEpersonIdentifier()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public OrgUnitHiddenItemsScriptConfiguration<OrgUnitHiddenItemsScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("orgunit-hidden-relationships",
                                                                 OrgUnitHiddenItemsScriptConfiguration.class);
    }
}
