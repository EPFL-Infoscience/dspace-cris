/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.enhancer.impl;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.enhancer.AbstractItemEnhancer;
import org.dspace.content.enhancer.ItemEnhancer;
import org.dspace.content.service.ItemService;
import org.dspace.content.vo.MetadataValueVO;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.util.UUIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link ItemEnhancer} that add metadata values on the given
 * item taking informations from linked entities.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class RelatedEntityItemEnhancer extends AbstractItemEnhancer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelatedEntityItemEnhancer.class);

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
    public void enhance(Context context, Item item) {
        try {
            cleanObsoleteVirtualFields(context, item);
            updateVirtualFieldsPlaces(context, item);
            performEnhancement(context, item);
        } catch (SQLException e) {
            LOGGER.error("An error occurs enhancing item with id {}: {}", item.getID(), e.getMessage(), e);
            throw new SQLRuntimeException(e);
        }
    }

    private void cleanObsoleteVirtualFields(Context context, Item item) throws SQLException {

        List<MetadataValue> metadataValuesToDelete = getObsoleteVirtualFields(item);
        if (!metadataValuesToDelete.isEmpty()) {
            itemService.removeMetadataValues(context, item, metadataValuesToDelete);
        }

    }

    private void updateVirtualFieldsPlaces(Context context, Item item) {
        List<MetadataValue> virtualSourceFields = getVirtualSourceFields(item);
        for (MetadataValue virtualSourceField : virtualSourceFields) {
            metadataWithPlaceToUpdate(item, virtualSourceField)
                .ifPresent(updatePlaces(item, virtualSourceField));
        }
    }

    private Optional<MetadataValue> metadataWithPlaceToUpdate(Item item, MetadataValue virtualSourceField) {
        return findEnhanceableValue(virtualSourceField, item)
            .filter(hasToUpdatePlace(virtualSourceField))
            .stream().findFirst();
    }

    private Predicate<MetadataValue> hasToUpdatePlace(MetadataValue virtualSourceField) {
        return metadataValue -> metadataValue.getPlace() != virtualSourceField.getPlace();
    }

    private Consumer<MetadataValue> updatePlaces(Item item, MetadataValue virtualSourceField) {
        return mv -> {
            virtualSourceField.setPlace(mv.getPlace());
            getRelatedVirtualField(item, mv)
                .ifPresent(relatedMv -> relatedMv.setPlace(mv.getPlace()));
        };
    }

    private Optional<MetadataValue> findEnhanceableValue(MetadataValue virtualSourceField, Item item) {
        return getEnhanceableMetadataValue(item).stream()
            .filter(metadataValue -> hasAuthorityEqualsTo(metadataValue, virtualSourceField.getValue()))
            .findFirst();
    }

    private List<MetadataValue> getObsoleteVirtualFields(Item item) {

        List<MetadataValue> obsoleteVirtualFields = new ArrayList<>();

        List<MetadataValue> virtualSourceFields = getVirtualSourceFields(item);
        for (MetadataValue virtualSourceField : virtualSourceFields) {
            if (!isPlaceholder(virtualSourceField) && isRelatedSourceNoMorePresent(item, virtualSourceField)) {
                obsoleteVirtualFields.add(virtualSourceField);
                getRelatedVirtualField(item, virtualSourceField).ifPresent(obsoleteVirtualFields::add);
            }
        }

        return obsoleteVirtualFields;

    }

    private boolean isRelatedSourceNoMorePresent(Item item, MetadataValue virtualSourceField) {
        return getEnhanceableMetadataValue(item).stream()
            .noneMatch(metadataValue -> hasAuthorityEqualsTo(metadataValue, virtualSourceField.getValue()));
    }

    private Optional<MetadataValue> getRelatedVirtualField(Item item, MetadataValue virtualSourceField) {
        return getVirtualFields(item).stream()
            .filter(metadataValue -> metadataValue.getPlace() == virtualSourceField.getPlace())
            .findFirst();
    }

    private void performEnhancement(Context context, Item item) throws SQLException {

        if (noEnhanceableMetadata(context, item)) {
            return;
        }

        List<MetadataValueDTO> metadataValuesToAdd = new ArrayList<MetadataValueDTO>();

        List<MetadataValue> virtualFields = getVirtualFields(item);
        List<MetadataValue> virtualSourceFields = getVirtualSourceFields(item);

        for (MetadataValue metadataValue : getEnhanceableMetadataValue(item)) {

            if (wasValueAlreadyUsedForEnhancement(virtualFields, virtualSourceFields, metadataValue)) {
                continue;
            }

            Item relatedItem = findRelatedEntityItem(context, metadataValue);
            if (relatedItem == null) {
                metadataValuesToAdd.add(getVirtualField(new MetadataValueVO(PLACEHOLDER_PARENT_METADATA_VALUE)));
                metadataValuesToAdd.add(getVirtualSourceField(new MetadataValueVO(null,
                    PLACEHOLDER_PARENT_METADATA_VALUE)));
                continue;
            }

            List<MetadataValue> relatedItemMetadataValues = getMetadataValues(relatedItem, relatedItemMetadataField);
            if (relatedItemMetadataValues.isEmpty()) {
                metadataValuesToAdd.add(getVirtualField(new MetadataValueVO(PLACEHOLDER_PARENT_METADATA_VALUE)));
                metadataValuesToAdd.add(getVirtualSourceField(new MetadataValueVO(metadataValue)));
                continue;
            }

            relatedItemMetadataValues.stream()
                .map(relatedItemMetadataValue -> getRelatedItemValue(context, relatedItemMetadataValue))
                .filter(relatedItemValue -> relatedItemValue != null && isNotBlank(relatedItemValue.getValue()))
                .flatMap(relatedItemValue -> enhanceVirtualFields(metadataValue, relatedItemValue).stream())
                .forEach(metadataValuesToAdd::add);

        }

        addMetadataValues(context, item, metadataValuesToAdd);

    }

    private void addMetadataValues(Context context, Item item, List<MetadataValueDTO> metadataValues)
        throws SQLException {

        Map<String, List<MetadataValueDTO>> metadataValuesGroupedByField = metadataValues.stream()
            .collect(Collectors.groupingBy(MetadataValueDTO::getMetadataField));

        for (String metadataField : metadataValuesGroupedByField.keySet()) {
            addMetadataValues(context, item, metadataField, metadataValuesGroupedByField.get(metadataField));
        }

    }

    private void addMetadataValues(Context context, Item item, String metadataField,
        List<MetadataValueDTO> metadataValues) throws SQLException {

        List<String> values = metadataValues.stream().map(MetadataValueDTO::getValue).collect(toList());

        List<String> authorities = metadataValues.stream().map(MetadataValueDTO::getAuthority).collect(toList());
        List<Integer> confidences = metadataValues.stream().map(MetadataValueDTO::getConfidence).collect(toList());

        MetadataFieldName field = new MetadataFieldName(metadataField);

        itemService.addMetadata(context, item, field.schema, field.element, field.qualifier,
            null, values, authorities, confidences);
    }

    private List<MetadataValueDTO> enhanceVirtualFields(MetadataValue metadataValue,
        MetadataValueVO relatedItemMetadataValue) {

        List<MetadataValueDTO> metadataValuesToAdd = new ArrayList<MetadataValueDTO>();

        metadataValuesToAdd.add(getVirtualField(relatedItemMetadataValue));
        metadataValuesToAdd.add(getVirtualSourceField(new MetadataValueVO(metadataValue)));

        return metadataValuesToAdd;
    }

    protected MetadataValueVO getRelatedItemValue(Context context, MetadataValue relatedItemMetadataValue) {
        return new MetadataValueVO(relatedItemMetadataValue.getValue());
    }

    private boolean noEnhanceableMetadata(Context context, Item item) {

        return getEnhanceableMetadataValue(item)
            .stream()
            .noneMatch(metadataValue -> validAuthority(context, metadataValue));
    }

    private boolean validAuthority(Context context, MetadataValue metadataValue) {
        // FIXME: we could find a more efficient way, here we are doing twice the same action
        //  to understand if the enhanced item has at least an item whose references should be put in virtual fields.
        Item relatedItem = findRelatedEntityItem(context, metadataValue);
        if (relatedItem == null) {
            return false;
        }

        return getMetadataValues(relatedItem, relatedItemMetadataField).stream()
            .map(value -> getRelatedItemValue(context, value))
            .anyMatch(relatedItemValue -> relatedItemValue != null && isNotBlank(relatedItemValue.getValue()));
    }

    private List<MetadataValue> getEnhanceableMetadataValue(Item item) {
        return sourceItemMetadataFields.stream()
            .flatMap(sourceItemMetadataField -> getMetadataValues(item, sourceItemMetadataField).stream())
            .collect(Collectors.toList());
    }

    private boolean wasValueAlreadyUsedForEnhancement(List<MetadataValue> virtualFields,
        List<MetadataValue> virtualSourceFields, MetadataValue metadataValue) {

        if (isPlaceholderAtPlace(virtualFields, metadataValue.getPlace())) {
            return true;
        }

        return virtualSourceFields.stream()
            .anyMatch(virtualSourceField -> virtualSourceField.getPlace() == metadataValue.getPlace()
                && hasAuthorityEqualsTo(metadataValue, virtualSourceField.getValue()));

    }

    private boolean isPlaceholderAtPlace(List<MetadataValue> metadataValues, int place) {
        return place < metadataValues.size() ? isPlaceholder(metadataValues.get(place)) : false;
    }

    private boolean hasAuthorityEqualsTo(MetadataValue metadataValue, String authority) {
        return Objects.equals(metadataValue.getAuthority(), authority);
    }

    private Item findRelatedEntityItem(Context context, MetadataValue metadataValue) {
        try {
            UUID relatedItemUUID = UUIDUtils.fromString(metadataValue.getAuthority());
            return relatedItemUUID != null ? itemService.find(context, relatedItemUUID) : null;
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }
    }

    private boolean isPlaceholder(MetadataValue metadataValue) {
        return PLACEHOLDER_PARENT_METADATA_VALUE.equals(metadataValue.getValue());
    }

    private List<MetadataValue> getMetadataValues(Item item, String metadataField) {
        return itemService.getMetadataByMetadataString(item, metadataField);
    }

    private List<MetadataValue> getVirtualSourceFields(Item item) {
        return getMetadataValues(item, getVirtualSourceMetadataField());
    }

    private List<MetadataValue> getVirtualFields(Item item) {
        return getMetadataValues(item, getVirtualMetadataField());
    }

    private MetadataValueDTO getVirtualField(MetadataValueVO value) {
        return new MetadataValueDTO(getVirtualMetadataField(), value.getValue(), value.getAuthority(),
            value.getConfidence());
    }

    private MetadataValueDTO getVirtualSourceField(MetadataValueVO value) {
        return new MetadataValueDTO(getVirtualSourceMetadataField(), value.getAuthority());
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
