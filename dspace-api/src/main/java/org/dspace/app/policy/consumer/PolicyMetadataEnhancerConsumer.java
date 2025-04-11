/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.policy.consumer;

import static org.dspace.util.FunctionalUtils.throwingConsumerWrapper;
import static org.dspace.util.FunctionalUtils.throwingMapperWrapper;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.DSpaceObjectService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.discovery.IndexingService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code Consumer} that transforms the policy inside a {@code BitStream} to target metadatas.
 * 
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 *
 */
public class PolicyMetadataEnhancerConsumer implements Consumer {

    public static final String ACCESS_RESTRICTED = "restricted";
    public static final String ACCESS_OPEN = "openaccess";
    public static final String METADATA_ONLY = "metadata-only";

    private static final String MAIN_DOC_TYPE = "main document";

    private static final String METADATA_DC_TYPE = "dc.type";

    private static final Logger logger = LoggerFactory.getLogger(PolicyMetadataEnhancerConsumer.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private static final MetadataFieldName dataciteRightsMetadata = new MetadataFieldName("datacite", "rights");
    private static final MetadataFieldName dataciteAvailableMetadata = new MetadataFieldName("datacite", "available");
    private static final MetadataFieldName viewerMetadata = new MetadataFieldName("bitstream", "viewer", "provider");
    private static final MetadataFieldName oaireLicenseMetadata = new MetadataFieldName("oaire", "licenseCondition");
    private static final MetadataFieldName oaireVersionMetadata = new MetadataFieldName("oaire", "version");
    private static final MetadataFieldName epflLicenseMetadata = new MetadataFieldName("epfl", "licenseName");
    private static final List<MetadataFieldName> bitstreamToItemMetadatas = List.of(
        oaireLicenseMetadata,
        dataciteAvailableMetadata,
        dataciteRightsMetadata,
        epflLicenseMetadata,
        oaireVersionMetadata
    );
    private static final Map<MetadataFieldName, List<String>> defaultItemMetadatas = Map.of(dataciteRightsMetadata,
            List.of(METADATA_ONLY));

    private IndexingService indexService;
    private BitstreamService bitstreamService;
    private ItemService itemService;
    private ResourcePolicyService resourcePolicyService;
    private AuthorizeService authorizeService;
    private Set<Bitstream> bitstreamAlreadyProcessed = new HashSet<>();
    private Set<Item> itemsToProcess = new HashSet<>();
    private Set<Item> itemsToUpdate = new HashSet<>();
    private MetadataFieldService metadataFieldService;

    @Override
    public void initialize() throws Exception {
        this.bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        this.itemService = ContentServiceFactory.getInstance().getItemService();
        this.resourcePolicyService = ContentServiceFactory.getInstance().getResourcePolicyService();
        this.authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
        this.metadataFieldService = ContentServiceFactory.getInstance().getMetadataFieldService();
        this.indexService = new DSpace().getSingletonService(IndexingService.class);
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        if (Constants.BITSTREAM == event.getSubjectType()) {
            this.handleBitStreamConsumer(
                    ctx,
                    Optional.ofNullable((Bitstream) event.getObject(ctx))
                            .orElse(this.loadBitstream(ctx, event)),
                    event
            );
        } else if (Constants.ITEM == event.getSubjectType() && (Event.CREATE == event.getEventType() ||
                Event.MODIFY == event.getEventType())) {
            this.handleItemConsumer(
                    ctx,
                    Optional.ofNullable((Item) event.getObject(ctx))
                            .orElse(this.loadItem(ctx, event))
            );
        } else {
            logger.warn(
                "Can't consume the DSPaceObject with id {}, only BITSTREAM and ITEMS'CREATION events are consumable!",
                event.getSubjectID()
            );
        }
    }

    private void removeAllEnhancedMetadatas(Context ctx, Bitstream bitstream) {
        try {
            ctx.turnOffAuthorisationSystem();
            Optional.of(getRemovableMetadatas(bitstream))
                .filter(list -> !list.isEmpty())
                .ifPresent(throwingConsumerWrapper(list ->
                        this.bitstreamService.removeMetadataValues(ctx, bitstream, list)
                    )
                );
        } finally {
            ctx.restoreAuthSystemState();
        }
    }

    @Override
    public void end(Context ctx) throws Exception {
        bitstreamAlreadyProcessed.clear();
        this.itemsToProcess
            .forEach(item -> this.handleItemConsumer(ctx, item));
        itemsToProcess.clear();

        itemsToUpdate.forEach(item -> updateItem(ctx, item));
        itemsToUpdate.clear();
    }

    @Override
    public void finish(Context ctx) throws Exception {

    }

    private boolean alreadyProcessed(Bitstream bitstream) {
        return bitstreamAlreadyProcessed.contains(bitstream);
    }

    private void handleBitStreamConsumer(Context ctx, Bitstream bitstream, Event event) {

        if (bitstream == null || this.alreadyProcessed(bitstream)) {
            return;
        }
        List<Item> bitstreamItems = List.of();
        try {
            consume(ctx, bitstream, event);
            bitstreamItems = bitstream.getBundles()
                .stream()
                .filter(bundle -> "ORIGINAL".equals(bundle.getName()))
                .map(Bundle::getItems)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            bitstreamAlreadyProcessed.add(bitstream);
            bitstreamItems
                .stream()
                .forEach(item -> this.itemsToProcess.add(item));
        }
    }

    private void handleItemConsumer(Context ctx, Item item) {

        if (item == null) {
            return;
        }

        try {
            Item loadedItem = this.itemService.find(ctx, item.getID());
            boolean updated = false;
            Map<MetadataField, List<String>> grouped =
                Optional.ofNullable(loadedItem)
                        .map(i -> i.getBundles("ORIGINAL"))
                        .filter(bundles -> !bundles.isEmpty())
                        .map(bundles -> bundles.get(0))
                        .map(Bundle::getBitstreams)
                        .filter(bitstreams -> !bitstreams.isEmpty())
                        .map(bitstreams -> getRightBitstream(bitstreams, ctx))
                        .map(bitstream -> getMetadatasForItem(ctx, List.of(bitstream)).collect(Collectors.toList()))
                        .map(metadatas -> groupByMetadataField(metadatas))
                        .filter(metadatas -> !metadatas.isEmpty())
                        .orElse(this.mapWithMetadataField(ctx, defaultItemMetadatas));
            try {
                ctx.turnOffAuthorisationSystem();
                final List<MetadataValue> removableMetadatas = getRemovableMetadatas(loadedItem);
                final Set<Entry<MetadataField, List<String>>> entrySet = grouped
                    .entrySet();
                if (anyDiff(removableMetadatas, entrySet)) {
                    this.itemService.removeMetadataValues(ctx, loadedItem, removableMetadatas);
                    entrySet.stream().forEach(
                            throwingConsumerWrapper(
                                entry -> this.itemService.addMetadata(ctx,
                                            loadedItem, entry.getKey(), null, entry.getValue())));
                    updated = true;
                }
            } finally {
                ctx.restoreAuthSystemState();
            }
            updated = handleDateAvailableMetadata(ctx, item);
            if (updated) {
                itemsToUpdate.add(loadedItem);
            }
        } catch (SQLException e) {
            logger.error(MessageFormat.format("Error while processing item {}!", item.getID().toString()), e);
            throw new SQLRuntimeException(e);
        }

    }

    /**
     * Return true if the metadata in the two structures are different
     * @param removableMetadatas
     * @param entrySet
     * @return true if different
     */
    private boolean anyDiff(List<MetadataValue> removableMetadatas, Set<Entry<MetadataField, List<String>>> entrySet) {
        for (MetadataValue m : removableMetadatas) {
            final String metadataKey = m.getMetadataField().toString('.');
            Optional<List<String>> values = entrySet.stream()
                    .filter(entry -> {
                        return entry.getKey().toString('.').equals(metadataKey);
                    })
                    .findFirst().map(entry -> entry.getValue());
            if (values.isPresent()) {
                if (!values.get().contains(m.getValue())) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return entrySet.stream().mapToInt(entry -> entry.getValue().size()).sum() != removableMetadatas.size();
    }

    private Bitstream getRightBitstream(List<Bitstream> bitstreams, Context ctx) {
        if (bitstreams.size() == 1) {
            return bitstreams.get(0);
        } else {
            return bitstreams.stream()
                    .filter(bitstream -> isMetadataType(bitstream, METADATA_DC_TYPE, MAIN_DOC_TYPE))
                    .findFirst()
                    .orElse(bitstreams.stream()
                            .filter(bitstream -> isNotBitstreamType(bitstream, ctx, "image"))
                            .findFirst()
                            .orElse(bitstreams.get(0)));
        }
    }

    private boolean isMetadataType(Bitstream bitstream, String metadataField, String targetType) {
        return bitstream.getMetadata()
                .stream()
                .filter(metadataValue -> metadataValue.getMetadataField().toString('.').equals(metadataField))
                .map(MetadataValue::getValue)
                .findFirst()
                .orElse("dummy")
                .equals(targetType);
    }

    private boolean isNotBitstreamType(Bitstream bitstream, Context ctx, String type) {
        try {
            return !bitstream.getFormat(ctx).getMIMEType().split("/")[0].equals(type);
        } catch (SQLException e) {
            logger.error(MessageFormat.format("Error while bitstream {}!", bitstream.getID().toString()), e);
            throw new SQLRuntimeException(e);
        }
    }

    private boolean handleDateAvailableMetadata(Context ctx, Item item) throws SQLException {
        String rights = itemService.getMetadataFirstValue(item, "datacite", "rights", null, Item.ANY);
        if (null == rights || rights.trim().isEmpty()) {
            return false;
        }
        if (List.of(METADATA_ONLY, ACCESS_OPEN).contains(rights)) {
            String dateAccessioned = itemService.getMetadataFirstValue(item, "dc", "date", "accessioned", Item.ANY);
            String dateAvailable = itemService.getMetadataFirstValue(item, "dc", "date", "available", Item.ANY);
            if (Objects.nonNull(dateAccessioned) && !dateAccessioned.equals(dateAvailable)) {
                return updateDateAvailableMetadata(ctx, item, dateAccessioned);
            }
            return false;
        }
        String dataciteAvailable = itemService.getMetadataFirstValue(item, "datacite", "available", null, Item.ANY);
        return updateDateAvailableMetadata(ctx, item, dataciteAvailable);
    }

    private boolean updateDateAvailableMetadata(Context ctx, Item item, String date) throws SQLException {
        try {
            ctx.turnOffAuthorisationSystem();
            List<MetadataValue> dateAvailable = itemService.getMetadata(item, "dc", "date", "available", Item.ANY);
            String currDateAvailable = itemService.getMetadataFirstValue(item, "dc", "date", "available", Item.ANY);
            if (dateAvailable.size() > 1 || date == null || !date.equals(currDateAvailable)) {
                itemService.removeMetadataValues(ctx, item, dateAvailable);
                if (null != date && !date.trim().isEmpty()) {
                    itemService.addMetadata(ctx, item, "dc", "date", "available", null, date);
                }
                return true;
            }
            return false;
        } finally {
            ctx.restoreAuthSystemState();
        }
    }

    private Map<MetadataField, List<String>> mapWithMetadataField(Context ctx,
            Map<MetadataFieldName, List<String>> fieldNameMap) {
        return fieldNameMap
                    .entrySet()
                    .stream()
                    .map(
                        throwingMapperWrapper(
                            entry -> {
                                MetadataFieldName fieldName = entry.getKey();
                                MetadataField field = this.metadataFieldService.findByElement(ctx, fieldName.schema,
                                        fieldName.element, fieldName.qualifier);
                                return new AbstractMap.SimpleEntry<>(field, entry.getValue());
                            }
                        )
                    )
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    private Stream<MetadataValue> getMetadatasForItem(Context ctx, List<Bitstream> bitstreams) {
        return bitstreams
            .stream()
            .map(throwingMapperWrapper(bitstream -> this.bitstreamService.find(ctx, bitstream.getID())))
            .filter(Objects::nonNull)
            .flatMap(bitstream -> filterMetadatasForItem(bitstream));
    }

    private Stream<MetadataValue> filterMetadatasForItem(Bitstream bitstream) {
        return bitstream.getMetadata()
            .stream()
            .filter(
                metadataFilter(
                    bitstreamToItemMetadatas
                )
            );
    }

    private List<MetadataValue> getRemovableMetadatas(DSpaceObject dspaceObject) {
        return dspaceObject
            .getMetadata()
            .stream()
            .filter(
                metadataFilter(
                    bitstreamToItemMetadatas
                )
            )
            .collect(Collectors.toList());
    }

    private Map<MetadataField, List<String>> groupByMetadataField(List<MetadataValue> metadatas) {
        return this.collectByGroupingMetadataFieldMappingValue(metadatas.stream());
    }

    private Map<MetadataField, List<String>> collectByGroupingMetadataFieldMappingValue(Stream<MetadataValue> stream) {
        return stream
                .collect(
                        Collectors.groupingBy(
                                MetadataValue::getMetadataField,
                                Collectors.mapping(MetadataValue::getValue, Collectors.toList())
                                )
                        );
    }

    private void consume(Context ctx, Bitstream bitstream, Event event) throws SQLException {

        if (Event.DELETE == event.getEventType()) {
            removeAllEnhancedMetadatas(ctx, bitstream);
            return;
        }

        Date endDate = null;
        String policyValue = authorizeService.authorizeActionBoolean(ctx, null, bitstream, Constants.READ, false)
                ? ACCESS_OPEN
                : ACCESS_RESTRICTED;

        Optional<ResourcePolicy> customPolicy = this.getCustomResourcePolicy(ctx, bitstream);
        Optional<MetadataValue> dataciteAvailable = this.getDataciteAvailableMetadata(bitstream);
        Optional<MetadataValue> dataciteRights = this.getDataciteRightsMetadata(bitstream);

        if (customPolicy.isPresent()) {
            ResourcePolicy customPolicyValue = customPolicy.get();
            policyValue = Optional.ofNullable(customPolicyValue.getRpName())
                                .orElse(policyValue);
            endDate = customPolicyValue.getStartDate();
        }

        String formattedDate =
                Optional.ofNullable(endDate)
                        .map(date -> dateFormat.format(date))
                        .orElse(null);

        this.addOrRemoveMetadataWithValue(
            this.bitstreamService,
            ctx,
            formattedDate,
            bitstream,
            dataciteAvailable,
            dataciteAvailableMetadata
        );

        BitstreamFormat format = bitstream.getFormat(ctx);
        String mimeType = Optional.ofNullable(format).map(f -> f.getMIMEType()).orElse(null);
        String value = null;
        if (Objects.nonNull(mimeType)) {
            if ("application/pdf".equals(mimeType)) {
                value = "pdf";
            }
        }
        this.addOrRemoveMetadataWithValue(
            this.bitstreamService,
            ctx,
            value,
            bitstream,
            this.getMetadata(bitstream, viewerMetadata),
            viewerMetadata
        );

        this.handleDataciteRightsMetadata(this.bitstreamService, ctx, policyValue, bitstream, dataciteRights);
    }

    private Bitstream loadBitstream(Context ctx, Event event) {
        Bitstream found = null;
        try {
            found = this.bitstreamService.find(ctx, event.getSubjectID());
        } catch (SQLException e) {
            logger.error("Error while retrieving the bitstream with ID: " + event.getSubjectID(), e);
            throw new SQLRuntimeException("Error while retrieving the bitstream with ID: " + event.getSubjectID(), e);
        }
        return found;
    }

    private Item loadItem(Context ctx, Event event) {
        Item found = null;
        try {
            found = this.itemService.find(ctx, event.getSubjectID());
        } catch (SQLException e) {
            logger.error("Error while retrieving the bitstream with ID: " + event.getSubjectID(), e);
            throw new SQLRuntimeException("Error while retrieving the bitstream with ID: " + event.getSubjectID(), e);
        }
        return found;
    }

    private <T extends DSpaceObject> void handleDataciteRightsMetadata(
            DSpaceObjectService<T> dspaceObjectService,
            Context ctx,
            String policyValue,
            T dspaceObject,
            Optional<MetadataValue> dataciteRights
    ) throws SQLException {
        if (
                dataciteRights.isEmpty() ||
                dataciteRights.filter(metadata -> !policyValue.equals(metadata.getValue())).isPresent()
        ) {
            try {
                ctx.turnOffAuthorisationSystem();
                dspaceObjectService.setMetadataSingleValue(ctx, dspaceObject, dataciteRightsMetadata, null,
                        policyValue);
            } finally {
                ctx.restoreAuthSystemState();
            }
        }
    }

    private <T extends DSpaceObject> void addOrRemoveMetadataWithValue(
        DSpaceObjectService<T> dspaceObjectService,
        Context ctx,
        String metadataValue,
        T dspaceObject,
        Optional<MetadataValue> metadataOptional, MetadataFieldName metadataFieldName
    ) throws SQLException {
        try {
            ctx.turnOffAuthorisationSystem();
            if (metadataValue == null) {
                if (metadataOptional.isPresent()) {
                    dspaceObjectService.removeMetadataValues(ctx, dspaceObject, List.of(metadataOptional.get()));
                }
            } else {
                if (
                        metadataOptional.isEmpty() ||
                        metadataOptional.filter(metadata -> !metadataValue.equals(metadata.getValue())).isPresent()
                ) {
                    dspaceObjectService.setMetadataSingleValue(
                        ctx,
                        dspaceObject,
                        metadataFieldName,
                        null,
                        metadataValue
                    );
                }
            }
        } finally {
            ctx.restoreAuthSystemState();
        }
    }

    private Optional<ResourcePolicy> getCustomResourcePolicy(Context ctx, Bitstream bitstream) throws SQLException {
        return this.resourcePolicyService.find(ctx, bitstream)
                .stream()
                .filter(policy -> ResourcePolicy.TYPE_CUSTOM.equals(policy.getRpType()))
                .findFirst();
    }

    private Optional<MetadataValue> getDataciteAvailableMetadata(Bitstream bitstream) {
        return this.getMetadata(bitstream, dataciteAvailableMetadata);
    }

    private Optional<MetadataValue> getDataciteRightsMetadata(Bitstream bitstream) {
        return this.getMetadata(bitstream, dataciteRightsMetadata);
    }

    private Optional<MetadataValue> getMetadata(DSpaceObject dspaceObject, MetadataFieldName metadataField) {
        return dspaceObject.getMetadata()
                .stream()
                .filter(metadataFilter(metadataField)
                )
                .findFirst();
    }

    private Predicate<? super MetadataValue> metadataFilter(MetadataFieldName metadataField) {
        return metadata ->
                StringUtils.equals(metadataField.schema, metadata.getSchema()) &&
                StringUtils.equals(metadataField.element, metadata.getElement()) &&
                StringUtils.equals(metadataField.qualifier, metadata.getQualifier());
    }

    private Predicate<? super MetadataValue> metadataFilter(List<MetadataFieldName> metadataFields) {
        return metadata ->
            metadataFields
                .stream()
                .filter(field ->
                    StringUtils.equals(field.schema, metadata.getSchema()) &&
                    StringUtils.equals(field.element, metadata.getElement()) &&
                    StringUtils.equals(field.qualifier, metadata.getQualifier())
                )
                .findFirst()
                .isPresent();
    }

    private void updateItem(Context context, Item item) {
        try {
            context.turnOffAuthorisationSystem();
            if (item.isArchived()) {
                indexService.indexContent(context, new IndexableItem(item), true);
            }
            indexService.commit();
            itemService.update(context, item);
        } catch (SQLException | AuthorizeException | SearchServiceException e) {
            throw new RuntimeException(e);
        } finally {
            context.restoreAuthSystemState();
        }
    }

}
