/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.security;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.security.service.CrisSecurityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.util.UUIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link CrisSecurityService}.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class CrisSecurityServiceImpl implements CrisSecurityService {

    @Autowired
    private ItemService itemService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private AuthorizeService authorizeService;

    @Autowired
    private EPersonService ePersonService;

    private static final Logger log = LoggerFactory.getLogger(CrisSecurityServiceImpl.class);

    @Override
    public boolean hasAccess(Context context, Item item, EPerson user, AccessItemMode accessMode) throws SQLException {
        Optional<CrisSecurity> matchingSecurity = accessMode.getSecurities().stream()
                                                 .filter(
                                                     security -> hasAccess(context, item, user, accessMode, security))
                                                 .findFirst();
        if (matchingSecurity.isPresent()) {
            log.info("found matching policy for {} on item {}: {}",
                     user.getID(), item.getID(), matchingSecurity.get().name());
        }
        return matchingSecurity.isPresent();
    }

    private boolean hasAccess(
        Context context, Item item, EPerson user, AccessItemMode accessMode, CrisSecurity crisSecurity
    ) {
        try {
            final boolean checkSecurity = checkSecurity(context, item, user, accessMode, crisSecurity);

            return Optional.ofNullable(accessMode.getAdditionalFilter())
                .map(filter -> checkSecurity && filter.getResult(context, item))
                .orElse(checkSecurity);
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }

    }

    private boolean checkSecurity(Context context, Item item, EPerson user, AccessItemMode accessMode,
                              CrisSecurity crisSecurity) throws SQLException {
        switch (crisSecurity) {
            case ADMIN:
                return authorizeService.isAdmin(context, user);
            case CUSTOM:
                return hasAccessByCustomPolicy(context, item, user, accessMode);
            case GROUP:
                return hasAccessByGroup(context, user, accessMode.getGroups());
            case ITEM_ADMIN:
                return authorizeService.isAdmin(context, user, item);
            case OWNER:
                return isOwner(user, item);
            case SUBMITTER:
                return user != null && user.equals(item.getSubmitter());
            case SUBMITTER_GROUP:
                return isUserInSubmitterGroup(context, item, user);
            case ALL:
                return true;
            case NONE:
            default:
                return false;
        }
    }

    private boolean isOwner(EPerson eperson, Item item) {
        return ePersonService.isOwnerOfItem(eperson, item);
    }

    private boolean hasAccessByCustomPolicy(Context context, Item item, EPerson user, AccessItemMode accessMode)
        throws SQLException {
        return hasAccessByGroupMetadataFields(context, item, user, accessMode.getGroupMetadataFields())
            || hasAccessByUserMetadataFields(context, item, user, accessMode.getUserMetadataFields())
            || hasAccessByItemMetadataFields(context, item, user, accessMode.getItemMetadataFields());
    }

    private boolean hasAccessByGroupMetadataFields(Context context, Item item, EPerson user,
        List<String> groupMetadataFields) throws SQLException {

        if (user == null || CollectionUtils.isEmpty(groupMetadataFields)) {
            return false;
        }

        Set<UUID> specialGroups = context.getSpecialGroupUuids();
        if (!CollectionUtils.isEmpty(specialGroups)) {
            for (UUID group : specialGroups) {
                for (String groupMetadataField : groupMetadataFields) {
                    if (anyMetadataHasAuthorityEqualsTo(item, groupMetadataField, group)) {
                        return true;
                    }
                }
            }
        }

        List<Group> userGroups = user.getGroups();
        if (CollectionUtils.isEmpty(userGroups)) {
            return false;
        }

        for (Group group : userGroups) {
            for (String groupMetadataField : groupMetadataFields) {
                if (anyMetadataHasAuthorityEqualsTo(item, groupMetadataField, group.getID())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasAccessByUserMetadataFields(Context context, Item item, EPerson user,
        List<String> userMetadataFields) throws SQLException {

        if (user == null || CollectionUtils.isEmpty(userMetadataFields)) {
            return false;
        }

        for (String userMetadataField : userMetadataFields) {
            if (anyMetadataHasAuthorityEqualsTo(item, userMetadataField, user.getID())) {
                return true;
            }
        }

        return false;
    }

    private boolean hasAccessByItemMetadataFields(Context context, Item item, EPerson user,
        List<String> itemMetadataFields) throws SQLException {

        if (user == null || CollectionUtils.isEmpty(itemMetadataFields)) {
            return false;
        }

        return findRelatedItems(context, item, itemMetadataFields).stream()
            .anyMatch(relatedItem -> isOwner(user, relatedItem));
    }

    private List<Item> findRelatedItems(Context context, Item item, List<String> itemMetadataFields)
        throws SQLException {

        List<Item> relatedItems = new ArrayList<>();

        if (CollectionUtils.isEmpty(itemMetadataFields)) {
            return relatedItems;
        }

        for (String itemMetadataField : itemMetadataFields) {
            List<MetadataValue> metadataValues = itemService.getMetadataByMetadataString(item, itemMetadataField);
            for (MetadataValue metadataValue : metadataValues) {
                UUID relatedItemId = UUIDUtils.fromString(metadataValue.getAuthority());
                Item relatedItem = itemService.find(context, relatedItemId);
                if (relatedItem != null) {
                    relatedItems.add(relatedItem);
                }
            }
        }

        return relatedItems;

    }

    private boolean hasAccessByGroup(Context context, EPerson user, List<String> groups) throws SQLException {
        if (CollectionUtils.isEmpty(groups)) {
            return false;
        }

        if (user == null) {
            return false;
        }

        List<Group> specialGroups = context.getSpecialGroups();
        List<Group> userGroups = user.getGroups();
        return groups.stream()
                .anyMatch(group -> isInGroupList(group, specialGroups) || isInGroupList(group, userGroups));
    }

    private boolean isInGroupList(String group, List<Group> groups) {
        return groups.stream().anyMatch(g -> StringUtils.containsAny(group, g.getName(), g.getID().toString()));
    }

    private boolean isUserInSubmitterGroup(Context context, Item item, EPerson user) throws SQLException {
        Collection collection = item.getOwningCollection();
        if (collection == null) {
            return false;
        }
        return groupService.isMember(context, user, collection.getSubmitters());
    }

    private boolean anyMetadataHasAuthorityEqualsTo(Item item, String metadata, UUID uuid) {
        return itemService.getMetadataByMetadataString(item, metadata).stream()
            .anyMatch(value -> uuid.toString().equals(value.getAuthority()));
    }

}
