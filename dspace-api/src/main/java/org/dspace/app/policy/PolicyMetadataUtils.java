/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.policy;

import static org.dspace.util.FunctionalUtils.throwingConsumerWrapper;
import static org.dspace.util.FunctionalUtils.throwingMapperWrapper;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import org.dspace.event.Event;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utility class for managing and handling metadata-related policies, rights, and configurations
 * associated with DSpace Items, Bitstreams, and ResourcePolicies.
 *
 * This class provides methods for processing Items and Bitstreams, managing metadata updates,
 * handling rights metadata, and interacting with the core service components of DSpace. It aims
 * to facilitate the application of metadata policies and enhance metadata consistency throughout
 * the platform.
 *
 * Fields:
 * - Static constants representing various metadata-related policies and types.
 * - Service dependencies required for operations (such as indexing, item, bitstream, and metadata services).
 * - Metadata configurations used for specific policy management (e.g., rights, availability, and licenses).
 *
 * Methods:
 * - Public methods for handling Items and Bitstreams within a specific Context.
 * - Private methods for performing metadata filtering, metadata updates, and policy application.
 * - Utility methods for retrieving or processing metadata fields and values.
 */
public class PolicyMetadataUtils {

    private static final Logger logger = LoggerFactory.getLogger(PolicyMetadataUtils.class);

    private static IndexingService indexService;
    private static BitstreamService bitstreamService;
    private static ItemService itemService;
    private static ResourcePolicyService resourcePolicyService;
    private static AuthorizeService authorizeService;
    private static MetadataFieldService metadataFieldService;


    public static final String ACCESS_RESTRICTED = "restricted";
    public static final String ACCESS_OPEN = "openaccess";
    public static final String EMBARGO = "embargo";
    public static final String METADATA_ONLY = "metadata-only";
    public static final String MAIN_DOC_TYPE = "main document";
    public static final String METADATA_DC_TYPE = "dc.type";
    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    public static final MetadataFieldName dataciteRightsMetadata = new MetadataFieldName("datacite", "rights");
    public static final MetadataFieldName dataciteAvailableMetadata = new MetadataFieldName("datacite", "available");
    public static final MetadataFieldName viewerMetadata = new MetadataFieldName("bitstream", "viewer", "provider");
    public static final MetadataFieldName oaireLicenseMetadata = new MetadataFieldName("oaire", "licenseCondition");
    public static final MetadataFieldName oaireVersionMetadata = new MetadataFieldName("oaire", "version");
    public static final MetadataFieldName epflLicenseMetadata = new MetadataFieldName("epfl", "licenseName");

    public static final List<MetadataFieldName> bitstreamToItemMetadatas = List.of(
            oaireLicenseMetadata,
            dataciteAvailableMetadata,
            dataciteRightsMetadata,
            epflLicenseMetadata,
            oaireVersionMetadata
    );

    public static final Map<MetadataFieldName, List<String>> defaultItemMetadatas =
            Map.of(dataciteRightsMetadata, List.of(METADATA_ONLY));

    private PolicyMetadataUtils() { }

    public static Item handleItem(Context ctx, Item item, boolean skipUpdate) {

        if (item == null) {
            return null;
        }

        try {
            Item loadedItem = getItemService().find(ctx, item.getID());
            boolean updated = false;
            Map<MetadataField, List<String>> grouped =
                    Optional.ofNullable(loadedItem)
                            .map(i -> i.getBundles("ORIGINAL"))
                            .filter(bundles -> !bundles.isEmpty())
                            .map(bundles -> bundles.get(0))
                            .map(Bundle::getBitstreams)
                            .filter(bitstreams -> !bitstreams.isEmpty())
                            .map(bitstreams -> getRightBitstream(bitstreams, ctx))
                            .map(bitstream ->
                                    getMetadatasForItem(ctx, List.of(bitstream)).collect(Collectors.toList()))
                            .map(PolicyMetadataUtils::groupByMetadataField)
                            .filter(metadatas -> !metadatas.isEmpty())
                            .orElse(mapWithMetadataField(ctx, defaultItemMetadatas));
            try {
                ctx.turnOffAuthorisationSystem();
                final List<MetadataValue> removableMetadatas = getRemovableMetadatas(loadedItem);
                final Set<Map.Entry<MetadataField, List<String>>> entrySet = grouped
                        .entrySet();
                if (anyDiff(removableMetadatas, entrySet)) {
                    getItemService().removeMetadataValues(ctx, loadedItem, removableMetadatas);
                    entrySet.stream().forEach(
                            throwingConsumerWrapper(
                                    entry -> getItemService().addMetadata(ctx,
                                            loadedItem, entry.getKey(), null, entry.getValue())));
                    updated = true;
                }
            } finally {
                ctx.restoreAuthSystemState();
            }
            updated = handleDateAvailableMetadata(ctx, item) || updated;
            if (!skipUpdate && updated) {
                updateItem(ctx, loadedItem);
            }
            return updated ? loadedItem : null;
        } catch (SQLException e) {
            logger.error(MessageFormat.format("Error while processing item {}!", item.getID().toString()), e);
            throw new SQLRuntimeException(e);
        }

    }

    public static void handleBitstream(Context ctx, Bitstream bitstream, boolean skipUpdate) {
        handleBitstream(ctx, bitstream, null, skipUpdate, null, null);
    }

    public static void handleBitstream(Context ctx, Bitstream bitstream, Event event, boolean skipUpdate,
                                       Set<Bitstream> bitstreamAlreadyProcessed, Set<Item> itemsToProcess) {

        if (bitstream == null || bitstreamAlreadyProcessed != null && bitstreamAlreadyProcessed.contains(bitstream)) {
            return;
        }
        List<Item> bitstreamItems = List.of();
        try {
            handleBitstream(ctx, bitstream, event);
            bitstreamService.updateThumbnailResourcePolicies(ctx, bitstream);
            bitstreamItems = bitstream.getBundles()
                    .stream()
                    .filter(bundle -> "ORIGINAL".equals(bundle.getName()))
                    .map(Bundle::getItems)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
            if (!skipUpdate) {
                bitstreamItems
                        .forEach(item -> handleItem(ctx, item, false));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (bitstreamAlreadyProcessed != null) {
                bitstreamAlreadyProcessed.add(bitstream);
            }
            if (skipUpdate) {
                itemsToProcess.addAll(bitstreamItems);
            }
        }
    }

    private static void handleBitstream(Context ctx, Bitstream bitstream, Event event) throws SQLException {

        if (event != null && Event.DELETE == event.getEventType()) {
            removeAllEnhancedMetadatas(ctx, bitstream);
            return;
        }

        Date endDate = null;
        String policyValue = getAuthorizeService().authorizeActionBoolean(ctx, null, bitstream, Constants.READ, false)
                ? ACCESS_OPEN
                : ACCESS_RESTRICTED;

        Optional<ResourcePolicy> customPolicy = getCustomResourcePolicy(ctx, bitstream);
        Optional<MetadataValue> dataciteAvailable = getDataciteAvailableMetadata(bitstream);
        Optional<MetadataValue> dataciteRights = getDataciteRightsMetadata(bitstream);

        if (customPolicy.isPresent()) {
            ResourcePolicy customPolicyValue = customPolicy.get();
            if (isNotExpiredEmbargo(customPolicyValue)) {
                policyValue = Optional.ofNullable(customPolicyValue.getRpName())
                        .orElse(policyValue);
                endDate = customPolicyValue.getStartDate();
            }
        }

        String formattedDate =
                Optional.ofNullable(endDate)
                        .map(dateFormat::format)
                        .orElse(null);

        addOrRemoveMetadataWithValue(
                getBitstreamService(),
                ctx,
                formattedDate,
                bitstream,
                dataciteAvailable,
                dataciteAvailableMetadata
        );

        BitstreamFormat format = bitstream.getFormat(ctx);
        String mimeType = Optional.ofNullable(format).map(BitstreamFormat::getMIMEType).orElse(null);
        String value = null;
        if (Objects.nonNull(mimeType)) {
            if ("application/pdf".equals(mimeType)) {
                value = "pdf";
            }
        }
        addOrRemoveMetadataWithValue(
                getBitstreamService(),
                ctx,
                value,
                bitstream,
                getMetadata(bitstream, viewerMetadata),
                viewerMetadata
        );

        handleDataciteRightsMetadata(getBitstreamService(), ctx, policyValue, bitstream, dataciteRights);
    }


    public static Item loadItem(Context ctx, Event event) {
        Item found = null;
        try {
            found = getItemService().find(ctx, event.getSubjectID());
        } catch (SQLException e) {
            logger.error("Error while retrieving the bitstream with ID: " + event.getSubjectID(), e);
            throw new SQLRuntimeException("Error while retrieving the bitstream with ID: " + event.getSubjectID(), e);
        }
        return found;
    }

    public static void updateItem(Context context, Item item) {
        try {
            context.turnOffAuthorisationSystem();
            if (item.isArchived()) {
                getIndexingService().indexContent(context, new IndexableItem(item), true);
            }
            getIndexingService().commit();
            getItemService().update(context, item);
        } catch (SQLException | AuthorizeException | SearchServiceException e) {
            throw new RuntimeException(e);
        } finally {
            context.restoreAuthSystemState();
        }
    }

    public static Bitstream loadBitstream(Context ctx, Event event) {
        Bitstream found = null;
        try {
            found = getBitstreamService().find(ctx, event.getSubjectID());
        } catch (SQLException e) {
            logger.error("Error while retrieving the bitstream with ID: " + event.getSubjectID(), e);
            throw new SQLRuntimeException("Error while retrieving the bitstream with ID: " + event.getSubjectID(), e);
        }
        return found;
    }

    private static void removeAllEnhancedMetadatas(Context ctx, Bitstream bitstream) {
        try {
            ctx.turnOffAuthorisationSystem();
            Optional.of(getRemovableMetadatas(bitstream))
                    .filter(list -> !list.isEmpty())
                    .ifPresent(throwingConsumerWrapper(list ->
                                    getBitstreamService().removeMetadataValues(ctx, bitstream, list)
                            )
                );
        } finally {
            ctx.restoreAuthSystemState();
        }
    }

    private static List<MetadataValue> getRemovableMetadatas(DSpaceObject dspaceObject) {
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

    private static Optional<MetadataValue> getMetadata(DSpaceObject dspaceObject, MetadataFieldName metadataField) {
        return dspaceObject.getMetadata()
                .stream()
                .filter(metadataFilter(metadataField)
                )
                .findFirst();
    }

    private static Optional<MetadataValue> getDataciteAvailableMetadata(Bitstream bitstream) {
        return getMetadata(bitstream, dataciteAvailableMetadata);
    }

    private static Optional<MetadataValue> getDataciteRightsMetadata(Bitstream bitstream) {
        return getMetadata(bitstream, dataciteRightsMetadata);
    }


    private static Predicate<? super MetadataValue> metadataFilter(MetadataFieldName metadataField) {
        return metadata ->
                StringUtils.equals(metadataField.schema, metadata.getSchema()) &&
                        StringUtils.equals(metadataField.element, metadata.getElement()) &&
                        StringUtils.equals(metadataField.qualifier, metadata.getQualifier());
    }

    private static Predicate<? super MetadataValue> metadataFilter(List<MetadataFieldName> metadataFields) {
        return metadata ->
                metadataFields
                    .stream()
                    .anyMatch(field ->
                        StringUtils.equals(field.schema, metadata.getSchema()) &&
                        StringUtils.equals(field.element, metadata.getElement()) &&
                        StringUtils.equals(field.qualifier, metadata.getQualifier())
                    );
    }


    /**
     * Return true if the metadata in the two structures are different
     * @return true if different
     */
    private static boolean anyDiff(
            List<MetadataValue> removableMetadata, Set<Map.Entry<MetadataField, List<String>>> entrySet) {
        for (MetadataValue m : removableMetadata) {
            final String metadataKey = m.getMetadataField().toString('.');
            Optional<List<String>> values = entrySet.stream()
                    .filter(entry -> {
                        return entry.getKey().toString('.').equals(metadataKey);
                    })
                    .findFirst().map(Map.Entry::getValue);
            if (values.isPresent()) {
                if (!values.get().contains(m.getValue())) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return entrySet.stream().mapToInt(entry -> entry.getValue().size()).sum() != removableMetadata.size();
    }

    private static boolean isNotBitstreamType(Bitstream bitstream, Context ctx, String type) {
        try {
            return !bitstream.getFormat(ctx).getMIMEType().split("/")[0].equals(type);
        } catch (SQLException e) {
            logger.error(MessageFormat.format("Error on bitstream {}", bitstream.getID().toString()), e);
            throw new SQLRuntimeException(e);
        }
    }

    private static boolean isMetadataType(Bitstream bitstream, String metadataField, String targetType) {
        return bitstream.getMetadata()
                .stream()
                .filter(metadataValue -> metadataValue.getMetadataField().toString('.').equals(metadataField))
                .map(MetadataValue::getValue)
                .findFirst()
                .orElse("dummy")
                .equals(targetType);
    }

    private static Bitstream getRightBitstream(List<Bitstream> bitstreams, Context ctx) {
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

    private static Optional<ResourcePolicy> getCustomResourcePolicy(
            Context ctx, Bitstream bitstream) throws SQLException {
        return getResourcePolicyService().find(ctx, bitstream)
                .stream()
                .filter(policy -> ResourcePolicy.TYPE_CUSTOM.equals(policy.getRpType()))
                .findFirst();
    }

    private static boolean handleDateAvailableMetadata(Context ctx, Item item) throws SQLException {
        String rights = getItemService().getMetadataFirstValue(item, "datacite", "rights", null, Item.ANY);
        if (null == rights || rights.trim().isEmpty()) {
            return false;
        }
        if (List.of(METADATA_ONLY, ACCESS_OPEN).contains(rights)) {
            String dateAccessioned =
                    getItemService().getMetadataFirstValue(item, "dc", "date", "accessioned", Item.ANY);
            String dateAvailable =
                    getItemService().getMetadataFirstValue(item, "dc", "date", "available", Item.ANY);
            if (Objects.nonNull(dateAccessioned) && !dateAccessioned.equals(dateAvailable)) {
                return updateDateAvailableMetadata(ctx, item, dateAccessioned);
            }
            return false;
        }
        String dataciteAvailable =
                getItemService().getMetadataFirstValue(item, "datacite", "available", null, Item.ANY);
        return updateDateAvailableMetadata(ctx, item, dataciteAvailable);
    }

    private static boolean updateDateAvailableMetadata(Context ctx, Item item, String date) throws SQLException {
        try {
            ctx.turnOffAuthorisationSystem();
            List<MetadataValue> dateAvailable = getItemService().getMetadata(item, "dc", "date", "available", Item.ANY);
            String currDateAvailable =
                    getItemService().getMetadataFirstValue(item, "dc", "date", "available", Item.ANY);
            if (dateAvailable.size() > 1 || date == null || !date.equals(currDateAvailable)) {
                getItemService().removeMetadataValues(ctx, item, dateAvailable);
                if (null != date && !date.trim().isEmpty()) {
                    getItemService().addMetadata(ctx, item, "dc", "date", "available", null, date);
                }
                return true;
            }
            return false;
        } finally {
            ctx.restoreAuthSystemState();
        }
    }

    private static Map<MetadataField, List<String>> mapWithMetadataField(Context ctx,
                                                                         Map<MetadataFieldName,
                                                                                 List<String>> fieldNameMap) {
        return fieldNameMap
                .entrySet()
                .stream()
                .map(
                        throwingMapperWrapper(
                                entry -> {
                                    MetadataFieldName fieldName = entry.getKey();
                                    MetadataField field = getMetadataFieldService().findByElement(ctx, fieldName.schema,
                                            fieldName.element, fieldName.qualifier);
                                    return new AbstractMap.SimpleEntry<>(field, entry.getValue());
                                }
                        )
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Stream<MetadataValue> getMetadatasForItem(Context ctx, List<Bitstream> bitstreams) {
        return bitstreams
                .stream()
                .map(throwingMapperWrapper(bitstream -> getBitstreamService().find(ctx, bitstream.getID())))
                .filter(Objects::nonNull)
                .flatMap(PolicyMetadataUtils::filterMetadatasForItem);
    }

    private static Stream<MetadataValue> filterMetadatasForItem(Bitstream bitstream) {
        return bitstream.getMetadata()
                .stream()
                .filter(
                        metadataFilter(
                                bitstreamToItemMetadatas
                        )
                );
    }

    private static Map<MetadataField, List<String>> groupByMetadataField(List<MetadataValue> metadatas) {
        return collectByGroupingMetadataFieldMappingValue(metadatas.stream());
    }

    private static Map<MetadataField, List<String>> collectByGroupingMetadataFieldMappingValue(
            Stream<MetadataValue> stream) {
        return stream
                .collect(
                        Collectors.groupingBy(
                                MetadataValue::getMetadataField,
                                Collectors.mapping(MetadataValue::getValue, Collectors.toList())
                        )
                );
    }

    private static <T extends DSpaceObject> void handleDataciteRightsMetadata(
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

    /**
     * Determines if a given resource policy does not correspond to an expired embargo.
     * An active embargo is identified by a policy name equal to "EMBARGO" and a start date
     * set in the future relative to the current date, since the date represents when the
     * object will become publicly accessible.
     *
     * @param resourcePolicy the ResourcePolicy object to be evaluated
     * @return true if the resource policy is not associated with an active embargo, false otherwise
     */
    private static boolean isNotExpiredEmbargo(ResourcePolicy resourcePolicy) {
        return  !(EMBARGO.equals(resourcePolicy.getRpName()) && resourcePolicy.getStartDate().before(new Date()));
    }

    private static <T extends DSpaceObject> void addOrRemoveMetadataWithValue(
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

    private static BitstreamService getBitstreamService() {
        if (bitstreamService == null) {
            bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        }
        return bitstreamService;
    }

    private static ItemService getItemService() {
        if (itemService == null) {
            itemService = ContentServiceFactory.getInstance().getItemService();
        }
        return itemService;
    }

    private static ResourcePolicyService getResourcePolicyService() {
        if (resourcePolicyService == null) {
            resourcePolicyService = ContentServiceFactory.getInstance().getResourcePolicyService();
        }
        return resourcePolicyService;
    }

    private static AuthorizeService getAuthorizeService() {
        if (authorizeService == null) {
            authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
        }
        return authorizeService;
    }

    private static MetadataFieldService getMetadataFieldService() {
        if (metadataFieldService == null) {
            metadataFieldService = ContentServiceFactory.getInstance().getMetadataFieldService();
        }
        return metadataFieldService;
    }

    private static IndexingService getIndexingService() {
        if (indexService == null) {
            indexService = new DSpace().getSingletonService(IndexingService.class);
        }
        return indexService;
    }

}
