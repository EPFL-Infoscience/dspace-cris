/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.deduplication.service.impl;

import static org.dspace.util.FunctionalUtils.throwingConsumerWrapper;
import static org.dspace.util.FunctionalUtils.throwingMapperWrapper;
import static org.dspace.util.FunctionalUtils.throwingPredicateWrapper;

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
        metadataAuthorityService
            .getAuthorityMetadata()
            .forEach(value -> authorityMetadataFields.add(getElements(value)));
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

        List<Item> itemsToWithdraw = otherItems.isEmpty()
            ? dedupUtils.findGroup(context, deduplicationSetMergeDTO.getSetId())
                        .getItems().stream()
                        .filter(item -> !item.getID().equals(targetItem.getID()))
                        .collect(Collectors.toList())
            : otherItems;

        withdrawOtherItems(context, itemsToWithdraw);
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

    private List<Item> getOtherItems(Context context, DeduplicationSetMergeDTO deduplicationSetMergeDTO) {
        return deduplicationSetMergeDTO
            .getMergedItems().stream()
            .map(this::getUUIDFromUri)
            .map(throwingMapperWrapper(uuid -> itemService.find(context, uuid)))
            .collect(Collectors.toList());
    }

    private List<Bitstream> getBitstreams(Context context,
                                          DeduplicationSetMergeDTO deduplicationSetMergeDTO) throws SQLException {
        return deduplicationSetMergeDTO
            .getBitstreams().stream()
            .map(this::getUUIDFromUri)
            .map(throwingMapperWrapper(uuid -> bitstreamService.find(context, uuid)))
            .collect(Collectors.toList());
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

            itemService.clearMetadata(context, targetItem, fields[0], fields[1], fields[2], Item.ANY);

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
                if (!Objects.isNull(metadataValue) && !isListContainsMetadataValue(metadataValues, metadataValue)) {
                    metadataValues.add(metadataValue);
                }
            }
        }

        return metadataValues;
    }

    private boolean isListContainsMetadataValue(List<MetadataValue> metadataValues, MetadataValue metadataValue) {
        return metadataValues
            .stream()
            .map(MetadataValue::getValue)
            .anyMatch(value -> value.equals(metadataValue.getValue()));
    }

    private List<DeduplicationMetadataSourcesDTO> sortSourcesAscByPosition(
        List<DeduplicationMetadataSourcesDTO> sources) {
        return sources
            .stream()
            .sorted(Comparator.comparingInt(DeduplicationMetadataSourcesDTO::getPlace))
            .collect(Collectors.toList());
    }

    private MetadataValue getMatchedMetadataValueFromItem(Item item, int position, String metadataField) {
        return itemService
            .getMetadataByMetadataString(item, metadataField)
            .stream()
            .filter(mv -> mv.getPlace() == position)
            .peek(mv -> mv.setPlace(-1))
            .findFirst()
            .orElse(null);
    }

    private void updateItemBitstreams(Context context, Item targetItem, List<Item> otherItems,
                                      List<Bitstream> bitstreams) throws SQLException, AuthorizeException, IOException {
        for (Bitstream bitstream : bitstreams) {
            Set<Bundle> bundles = getMatchedBundlesFromOtherItems(otherItems, bitstream.getBundles());
            for (Bundle bundle : bundles) {
                List<Bundle> targetBundles = targetItem.getBundles(bundle.getName());
                if (!targetBundles.isEmpty()) {
                    addBitstreamAndReplacePolicies(context, targetBundles.get(0), bitstream);
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

    private void addBitstreamAndReplacePolicies(Context context, Bundle targetBundle, Bitstream bitstream)
        throws SQLException, AuthorizeException {
        if (isListContainsBitstream(targetBundle.getBitstreams(), bitstream)) {
            return;
        }

        List<ResourcePolicy> bundlePolicies = bundleService.getBundlePolicies(context, targetBundle);
        bundleService.addBitstream(context, targetBundle, bitstream);
        bundleService.replaceAllBitstreamPolicies(context, targetBundle, bundlePolicies);
        bundleService.update(context, targetBundle);
    }

    private boolean isListContainsBitstream(List<Bitstream> bitstreams, Bitstream bitstream) {
        return bitstreams
            .stream()
            .map(Bitstream::getChecksum)
            .anyMatch(checksum -> checksum.equals(bitstream.getChecksum()));
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
        boolean bitstreamFound;
        List<Bundle> bundles = item.getBundles();
        for (Bundle bundle : bundles) {
            for (Bitstream itemBitstream : bundle.getBitstreams()) {
                bitstreamFound = false;
                for (Bitstream mergedBitstream : bitstreams) {
                    if (itemBitstream.getID().toString().equals(mergedBitstream.getID().toString())) {
                        bitstreamFound = true;
                        break;
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
                        if (!metadataValues.isEmpty()) {
                            replaceAuthority(context, item, targetItem, otherItem, metadataField, metadataValues);
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
                                                                     metadataField[2], Item.ANY);
        return metadataValues
            .stream()
            .anyMatch(
                metadataValue -> Optional
                    .ofNullable(metadataValue.getAuthority())
                    .map(authority -> authority.equals(otherItem.getID().toString()))
                    .isPresent()
            )
            ? metadataValues
            : new ArrayList<>();
    }

    private void replaceAuthority(Context context, Item item, Item targetItem, Item otherItem,
                                  String[] metadataField, List<MetadataValue> metadataValues)
        throws SQLException, AuthorizeException {
        metadataValues
            .stream()
            .filter(mv -> mv.getAuthority() != null)
            .filter(mv -> mv.getAuthority().equals(otherItem.getID().toString()))
            .forEach(mv -> {
                mv.setAuthority(targetItem.getID().toString());
                mv.setValue(targetItem.getName());
            });
        updateItemMetaDataValues(context, item, metadataValues, metadataField);
    }

    private void updateItemMetaDataValues(Context context, Item item,
                                          List<MetadataValue> metadataValues, String[] metadataField)
        throws SQLException, AuthorizeException {
        itemService.clearMetadata(context, item, metadataField[0], metadataField[1], metadataField[2], null);
        metadataValues.forEach(throwingConsumerWrapper(
            mv -> itemService.addMetadata(
                context, item, mv.getSchema(), mv.getElement(), mv.getQualifier(), mv.getLanguage(), mv.getValue(),
                mv.getAuthority(), mv.getConfidence(), mv.getPlace()
            )
        ));
        itemService.update(context, item);
    }

    private void addUriMetadataToOtherItems(Context context, Item targetItem, List<Item> otherItems) {
        Optional
            .ofNullable(itemService.getMetadata(targetItem, "dc.identifier.uri"))
            .ifPresent(targetUri -> otherItems
                .forEach(throwingConsumerWrapper(
                    item -> itemService.addMetadata(context, item,"dspace", "merge", "target-uri",
                                                    null, List.of(targetUri))
                ))
            );
    }

    @SuppressWarnings({ "unchecked" })
    private void withdrawOtherItems(Context context, List<Item> otherItems) {
        otherItems.forEach(throwingConsumerWrapper(item -> {
            Optional<WorkflowItem> workflowItem = Optional.ofNullable(workflowItemService.findByItem(context, item));
            Optional<WorkspaceItem> workspaceItem = Optional.ofNullable(workspaceItemService.findByItem(context, item));

            workflowItem.ifPresent(throwingConsumerWrapper(wi -> workflowItemService.delete(context, wi)));
            workspaceItem.ifPresent(throwingConsumerWrapper(wsi -> workspaceItemService.deleteAll(context, wsi)));

            if (workflowItem.isEmpty() && workspaceItem.isEmpty()) {
                itemService.withdraw(context, item);
            }
        }));
    }

    private void removeItemsFromSet(Context context, String setId) throws SearchServiceException, SQLException {
        Optional
            .ofNullable(dedupUtils.findGroup(context, setId))
            .ifPresent(duplicateInfo -> duplicateInfo
                .getItems()
                .forEach(throwingConsumerWrapper(
                    item -> dedupUtils.rejectAdminDups(context, duplicateInfo, item.getID())
                ))
            );
    }

    private void createRelationships(Context context, Item leftItem, List<Item> rightItems) {
        rightItems
            .stream()
            .filter(throwingPredicateWrapper(rightItem -> itemService.find(context, rightItem.getID()) != null))
            .forEach(throwingConsumerWrapper(rightItem -> createRelationship(context, leftItem, rightItem)));
    }

    private void createRelationship(Context context, Item leftItem, Item rightItem)
        throws SQLException, AuthorizeException {
        EntityType leftEntityType = entityTypeService.findByItem(context, leftItem);
        EntityType rightEntityType = entityTypeService.findByItem(context, rightItem);
        RelationshipType relationshipType = relationshipTypeService
            .findbyTypesAndTypeName(context, leftEntityType, rightEntityType, "isMergedFromItem", "isMergedInItem");
        relationshipService.create(context, leftItem, rightItem, relationshipType, false);
    }

    private List<Item> getItemsToMerge(Context context, DeduplicationMerge deduplicationMerge) {
        return deduplicationMerge
            .getMergedItems().stream()
            .map(UUIDUtils::fromString)
            .map(throwingMapperWrapper(uuid -> itemService.find(context, uuid)))
            .distinct()
            .collect(Collectors.toList());
    }

    private void replaceItemExistedMetadata(Context context, Item targetItem,
                                            List<Item> itemsToMerge, List<String> replacedNotEmptyMetadata) {
        replacedNotEmptyMetadata
            .stream()
            .map(metadataField ->
                Map.entry(
                    metadataField,
                    itemsToMerge
                        .stream()
                        .flatMap(itemToMerge -> filterEmpty(getItemMetadataValues(itemToMerge, metadataField).stream()))
                        .collect(Collectors.toList())
                )
            )
            .filter(entry -> !CollectionUtils.isEmpty(entry.getValue()))
            .forEach(throwingConsumerWrapper(
                entry -> updateItemMetaDataValues(context, targetItem, entry.getValue(), getElements(entry.getKey())),
                "Error while replacing metadata for deduplication!")
            );
    }

    private void replaceItemMetadata(Context context, Item targetItem,
                                     List<Item> mergedItems, List<String> replacedMetadata) {
        replacedMetadata.forEach(throwingConsumerWrapper(
            metadataField -> updateItemMetaDataValues(
                context, targetItem,
                getItemsMetadataValues(mergedItems, metadataField),
                getElements(metadataField)
            )
        ));
    }

    private void appendItemExistedMetadata(Context context, Item targetItem,
                                           List<Item> mergedItems, List<String> appendedMetadata)
        throws SQLException, AuthorizeException {
        List<MetadataValue> metadataValues;
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
        return metadatas.filter(meta -> StringUtils.isNotEmpty(meta.getValue()));
    }

    private boolean hasValidMetadatas(List<MetadataValue> metadatas) {
        return Optional
            .ofNullable(metadatas)
            .filter(list -> !list.isEmpty())
            .flatMap(list -> filterEmpty(list.stream()).findAny())
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

    private void deleteMergedItems(Context context, List<Item> mergedItems) {
        mergedItems.forEach(throwingConsumerWrapper(item -> itemService.delete(context, item)));
    }

}
