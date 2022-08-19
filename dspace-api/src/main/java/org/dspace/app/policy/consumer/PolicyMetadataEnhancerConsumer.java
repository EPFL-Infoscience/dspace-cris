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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.codec.binary.StringUtils;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
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

    private BitstreamService bitstreamService;
    private ResourcePolicyService resourcePolicyService;
    private AuthorizeService authorizeService;
    private Set<Bitstream> bitstreamAlreadyProcessed = new HashSet<>();

    @Override
    public void initialize() throws Exception {
        this.bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        this.resourcePolicyService = ContentServiceFactory.getInstance().getResourcePolicyService();
        this.authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    }

    @Override
    public void consume(Context ctx, Event event) throws Exception {

        Bitstream bitstream = Optional.ofNullable((Bitstream) event.getObject(ctx))
                .orElse(this.loadBitstream(ctx, event));
        if (bitstream == null || bitstreamAlreadyProcessed.contains(bitstream)) {
            return;
        }

        try {
            consume(ctx, bitstream);
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        } finally {
            bitstreamAlreadyProcessed.add(bitstream);
        }
    }

    private void consume(Context ctx, Bitstream bitstream) throws SQLException {
        Date endDate = null;
        String policyValue = authorizeService.authorizeActionBoolean(ctx, null, bitstream, Constants.READ, false)?
                ACCESS_OPEN:ACCESS_RESTRICTED;

        Optional<ResourcePolicy> customPolicy = this.getCustomResourcePolicy(ctx, bitstream);
        Optional<MetadataValue> dataciteAvailable = this.getDataciteAvailableMetadata(bitstream);
        Optional<MetadataValue> dataciteRights = this.getDataciteRightsMetadata(bitstream);

        if (customPolicy.isPresent()) {
            ResourcePolicy customPolicyValue = customPolicy.get();
            policyValue = Optional.ofNullable(customPolicyValue.getRpName())
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
            throw new SQLRuntimeException("Error while retrieving the bitstream with ID: " + event.getSubjectID(), e);
        }
        return found;
    }

    @Override
    public void end(Context ctx) throws Exception {
        bitstreamAlreadyProcessed.clear();
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

    private Optional<MetadataValue> getMetadata(Bitstream bitstream, MetadataFieldName metadataField) {
        return bitstream.getMetadata()
                .stream()
                .filter(metadata ->
                        StringUtils.equals(metadataField.schema, metadata.getSchema()) &&
                        StringUtils.equals(metadataField.element, metadata.getElement()) &&
                        StringUtils.equals(metadataField.qualifier, metadata.getQualifier())
                )
                .findFirst();
    }

}
