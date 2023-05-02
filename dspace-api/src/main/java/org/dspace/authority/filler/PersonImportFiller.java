/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authority.filler;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.removeStart;
import static org.apache.commons.lang3.StringUtils.startsWith;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authority.service.AuthorityValueService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.epfl.service.PersonApiService;
import org.springframework.beans.factory.annotation.Autowired;

public class PersonImportFiller implements AuthorityImportFiller {

    private static final Logger LOGGER = LogManager.getLogger(PersonImportFiller.class);

    @Autowired
    private PersonApiService personApiService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private BitstreamService bitstreamService;

    @Autowired
    private BundleService bundleService;

    @Override
    public List<MetadataValueDTO> getMetadataListByRelatedItemAndMetadata(Context context, Item relatedItem,
        MetadataValue metadata) {
        return List.of();
    }

    @Override
    public boolean allowsUpdate(Context context, MetadataValue sourceMetadata, Item itemToFill) {
        return false;
    }

    @Override
    public void fillItem(Context context, MetadataValue sourceMetadata, Item item) throws SQLException {

        try {

            getSciperFromMetadataValue(sourceMetadata)
                .ifPresent(sciper -> enrichItem(context, item, sciper));

        } catch (Exception ex) {
            LOGGER.error("An error occurs trying to enrich item with data from OrgUnit API", ex);
        }

        setMetadataIfNotAlreadySet(context, item, "dc", "title", null, sourceMetadata.getValue());

        getSciperFromMetadataValue(sourceMetadata)
            .ifPresent(sciper -> setMetadataIfNotAlreadySet(context, item, "epfl", "sciperId", null, sciper));

    }

    private Optional<String> getSciperFromMetadataValue(MetadataValue metadataValue) {
        return Optional.ofNullable(metadataValue.getAuthority())
            .filter(this::isWillBeGeneratedAuthority)
            .map(authority -> removeWillBeGeneratedPrefix(authority));
    }

    private boolean isWillBeGeneratedAuthority(String authority) {
        return startsWith(authority, getWillBeGeneratedAuthority());
    }

    private String removeWillBeGeneratedPrefix(String authority) {
        return removeStart(authority, getWillBeGeneratedAuthority());
    }

    private String getWillBeGeneratedAuthority() {
        return AuthorityValueService.GENERATE + "SCIPER-ID" + AuthorityValueService.SPLIT;
    }

    private void enrichItem(Context context, Item item, String sciper) {

        personApiService.getMetadataValues(sciper).stream()
            .forEach(metadataValue -> addMetadata(context, item, metadataValue));

        personApiService.getPersonalPicture(sciper)
            .ifPresent(inputstream -> storePersonalPicture(context, item, sciper, inputstream));

    }

    private void storePersonalPicture(Context context, Item item, String sciper, InputStream inputStream) {

        try {

            Bundle bundle = getOriginalBundle(context, item);

            Bitstream bitstream = bitstreamService.create(context, bundle, inputStream);
            bitstream.setName(context, sciper + ".jpg");

            bitstreamService.setMetadataSingleValue(context, bitstream, "dc", "type", null, null, "personal picture");

            bitstreamService.update(context, bitstream);

        } catch (SQLException | AuthorizeException | IOException e) {
            throw new RuntimeException(e);
        }

    }

    private Bundle getOriginalBundle(Context context, Item item) throws SQLException, AuthorizeException {
        List<Bundle> bundles = itemService.getBundles(item, "ORIGINAL");

        if (CollectionUtils.isEmpty(bundles)) {
            return bundleService.create(context, item, "ORIGINAL");
        } else {
            return bundles.iterator().next();
        }

    }

    private void addMetadata(Context context, Item item, MetadataValueDTO value) {
        try {
            itemService.addMetadata(context, item, value.getSchema(), value.getElement(),
                value.getQualifier(), value.getLanguage(), value.getValue(), value.getAuthority(),
                value.getConfidence());
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }
    }

    private void setMetadataIfNotAlreadySet(Context context, Item item, String schema,
        String element, String qualifier, String value) {

        if (isMetadataNotSet(item, schema, element, qualifier)) {
            setMetadata(context, item, schema, element, qualifier, value);
        }
    }

    private boolean isMetadataNotSet(Item item, String schema, String element, String qualifier) {
        return isBlank(itemService.getMetadataFirstValue(item, schema, element, qualifier, Item.ANY));
    }

    private void setMetadata(Context context, Item item, String schema,
        String element, String qualifier, String value) {
        try {
            itemService.setMetadataSingleValue(context, item, schema, element, qualifier, null, value);
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }
    }

}
