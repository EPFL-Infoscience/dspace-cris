/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate.service;

import static java.lang.String.valueOf;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.dspace.app.profile.ResearcherProfile;
import org.dspace.app.profile.service.ResearcherProfileService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.indexobject.IndexableCollection;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.GroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ProfileInitializer {

    private static Logger log = LoggerFactory.getLogger(ProfileInitializer.class);

    @Autowired
    private ResearcherProfileService researcherProfileService;

    @Autowired
    private EpflClient client;

    @Autowired
    private ItemService itemService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private WorkspaceItemService workspaceItemService;

    @Autowired
    private InstallItemService installItemService;

    @Autowired
    private SearchService searchService;

    public boolean initialize(Context context, EPerson eperson) {

        if (eperson == null) {
            return false;
        }

        context.turnOffAuthorisationSystem();
        try {
            ResearcherProfile profile = findProfile(context, eperson)
                    .orElseGet(() -> createPrivateProfile(context, eperson));
            Optional<String> persid = getPersid(eperson);
            if (persid.isPresent()) {
                return enrichProfile(context, persid.get(), profile.getItem());
            } else {
                return false;
            }
        } finally {
            context.restoreAuthSystemState();
        }
    }

    public Optional<ResearcherProfile> findProfile(Context context, EPerson eperson) {
        try {
            return Optional.ofNullable(researcherProfileService.findById(context, eperson.getID()));
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException(e);
        }
    }

    private ResearcherProfile createPrivateProfile(Context context, EPerson eperson) {
        try {
            ResearcherProfile profile = researcherProfileService.createAndReturn(context, eperson);
            if (profile.isVisible()) {
                researcherProfileService.changeVisibility(context, profile, false);
            }
            return profile;
        } catch (AuthorizeException | SQLException | SearchServiceException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean enrichProfile(Context context, String persid, Item item) {
        try {
            boolean profileNeedSave = false;
            String legacyId = itemService.getMetadataFirstValue(item, "cris", "legacyId", null, Item.ANY);
            if (!StringUtils.equals(legacyId, persid)) {
                profileNeedSave = true;
                itemService.clearMetadata(context, item, "cris", "legacyId", null, Item.ANY);
                addMetadata(context, item, "cris", "legacyId", null, persid);
            }

            EpflResponse accred = client.getAccred(persid);
            if (accred == null || CollectionUtils.isEmpty(accred.getResult())) {
                log.warn("No accred found by persid " + persid);
                return false;
            }

            Long unitId = accred.getResult().get(0).getUnitid();
            String currUnitName = itemService.getMetadataFirstValue(item,  "person", "affiliation", "name", Item.ANY);
            if (unitId == null) {
                log.warn("No unitid found by persid " + persid);
                if (currUnitName != null) {
                    profileNeedSave = true;
                    itemService.clearMetadata(context, item, "person", "affiliation", "name", Item.ANY);
                }
            } else {
                Item unit = findOrgUnitByCrisLegacyId(context, unitId)
                    .orElseGet(() -> createOrgUnit(context, unitId));

                Group groupUnit = findGroupUnit(context, unitId)
                        .orElseGet(() -> createGroup(context, unitId));
                String unitName = getUnitName(unitId);
                if (!StringUtils.equals(unitName, currUnitName)) {
                    profileNeedSave = true;
                    itemService.clearMetadata(context, item, "person", "affiliation", "name", Item.ANY);
                    addMetadata(context, item, "person", "affiliation", "name", unitName, unit.getID().toString());
                }
            }
            if (profileNeedSave) {
                itemService.update(context, item);
            }
            return profileNeedSave;
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Group> findGroupUnit(Context ctx, Long unitId) {
        try {
            return Optional.ofNullable(groupService.findByName(ctx, getUnitName(unitId)));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Item> findOrgUnitByCrisLegacyId(Context ctx, Long unitId) {
        try {
            return streamOf(itemService.findArchivedByMetadataField(ctx, "cris.legacyId", valueOf(unitId)))
                .filter(item -> "OrgUnit".equals(itemService.getEntityType(item)))
                .findFirst();
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException(e);
        }
    }

    private Item createOrgUnit(Context context, Long unitId) {

        Collection collection = findOrgUnitCollection(context);

        try {
            WorkspaceItem workspaceItem = workspaceItemService.create(context, collection, true);
            Item item = workspaceItem.getItem();
            String value = getUnitName(unitId);
            addMetadata(context, item, "dc", "title", null, value);
            addMetadata(context, item, "cris", "legacyId", null, String.valueOf(unitId));
            return installItemService.installItem(context, workspaceItem);
        } catch (AuthorizeException | SQLException e) {
            throw new RuntimeException(e);
        }

    }

    private Group createGroup(Context context, Long unitId) {
        try {
            Group group = groupService.create(context);
            groupService.setName(group, getUnitName(unitId));
            groupService.update(context, group);
            context.setSpecialGroup(group.getID());
            return group;
        } catch (AuthorizeException | SQLException e) {
            throw new RuntimeException(e);
        }

    }
    private String getUnitName(Long unitId) {
        String value = "UNIT " + unitId;
        return value;
    }

    @SuppressWarnings("rawtypes")
    private Collection findOrgUnitCollection(Context context) {

        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setDSpaceObjectFilter(IndexableCollection.TYPE);
        discoverQuery.addFilterQueries("dspace.entity.type: OrgUnit");

        try {

            DiscoverResult discoverResult = searchService.search(context, discoverQuery);
            List<IndexableObject> indexableObjects = discoverResult.getIndexableObjects();

            if (CollectionUtils.isEmpty(indexableObjects)) {
                throw new RuntimeException("No OrgUnit collection found");
            }

            return (Collection) indexableObjects.get(0).getIndexedObject();

        } catch (SearchServiceException e) {
            throw new RuntimeException(e);
        }

    }

    private Stream<Item> streamOf(Iterator<Item> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    private void addMetadata(Context ctx, Item item, String schema, String element, String qualifier, String value) {
        try {
            itemService.addMetadata(ctx, item, schema, element, qualifier, null, value);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addMetadata(Context ctx, Item item, String schema, String element, String qualifier,
        String value, String authority) {
        try {
            itemService.addMetadata(ctx, item, schema, element, qualifier, null, value, authority, 600);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<String> getPersid(EPerson eperson) {
        return Optional.ofNullable(eperson.getNetid())
            .map(netId -> StringUtils.substringBefore(netId, "@"));
    }

    public EpflClient getClient() {
        return client;
    }

    public void setClient(EpflClient client) {
        this.client = client;
    }

}
