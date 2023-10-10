/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.unpaywall.consumer;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.unpaywall.dto.UnpaywallApiResponse;
import org.dspace.unpaywall.model.Unpaywall;
import org.dspace.unpaywall.service.UnpaywallService;

public class UnpaywallConsumer implements Consumer {

    private static final String EPFL_UNPAYWALL_METADATA = "epfl.unpaywall.metadata.";
    private final Set<Bitstream> bitstreamsAlreadyProcessed = new HashSet<>();
    private BitstreamService bitstreamService;

    private ItemService itemService;

    private UnpaywallService unpaywallService;

    private ConfigurationService configurationService;

    private final ObjectMapper objectMapper = new ObjectMapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
    @Override
    public void initialize() throws Exception {
        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        itemService = ContentServiceFactory.getInstance().getItemService();
        unpaywallService = ContentServiceFactory.getInstance().getUnpaywallService();
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        if (event.getSubjectType() != Constants.BITSTREAM) {
            return;
        }
        Bitstream bitstream = getBitstream(ctx, event);
        try {
            if (null == bitstream || bitstreamsAlreadyProcessed.contains(bitstream)) {
                return;
            }

            Optional<Item> optionalItem =
                Optional.ofNullable(bitstream.getBundles())
                        .filter(bundles -> !bundles.isEmpty())
                        .map(bundles -> bundles.get(0))
                        .map(bundle -> bundle.getItems())
                        .filter(items -> !items.isEmpty())
                        .map(items -> items.get(0));

            if (optionalItem.isEmpty()) {
                return;
            }

            Item item = optionalItem.get();
            String doi = itemService.getMetadataFirstValue(item,
                                                           "dc", "identifier", "doi", Item.ANY);
            if (StringUtils.isBlank(doi)) {
                return;
            }
            unpaywallService.findUnpaywall(ctx, doi, item.getID())
                            .map(Unpaywall::getJsonRecord)
                            .map(this::mapJsonResponse)
                            .ifPresent(json -> addMetadataAndAccessConditions(ctx, json, bitstream));
        } finally {
            bitstreamsAlreadyProcessed.add(bitstream);
        }
    }

    private void addMetadataAndAccessConditions(Context ctx, UnpaywallApiResponse json, Bitstream bitstream) {

        Optional<UnpaywallApiResponse.OaLocation> oaLocation = bestLocation(json);
        oaLocation.ifPresent(l -> {
            updateMetadata(ctx, bitstream, "oaire.licenseCondition", l.getLicense());
            updateMetadata(ctx, bitstream, "oaire.version", l.getVersion());
        });
    }

    private void updateMetadata(Context ctx, Bitstream bitstream, String metadataField, String metadataValue) {
        MetadataFieldName fieldName = new MetadataFieldName(metadataField);
        String value = bitstreamService.getMetadataFirstValue(bitstream,
                                                                      fieldName,
                                                                      Item.ANY);
        if (StringUtils.isNotBlank(value)) {
            return;
        }
        try {
            String mappedValue = mappedValue(metadataField, metadataValue);
            if (StringUtils.isNotBlank(mappedValue)) {
                bitstreamService.addMetadata(ctx, bitstream, fieldName.schema,
                                             fieldName.element, fieldName.qualifier,
                                             Item.ANY,
                                             mappedValue);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String mappedValue(String metadataField, String metadataValue) {
        return configurationService.getProperty(EPFL_UNPAYWALL_METADATA +
            metadataField.replaceAll("\\.", "_") +
            "." +
            metadataValue);
    }

    private Optional<UnpaywallApiResponse.OaLocation> bestLocation(UnpaywallApiResponse unpaywallApiResponse) {
        return unpaywallApiResponse.getOaLocations()
                                   .stream().filter(UnpaywallApiResponse.OaLocation::isBest)
                                   .findFirst();
    }

    @Override
    public void end(Context ctx) throws Exception {
        bitstreamsAlreadyProcessed.clear();
    }

    @Override
    public void finish(Context ctx) throws Exception { }

    private Bitstream getBitstream(Context ctx, Event event) throws SQLException {
        Bitstream bitstream = (Bitstream) event.getSubject(ctx);
        if (Objects.nonNull(bitstream)) {
            return bitstream;
        }
        return bitstreamService.find(ctx, event.getSubjectID());
    }

    private UnpaywallApiResponse mapJsonResponse(String unpaywallApiJson) {
        try {
            return objectMapper.readValue(unpaywallApiJson, UnpaywallApiResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
