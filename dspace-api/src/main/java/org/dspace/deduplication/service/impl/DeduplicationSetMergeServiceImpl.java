/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.deduplication.service.impl;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.deduplication.model.DeduplicationMerge;
import org.dspace.app.deduplication.model.DeduplicationSetMerge;
import org.dspace.app.deduplication.utils.DedupUtils;
import org.dspace.app.deduplication.utils.DuplicateInfo;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.EntityType;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.Relationship;
import org.dspace.content.RelationshipType;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.authority.service.MetadataAuthorityService;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.EntityTypeService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.RelationshipService;
import org.dspace.content.service.RelationshipTypeService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.deduplication.dto.DeduplicationMetadataDTO;
import org.dspace.deduplication.dto.DeduplicationMetadataSourcesDTO;
import org.dspace.deduplication.dto.DeduplicationSetMergeDTO;
import org.dspace.deduplication.service.DeduplicationSetMergeService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.util.UUIDUtils;
import org.dspace.workflow.WorkflowItem;
import org.dspace.workflow.WorkflowItemService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link DeduplicationSetMergeService}.
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 *
 */
public class DeduplicationSetMergeServiceImpl implements DeduplicationSetMergeService {

    private final Logger log = org.apache.logging.log4j.LogManager.getLogger(DeduplicationSetMergeServiceImpl.class);

    @Autowired
    private ItemService itemService;

    @Autowired
    private RelationshipService relationshipService;

    @Autowired
    private RelationshipTypeService relationshipTypeService;

    @Autowired
    private EntityTypeService entityTypeService;

    @Autowired
    private DedupUtils dedupUtils;

    @Autowired
    private BitstreamService bitstreamService;

    @Autowired
    private MetadataAuthorityService metadataAuthorityService;

    @Autowired
    private BundleService bundleService;

    @Autowired
    private WorkflowItemService workflowItemService;

    @Autowired
    private WorkspaceItemService workspaceItemService;

    private final List<String[]> authorityMetadataFields = new ArrayList<>();

    @PostConstruct
    private void init() {
        List<String> values = metadataAuthorityService.getAuthorityMetadata();
        for (String value : values) {
            authorityMetadataFields.add(getElements(value));
        }
    }

    private String[] getElements(String fieldName) {
        String[] tokens = StringUtils.split(fieldName, ".");
        int add = 4 - tokens.length;
        if (add > 0) {
            tokens = ArrayUtils.addAll(tokens, new String[add]);
        }
        return tokens;
    }

    @Override
    public DeduplicationSetMerge merge(Context context, UUID targetUUID,
                                       DeduplicationSetMergeDTO deduplicationSetMergeDTO)
        throws SQLException, AuthorizeException, SearchServiceException, IOException {

        Item targetItem;
        List<Item> otherItems;
        List<Bitstream> bitstreams;

        targetItem = getTargetItem(context, targetUUID);
        otherItems = getOtherItems(context, deduplicationSetMergeDTO);
        bitstreams = getBitstreams(context, deduplicationSetMergeDTO);

        updateItemMetaDataValues(context, targetItem, deduplicationSetMergeDTO.getMetadata());
        updateItemBitstreams(context, targetItem, otherItems, bitstreams);

        updateRelationships(context, targetItem, otherItems);
        updateAuthorities(context, targetItem, otherItems);

        addUriMetadataToOtherItems(context, targetItem, otherItems);

        withdrawOtherItems(context, otherItems);
        removeItemsFromSet(context, deduplicationSetMergeDTO.getSetId());

        createRelationships(context, targetItem, otherItems);

        return new DeduplicationSetMerge(otherItems, bitstreams, itemService.find(context, targetItem.getID()));

    }

    @Override
    public void merge(Context context, DeduplicationMerge deduplicationMerge)
        throws SQLException, AuthorizeException, SearchServiceException, IOException {
        Item targetItem = getTargetItem(context, UUIDUtils.fromString(deduplicationMerge.getTargetItem()));
        List<Item> itemsToMerge = getItemsToMerge(context, deduplicationMerge);

        if (!deduplicationMerge.isExclude()) {
            replaceItemExistedMetadata(context, targetItem, itemsToMerge,
                deduplicationMerge.getReplacedNotEmptyMetadata());

            replaceItemMetadata(context, targetItem, itemsToMerge,
                deduplicationMerge.getReplacedMetadata());

            appendItemExistedMetadata(context, targetItem, itemsToMerge,
                deduplicationMerge.getAppendedMetadata());
        }

        updateRelationships(context, targetItem, itemsToMerge);
        updateAuthorities(context, targetItem, itemsToMerge);

        if (deduplicationMerge.isDelete()) {
            deleteMergedItems(context, itemsToMerge);
        } else {
            withdrawOtherItems(context, itemsToMerge);
        }

        createRelationships(context, targetItem, itemsToMerge);
    }

    private Item getTargetItem(Context context, UUID targetUUID) throws SQLException {
        return itemService.find(context, targetUUID);
    }

    private List<Item>  getOtherItems(Context context,
                                      DeduplicationSetMergeDTO deduplicationSetMergeDTO) throws SQLException {
        List<Item> items = new ArrayList<>();
        for (String itemUri : deduplicationSetMergeDTO.getMergedItems()) {
            items.add(itemService.find(context, getUUIDFromUri(itemUri)));
        }
        return items;
    }

    private List<Bitstream> getBitstreams(Context context,
                                          DeduplicationSetMergeDTO deduplicationSetMergeDTO) throws SQLException {
        List<Bitstream> bitstreams = new ArrayList<>();
        for (String bitstreamUri : deduplicationSetMergeDTO.getBitstreams()) {
            bitstreams.add(bitstreamService.find(context, getUUIDFromUri(bitstreamUri)));
        }
        return bitstreams;
    }

    private UUID getUUIDFromUri(String uri) {
        String path = URI.create(uri).getPath();
        return UUIDUtils.fromString(path.substring(path.lastIndexOf("/") + 1));
    }

    private void updateItemMetaDataValues(Context context, Item targetItem,
                                          List<DeduplicationMetadataDTO> metadataList)
        throws SQLException, AuthorizeException {

        List<MetadataValue> metadataValues;
        String [] fields;

        for (DeduplicationMetadataDTO metadataDTO : metadataList) {
            metadataValues = getMetadataValues(context, metadataDTO);
            fields = getElements(metadataDTO.getMetadataField());

            itemService.clearMetadata(context, targetItem, fields[0], fields[1], fields[2], null);

            for (MetadataValue metadataValue : metadataValues) {
                itemService.addMetadata(context, targetItem, metadataValue.getSchema(), metadataValue.getElement(),
                    metadataValue.getQualifier(), metadataValue.getLanguage(),
                    metadataValue.getValue(), metadataValue.getAuthority(), metadataValue.getConfidence());
            }
        }

        itemService.update(context, targetItem);

    }

    private List<MetadataValue> getMetadataValues(Context context, DeduplicationMetadataDTO metadataDTO)
        throws SQLException {

        List<MetadataValue> metadataValues = new ArrayList<>();
        List<DeduplicationMetadataSourcesDTO> sortedSources = sortSourcesAscByPosition(metadataDTO.getSources());

        for (DeduplicationMetadataSourcesDTO source : sortedSources) {
            if (source.getItem() != null) {
                Item item = itemService.find(context, getUUIDFromUri(source.getItem()));
                MetadataValue metadataValue = getMatchedMetadataValueFromItem(item, source.getPlace(),
                    metadataDTO.getMetadataField());
                if (!Objects.isNull(metadataValue)) {
                    metadataValues.add(metadataValue);
                }
            }
        }

        return metadataValues;
    }

    private List<DeduplicationMetadataSourcesDTO> sortSourcesAscByPosition(
        List<DeduplicationMetadataSourcesDTO> sources) {
        return sources.stream()
                      .sorted(Comparator.comparingInt(DeduplicationMetadataSourcesDTO::getPlace))
                      .collect(Collectors.toList());
    }

    private MetadataValue getMatchedMetadataValueFromItem(Item item, int position, String metadataField) {
        List<MetadataValue> metadataValues = itemService.getMetadataByMetadataString(item, metadataField);
        for (MetadataValue metadataValue : metadataValues) {
            if (metadataValue.getPlace() == position) {
                metadataValue.setPlace(-1);
                return metadataValue;
            }
        }
        return null;
    }

    private void updateItemBitstreams(Context context, Item targetItem, List<Item> otherItems,
                                      List<Bitstream> bitstreams) throws SQLException, AuthorizeException, IOException {

        for (Bitstream bitstream : bitstreams) {
            Set<Bundle> bundles = getMatchedBundlesFromOtherItems(otherItems, bitstream.getBundles());
            for (Bundle bundle : bundles) {
                Bundle targetBundle = getTargetBundle(targetItem, bundle.getName());
                if (targetBundle != null) {
                    addBitstreamAndReplacePolicies(context, targetBundle, bitstream);
                } else {
                    createBundleAndAddBitstream(context, targetItem, bitstream, bundle);
                }
            }
        }

        removeUnMergedBitstreamsFromTargetItem(context, targetItem, bitstreams);

    }

    private Set<Bundle> getMatchedBundlesFromOtherItems(List<Item> otherItems, List<Bundle> bundles) {
        Set<Bundle> matchedBundles = new HashSet<>();
        for (Item item : otherItems) {
            for (Bundle bundle : item.getBundles()) {
                for (Bundle bundle1 : bundles) {
                    if (bundle.getID().toString().equals(bundle1.getID().toString())) {
                        matchedBundles.add(bundle1);
                    }
                }
            }
        }
        return matchedBundles;
    }

    private Bundle getTargetBundle(Item targetItem, String name) {
        List<Bundle> targetBundles = targetItem.getBundles(name);
        return findFirstBundle(targetBundles);
    }

    private Bundle findFirstBundle(List<Bundle> bundles) {
        if (!bundles.isEmpty()) {
            return bundles.get(0);
        }
        return null;
    }

    private void addBitstreamAndReplacePolicies(Context context, Bundle targetBundle, Bitstream bitstream)
        throws SQLException, AuthorizeException {
        List<ResourcePolicy> bundlePolicies = bundleService.getBundlePolicies(context, targetBundle);
        bundleService.addBitstream(context, targetBundle, bitstream);
        bundleService.replaceAllBitstreamPolicies(context, targetBundle, bundlePolicies);
        bundleService.update(context, targetBundle);
    }

    private void createBundleAndAddBitstream(Context context, Item targetItem, Bitstream bitstream, Bundle oldBundle)
        throws SQLException, AuthorizeException {
        Bundle newBundle = bundleService.create(context, targetItem, oldBundle.getName());
        bundleService.addBitstream(context, newBundle, bitstream);
        bundleService.inheritCollectionDefaultPolicies(context, newBundle, targetItem.getOwningCollection());
        bundleService.update(context, newBundle);
    }

    private void removeUnMergedBitstreamsFromTargetItem(Context context, Item item, List<Bitstream> bitstreams)
        throws SQLException, AuthorizeException, IOException {
        boolean bitstreamFound = false;
        List<Bundle> bundles = item.getBundles();
        for (Bundle bundle : bundles) {
            for (Bitstream itemBitstream : bundle.getBitstreams()) {
                bitstreamFound = false;
                for (Bitstream mergedBitstream : bitstreams) {
                    if (itemBitstream.getID().toString().equals(mergedBitstream.getID().toString())) {
                        bitstreamFound = true;
                    }
                }
                if (!bitstreamFound) {
                    bundleService.removeBitstream(context, bundle, itemBitstream);
                    bundleService.update(context, bundle);
                }
            }
        }
    }

    private void updateRelationships(Context context, Item targetItem, List<Item> otherItems)
        throws SQLException, AuthorizeException {
        for (Item item : otherItems) {
            List<Relationship> relationships =  relationshipService.findByItem(context, item);
            for (Relationship relationship : relationships) {
                if (item.getID() == relationship.getLeftItem().getID()) {
                    relationship.setLeftItem(targetItem);
                    relationshipService.update(context, relationship);
                } else if (item.getID() == relationship.getRightItem().getID()) {
                    relationship.setRightItem(targetItem);
                    relationshipService.update(context, relationship);
                }
                relationshipService.find(context, relationship.getID());
            }
        }
    }

    private void updateAuthorities(Context context, Item targetItem, List<Item> otherItems) {
        for (String [] metadataField : authorityMetadataFields) {
            for (Item otherItem : otherItems) {
                try {
                    Iterator<Item> items = itemService.findByAuthorityValue(context, metadataField[0], metadataField[1],
                        metadataField[2], otherItem.getID().toString());
                    while (items.hasNext()) {
                        Item item = items.next();
                        List<MetadataValue> metadataValues = findMatchedByAuthority(item, otherItem, metadataField);
                        if (metadataValues.size() > 0) {
                            replaceAuthority(context, item,
                                targetItem, otherItem, metadataField, metadataValues);
                        }
                    }
                } catch (Exception e) {
                    log.warn(e.getMessage(), e);
                }
            }
        }
    }

    private List<MetadataValue> findMatchedByAuthority(Item item, Item otherItem, String[] metadataField) {

        List<MetadataValue> metadataValues = itemService.getMetadata(item, metadataField[0], metadataField[1],
            metadataField[2], null);

        for (MetadataValue metadataValue : metadataValues) {
            if (metadataValue.getAuthority() != null
                && metadataValue.getAuthority().equals(otherItem.getID().toString())) {
                return metadataValues;
            }
        }
        return new ArrayList<>();
    }

    private void replaceAuthority(Context context, Item item,
                                  Item targetItem, Item otherItem,
                                  String[] metadataField, List<MetadataValue> metadataValues)
        throws SQLException, AuthorizeException {

        for (MetadataValue metadataValue : metadataValues) {
            if (metadataValue.getAuthority() != null
                && metadataValue.getAuthority().equals(otherItem.getID().toString())) {
                metadataValue.setAuthority(targetItem.getID().toString());
                metadataValue.setValue(targetItem.getName());
            }
        }
        updateItemMetaDataValues(context, item, metadataValues, metadataField);
    }

    private void updateItemMetaDataValues(
        Context context, Item item, List<MetadataValue> metadataValues, String[] metadataField
    ) throws SQLException, AuthorizeException {

        itemService.clearMetadata(context, item, metadataField[0], metadataField[1], metadataField[2], null);
        for (MetadataValue metadataValue : metadataValues) {
            itemService.addMetadata(context, item, metadataValue.getSchema(), metadataValue.getElement(),
                metadataValue.getQualifier(), metadataValue.getLanguage(), metadataValue.getValue(),
                metadataValue.getAuthority(), metadataValue.getConfidence(), metadataValue.getPlace());
        }

        itemService.update(context, item);
    }

    private void addUriMetadataToOtherItems(Context context, Item targetItem, List<Item> otherItems)
        throws SQLException {
        String targetUri = itemService.getMetadata(targetItem, "dc.identifier.uri");
        if (targetUri != null) {
            for (Item item : otherItems) {
                itemService.addMetadata(context, item,"dspace", "merge", "target-uri",
                    null, List.of(targetUri));
            }
        }
    }

    private void withdrawOtherItems(Context context, List<Item> otherItems)
        throws SQLException, AuthorizeException, IOException {
        for (Item item : otherItems) {

            WorkflowItem workflowItem = workflowItemService.findByItem(context, item);
            WorkspaceItem workspaceItem = workspaceItemService.findByItem(context, item);

            if (workflowItem != null) {
                workflowItemService.delete(context, workflowItem);
            }

            if (workspaceItem != null) {
                workspaceItemService.deleteAll(context, workspaceItem);
            }

            if (workflowItem == null && workspaceItem == null) {
                itemService.withdraw(context, item);
            }
        }
    }

    private void removeItemsFromSet(Context context, String setId)
        throws SearchServiceException, SQLException, AuthorizeException {
        DuplicateInfo duplicateInfo = dedupUtils.findGroup(context, setId);
        if (duplicateInfo != null) {
            for (Item item : duplicateInfo.getItems()) {
                dedupUtils.rejectAdminDups(context, duplicateInfo, item.getID(), Constants.ITEM);
            }
        }
    }

    private void createRelationships(Context context, Item leftItem, List<Item> rightItems)
        throws SQLException, AuthorizeException {
        for (Item rightItem : rightItems) {
            if (itemService.find(context, rightItem.getID()) == null) {
                continue;
            }
            createRelationship(context, leftItem, rightItem);
        }
    }

    private void createRelationship(Context context, Item leftItem, Item rightItem)
        throws SQLException, AuthorizeException {
        EntityType leftEntityType = entityTypeService.findByItem(context, leftItem);
        EntityType rightEntityType = entityTypeService.findByItem(context, rightItem);
        RelationshipType relationshipType =
            relationshipTypeService
                .findbyTypesAndTypeName(context, leftEntityType, rightEntityType, "isMergedFromItem", "isMergedInItem");
        relationshipService.create(context, leftItem, rightItem, relationshipType, false);
    }

    private List<Item> getItemsToMerge(Context context,
                                     DeduplicationMerge deduplicationMerge) throws SQLException {
        Set<Item> mergedItems = new HashSet<>();
        for (String mergedItem : deduplicationMerge.getMergedItems()) {
            mergedItems.add(itemService.find(context, UUIDUtils.fromString(mergedItem)));
        }
        return new ArrayList<Item>(mergedItems);
    }

    private void replaceItemExistedMetadata(Context context, Item targetItem,
                                         List<Item> itemsToMerge, List<String> replacedNotEmptyMetadata)
        throws SQLException, AuthorizeException {
        replacedNotEmptyMetadata
            .stream()
            .map(metadataField ->
                Map.entry(
                    metadataField,
                    itemsToMerge
                        .stream()
                        .flatMap(itemToMerge ->
                            filterEmpty(getItemMetadataValues(itemToMerge, metadataField).stream())
                        )
                        .collect(Collectors.toList())
                )
            )
            .filter(entry -> !CollectionUtils.isEmpty(entry.getValue()))
            .forEach(entry -> {
                try {
                    updateItemMetaDataValues(
                        context, targetItem,
                        entry.getValue(),
                        getElements(entry.getKey())
                    );
                } catch (SQLException | AuthorizeException e) {
                    throw new RuntimeException("Error while replacing metadata for deduplication!", e);
                }
            });
    }

    private void replaceItemMetadata(Context context, Item targetItem,
                                            List<Item> mergedItems, List<String> replacedMetadata)
        throws SQLException, AuthorizeException {
        for (String metadataField : replacedMetadata) {
            updateItemMetaDataValues(
                context, targetItem,
                getItemsMetadataValues(mergedItems, metadataField),
                getElements(metadataField)
            );
        }
    }

    private void appendItemExistedMetadata(Context context, Item targetItem,
                                            List<Item> mergedItems, List<String> appendedMetadata)
        throws SQLException, AuthorizeException {
        List<MetadataValue> metadataValues = null;
        for (String metadataField : appendedMetadata) {
            List<MetadataValue> itemMetadataValues =
                filterEmpty(getItemMetadataValues(targetItem, metadataField).stream())
                    .collect(Collectors.toList());
            if (hasValidMetadatas(itemMetadataValues)) {
                metadataValues = new ArrayList<>();
                metadataValues.addAll(itemMetadataValues);
                metadataValues.addAll(getItemsMetadataValues(mergedItems, metadataField));
                updateItemMetaDataValues(context, targetItem, metadataValues, getElements(metadataField));
            }
        }
    }

    private Stream<MetadataValue> filterEmpty(Stream<MetadataValue> metadatas) {
        return metadatas
            .filter(meta -> StringUtils.isNotEmpty(meta.getValue()));
    }

    private boolean hasValidMetadatas(List<MetadataValue> metadatas) {
        return Optional.ofNullable(metadatas)
                .filter(list -> !list.isEmpty())
                .map(list -> filterEmpty(list.stream()).findAny().orElse(null))
                .isPresent();
    }

    private List<MetadataValue> getItemsMetadataValues(List<Item> mergedItems, String metadataFiled) {
        return mergedItems
            .stream()
            .flatMap(item -> this.getItemMetadataValues(item, metadataFiled).stream())
            .collect(Collectors.toList());
    }

    private List<MetadataValue> getItemMetadataValues(Item item, String metadataFiled) {
        String[] elements = getElements(metadataFiled);
        return itemService.getMetadata(item, elements[0], elements[1], elements[2], null);
    }

    private void deleteMergedItems(Context context, List<Item> mergedItems)
        throws SQLException, AuthorizeException, IOException {
        for (Item item : mergedItems) {
            itemService.delete(context, item);
        }
    }

}
