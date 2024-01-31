/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.enhancer.impl;

import static org.dspace.util.FunctionalUtils.throwingConsumerWrapper;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.enhancer.AbstractItemEnhancer;
import org.dspace.content.service.ItemService;
import org.dspace.content.vo.MetadataValueVO;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.util.UUIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ParentOrganizationsItemEnhancer extends AbstractItemEnhancer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParentOrganizationsItemEnhancer.class);

    @Autowired
    private ItemService itemService;

    private String sourceEntityType;

    private List<String> sourceItemMetadataFields;

    private String relatedItemMetadataField;

    @Override
    public boolean canEnhance(Context context, Item item) {
        return sourceEntityType == null || sourceEntityType.equals(itemService.getEntityType(item));
    }

    @Override
    public boolean enhance(Context context, Item item, boolean deepMode) {
        try {
            cleanObsoleteVirtualFields(context, item);
            performEnhancement(context, item);
        } catch (SQLException e) {
            LOGGER.error("An error occurs enhancing item with id {}: {}", item.getID(), e.getMessage(), e);
            throw new SQLRuntimeException(e);
        }
        return true;
    }

    private void cleanObsoleteVirtualFields(Context context, Item item) throws SQLException {
        List<MetadataValue> metadataValuesToDelete = getObsoleteVirtualFields(item);
        if (!metadataValuesToDelete.isEmpty()) {
            itemService.removeMetadataValues(context, item, metadataValuesToDelete);
        }
    }

    private List<MetadataValue> getObsoleteVirtualFields(Item item) {
        return itemService
            .getMetadataByMetadataString(item, getVirtualSourceMetadataField())
            .stream()
            .filter(field -> isRelatedSourceNoMorePresent(item, field))
            .flatMap(field -> Stream.concat(
                Stream.of(field),
                getRelatedVirtualField(item, field).stream()
            ))
            .collect(Collectors.toList());
    }

    private boolean isRelatedSourceNoMorePresent(Item item, MetadataValue virtualSourceField) {
        return getEnhanceableMetadataValue(item)
            .stream()
            .noneMatch(metadataValue -> Objects.equals(metadataValue.getAuthority(), virtualSourceField.getValue()));
    }

    private Optional<MetadataValue> getRelatedVirtualField(Item item, MetadataValue virtualSourceField) {
        return itemService.getMetadataByMetadataString(item, getVirtualMetadataField())
            .stream()
            .filter(metadataValue -> metadataValue.getPlace() == virtualSourceField.getPlace())
            .findFirst();
    }

    private void performEnhancement(Context context, Item item) throws SQLException {
        if (noEnhanceableMetadata(context, item)) {
            return;
        }

        Set<String> idAlreadyUsed = new HashSet<String>();

        Queue<MetadataValue> enhanceableMetadataValues = new LinkedList<>(getEnhanceableMetadataValue(item));

        while (enhanceableMetadataValues.peek() != null) {
            MetadataValue metadataValue = enhanceableMetadataValues.poll();

            String authority = metadataValue.getAuthority();
            if (authority == null || idAlreadyUsed.contains(authority)) {
                continue;
            }

            idAlreadyUsed.add(authority);

            Item relatedItem = findRelatedEntityItem(context, metadataValue);

            if (relatedItem == null) {
                continue;
            }

            if ("Person".equals(itemService.getEntityType(relatedItem))) {
                metadataValue = getPersonAffiliationMetadataValue(relatedItem);

                if (metadataValue == null) {
                    continue;
                }

                relatedItem = findRelatedEntityItem(context, metadataValue);
                if (relatedItem == null) {
                    continue;
                }
            }

            enhanceableMetadataValues.addAll(
                itemService.getMetadata(relatedItem, "organization", "parentOrganization", null, null));

            if (wasValueAlreadyUsedForEnhancement(item, metadataValue)) {
                continue;
            }

            MetadataValue finalMetadataValue = metadataValue;

            itemService.getMetadataByMetadataString(relatedItem, relatedItemMetadataField)
                       .stream()
                       .map(MetadataValue::getValue)
                       .filter(StringUtils::isNotBlank)
                       .map(relatedValue -> new MetadataValueVO(relatedValue, finalMetadataValue.getAuthority()))
                       .forEach(throwingConsumerWrapper(
                           relatedValueVO -> enhanceVirtualFields(context, item, finalMetadataValue, relatedValueVO)
                       ));
        }
    }

    private boolean noEnhanceableMetadata(Context context, Item item) {
        return getEnhanceableMetadataValue(item)
            .stream()
            .noneMatch(metadataValue -> validAuthority(context, metadataValue));
    }

    private boolean validAuthority(Context context, MetadataValue metadataValue) {
        Item relatedItem = findRelatedEntityItem(context, metadataValue);

        return relatedItem != null &&
            itemService.getMetadataByMetadataString(relatedItem, relatedItemMetadataField)
                       .stream()
                       .map(MetadataValue::getValue)
                       .anyMatch(StringUtils::isNotBlank);
    }

    private Item findRelatedEntityItem(Context context, MetadataValue metadataValue) {
        try {
            UUID relatedItemUUID = UUIDUtils.fromString(metadataValue.getAuthority());
            return relatedItemUUID != null ? itemService.find(context, relatedItemUUID) : null;
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }
    }

    private MetadataValue getPersonAffiliationMetadataValue(Item person) {
        return itemService.getMetadataByMetadataString(person, "person.affiliation.name")
                          .stream()
                          .filter(mv -> StringUtils.isNotEmpty(mv.getAuthority()))
                          .findFirst()
                          .orElse(null);
    }

    private List<MetadataValue> getEnhanceableMetadataValue(Item item) {
        return sourceItemMetadataFields
            .stream()
            .flatMap(sourceItemMetadataField ->
                         itemService.getMetadataByMetadataString(item, sourceItemMetadataField).stream())
            .collect(Collectors.toList());
    }

    private boolean wasValueAlreadyUsedForEnhancement(Item item, MetadataValue metadataValue) {
        return itemService.getMetadataByMetadataString(item, getVirtualSourceMetadataField())
            .stream()
            .anyMatch(virtualSourceField ->
                          Objects.equals(metadataValue.getAuthority(), virtualSourceField.getValue()));
    }

    private void addVirtualField(Context context, Item item, MetadataValueVO value) throws SQLException {
        itemService.addMetadata(context, item, VIRTUAL_METADATA_SCHEMA, VIRTUAL_METADATA_ELEMENT,
                                getVirtualQualifier(), null, value.getValue(), value.getAuthority(),
                                value.getConfidence());
    }

    private void addVirtualSourceField(Context context, Item item, MetadataValueVO sourceValue) throws SQLException {
        itemService.addMetadata(context, item, VIRTUAL_METADATA_SCHEMA, VIRTUAL_SOURCE_METADATA_ELEMENT,
                                getVirtualQualifier(), null, sourceValue.getAuthority());
    }

    protected void enhanceVirtualFields(Context context, Item item, MetadataValue metadataValue,
                                        MetadataValueVO relatedItemMetadataValue) throws SQLException {
        addVirtualField(context, item, relatedItemMetadataValue);
        addVirtualSourceField(context, item, new MetadataValueVO(metadataValue));
    }

    public void setSourceEntityType(String sourceEntityType) {
        this.sourceEntityType = sourceEntityType;
    }

    public void setSourceItemMetadataField(List<String> sourceItemMetadataFields) {
        this.sourceItemMetadataFields = sourceItemMetadataFields;
    }

    public void setRelatedItemMetadataField(String relatedItemMetadataField) {
        this.relatedItemMetadataField = relatedItemMetadataField;
    }
}
