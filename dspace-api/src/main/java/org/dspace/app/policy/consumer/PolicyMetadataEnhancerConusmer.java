/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.policy.consumer;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.dspace.app.policy.OpenAirePolicyUtils;
import org.dspace.app.policy.OpenAirePolicyUtils.AccessRights;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
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
public class PolicyMetadataEnhancerConusmer implements Consumer {

    private static final Logger logger = LoggerFactory.getLogger(PolicyMetadataEnhancerConusmer.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private static final MetadataFieldName dataciteRightsMetadata = new MetadataFieldName("datacite", "rights");
    private static final MetadataFieldName dataciteAvailableMetadata = new MetadataFieldName("datacite", "available");

    private BitstreamService bitstreamService;
    private ResourcePolicyService resourcePolicyService;

    @Override
    public void initialize() throws Exception {
        this.bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        this.resourcePolicyService = ContentServiceFactory.getInstance().getResourcePolicyService();
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {
        Date endDate = null;
        String policyValue = OpenAirePolicyUtils.AccessRights.RESTRICTED.getName();
        Bitstream bitstream = Optional.ofNullable((Bitstream) event.getObject(ctx))
                                    .orElse(this.loadBitstream(ctx, event));

        Optional<ResourcePolicy> customPolicy = this.getResourcePolicy(ctx, bitstream);
        Optional<MetadataValue> dataciteAvailable = this.getDataciteAvailableMetadata(bitstream);
        Optional<MetadataValue> dataciteRights = this.getDataciteRightsMetadata(bitstream);

        if (customPolicy.isPresent()) {
            ResourcePolicy customPolicyValue = customPolicy.get();
            policyValue = Optional.ofNullable(customPolicyValue.getRpName())
                                .flatMap(policy -> OpenAirePolicyUtils.getPolicyByName(policy.toLowerCase()))
                                .map(AccessRights::getName)
                                .orElse(policyValue);
            endDate = customPolicyValue.getEndDate();
        }

        this.handleDataciteAvailableMetadata(ctx, endDate, bitstream, dataciteAvailable);

        this.handleDataciteRightsMetadata(ctx, policyValue, bitstream, dataciteRights);
    }

    private Bitstream loadBitstream(Context ctx, Event event) {
        Bitstream found = null;
        try {
            found = this.bitstreamService.find(ctx, event.getSubjectID());
        } catch (SQLException e) {
            logger.error("Error while retrieving the bitstream with ID: " + event.getSubjectID(), e);
        }
        return found;
    }

    @Override
    public void end(Context ctx) throws Exception {

    }

    @Override
    public void finish(Context ctx) throws Exception {

    }

    private void handleDataciteRightsMetadata(
            Context ctx,
            String policyValue,
            Bitstream bitstream,
            Optional<MetadataValue> dataciteRights
    ) throws SQLException {
        if (
                dataciteRights.isEmpty() ||
                dataciteRights.filter(metadata -> !policyValue.equals(metadata.getValue())).isPresent()
        ) {
            this.bitstreamService.setMetadataSingleValue(ctx, bitstream, dataciteRightsMetadata, null, policyValue);
        }
    }

    private void handleDataciteAvailableMetadata(Context ctx, Date endDate, Bitstream bitstream,
            Optional<MetadataValue> dataciteAvailable) throws SQLException {
        if (endDate == null) {
            if (dataciteAvailable.isPresent()) {
                this.bitstreamService.removeMetadataValues(ctx, bitstream, List.of(dataciteAvailable.get()));
            }
        } else {
            String formattedDate = dateFormat.format(endDate);
            if (
                    dataciteAvailable.isEmpty() ||
                    dataciteAvailable.filter(metadata -> !formattedDate.equals(metadata.getValue())).isPresent()
            ) {
                this.bitstreamService.setMetadataSingleValue(
                    ctx,
                    bitstream,
                    dataciteAvailableMetadata,
                    null,
                    formattedDate
                );
            }
        }
    }

    private Optional<ResourcePolicy> getResourcePolicy(Context ctx, Bitstream bitstream) throws SQLException {
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

    private Optional<MetadataValue> getMetadata(Bitstream bitstream, MetadataFieldName metadataField) {
        return bitstream.getMetadata()
                .stream()
                .filter(metadata ->
                            metadataField.schema.equals(metadata.getSchema()) &&
                            metadataField.element.equals(metadata.getElement())
                )
                .findFirst();
    }

}
