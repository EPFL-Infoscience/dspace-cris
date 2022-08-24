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
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.StringUtils;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
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
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
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

    private static final Logger logger = LoggerFactory.getLogger(PolicyMetadataEnhancerConsumer.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private static final MetadataFieldName dataciteRightsMetadata = new MetadataFieldName("datacite", "rights");
    private static final MetadataFieldName dataciteAvailableMetadata = new MetadataFieldName("datacite", "available");
    private static final MetadataFieldName oaireLicenseMetadata = new MetadataFieldName("oaire", "licenseCondition");
    private static final List<MetadataFieldName> bitstreamToItemMetadatas = List.of(
        oaireLicenseMetadata,
        dataciteAvailableMetadata,
        dataciteRightsMetadata
    );

    private BitstreamService bitstreamService;
    private ItemService itemService;
    private ResourcePolicyService resourcePolicyService;
    private AuthorizeService authorizeService;
    private Set<Bitstream> bitstreamAlreadyProcessed = new HashSet<>();
    private Set<Item> itemsToProcess = new HashSet<>();

    @Override
    public void initialize() throws Exception {
        this.bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        this.itemService = ContentServiceFactory.getInstance().getItemService();
        this.resourcePolicyService = ContentServiceFactory.getInstance().getResourcePolicyService();
        this.authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        this.handleBitStreamConsumer(
                ctx,
                Optional.ofNullable((Bitstream) event.getObject(ctx))
                    .orElse(this.loadBitstream(ctx, event)),
                event
        );
    }

    private void removeAllEnhancedMetadatas(Context ctx, Bitstream bitstream) {
        Optional.of(getRemovableMetadatas(bitstream))
            .filter(list -> !list.isEmpty())
            .ifPresent(throwingConsumerWrapper(list ->
                    this.bitstreamService.removeMetadataValues(ctx, bitstream, list)
                )
            );
    }

    @Override
    public void end(Context ctx) throws Exception {
        bitstreamAlreadyProcessed.clear();
        this.itemsToProcess
            .stream()
            .forEach(item -> this.handleItemConsumer(ctx, item));
        itemsToProcess.clear();
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
            List<MetadataValue> metadatas =
                Optional.ofNullable(loadedItem)
                        .map(i -> i.getBundles("ORIGINAL"))
                        .map(bundles -> bundles.get(0))
                        .map(Bundle::getBitstreams)
                        .map(bitstreams -> bitstreams.get(0))
                        .map(bitstream -> getMetadatasForItem(ctx, List.of(bitstream)).collect(Collectors.toList()))
                        .orElse(List.of());

            this.itemService.removeMetadataValues(ctx, loadedItem, getRemovableMetadatas(loadedItem));

            Map<MetadataField, List<String>> grouped = groupByMetadataField(metadatas);

            grouped
                .entrySet()
                .stream()
                .forEach(
                    throwingConsumerWrapper(entry ->
                        this.itemService.addMetadata(ctx, loadedItem, entry.getKey(), null, entry.getValue())
                    )
                );

        } catch (SQLException e) {
            logger.error(MessageFormat.format("Error while processing item {}!", item.getID().toString()), e);
            throw new SQLRuntimeException(e);
        }

    }

    private Stream<MetadataValue> getMetadatasForItem(Context ctx, List<Bitstream> bitstreams) {
        return bitstreams
            .stream()
            .map(
                throwingMapperWrapper(bitstream ->
                    this.bitstreamService.find(ctx, bitstream.getID()),
                    null
                )
            )
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
                dataciteAvailable
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
            dspaceObjectService.setMetadataSingleValue(ctx, dspaceObject, dataciteRightsMetadata, null, policyValue);
        }
    }

    private <T extends DSpaceObject> void addOrRemoveMetadataWithValue(
            DSpaceObjectService<T> dspaceObjectService,
            Context ctx,
            String metadataValue,
            T dspaceObject,
            Optional<MetadataValue> metadataOptional
    ) throws SQLException {
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
                    dataciteAvailableMetadata,
                    null,
                    metadataValue
                );
            }
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

}
