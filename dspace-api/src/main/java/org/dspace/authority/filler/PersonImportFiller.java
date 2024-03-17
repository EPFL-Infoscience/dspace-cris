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

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authenticate.service.ProfileInitializer;
import org.dspace.authority.service.AuthorityValueService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.eperson.EPerson;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.client.EpflApiClientImpl;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.epfl.service.PersonApiService;
import org.dspace.utils.DSpace;
import org.springframework.beans.factory.annotation.Autowired;

public class PersonImportFiller implements AuthorityImportFiller {

    private static final Logger LOGGER = LogManager.getLogger(PersonImportFiller.class);

    @Autowired
    private PersonApiService personApiService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private BitstreamService bitstreamService;

    private EpflApiClientImpl epflApiClient;
    private ProfileInitializer profileInitializer;

    public PersonImportFiller() {
        epflApiClient = new DSpace().getServiceManager()
                .getServiceByName("org.dspace.epfl.client.EpflApiClientImpl",
                                  EpflApiClientImpl.class);
        profileInitializer = new DSpace().getSingletonService(ProfileInitializer.class);

    }

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
                .ifPresent(sciper -> {
                    enrichItem(context, item, sciper);
                    createOrUpdateEPerson(context, item, sciper);
                });

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
            .map(this::removeWillBeGeneratedPrefix);
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

        personApiService.getMetadataValues(context, sciper)
            .forEach(metadataValue -> addMetadata(context, item, metadataValue));

        personApiService.getPersonalPicture(sciper)
            .ifPresent(content -> bitstreamService.replacePersonalPicture(context, item, sciper + ".jpg", content));

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

    private void createOrUpdateEPerson(Context context, Item item, String sciperId) {
        Optional<PersonDTO> personDTO = getPersonFromEPFL(sciperId);
        if (personDTO != null && personDTO.isPresent()) {
            createOrSynch(context, personDTO.get());
        } else {
            try {
                EPerson ePerson = profileInitializer.findPersonBySciper(context, sciperId);
                if (ePerson == null) {
                    profileInitializer.createBasicEPerson(context, sciperId);
                }
            } catch (SQLException e) {
                LOGGER.error("Error trying to read the EPerson with sciperId " + sciperId, e);
            } catch (AuthorizeException e) {
                LOGGER.error("Authorization error trying to initialize the EPerson with sciperId " + sciperId, e);
            }
        }
    }

    private Optional<PersonDTO> getPersonFromEPFL(String sciperId) {
        try {
            return epflApiClient.getPerson(sciperId, EpflApiClient.Language.EN);
        } catch (Exception e) {
            LOGGER.error("Exception trying to recover the eperson from epfl api for sciperId: " + sciperId, e);
            return null;
        }
    }

    private void createOrSynch(Context context, PersonDTO epflPerson) {
        try {
            EPerson ePerson = profileInitializer.findPerson(context, epflPerson);
            if (ePerson == null) {
                EPerson newEPerson = profileInitializer.createAndSyncEPerson(context, epflPerson);
                if (newEPerson != null) {
                    LOGGER.info("EPerson with uuid: " + newEPerson.getID() + ", sciperId: " + newEPerson.getNetid()
                                    + " was created");
                } else {
                    LOGGER.info("EPerson with sciperId " + epflPerson.getSciper() + " was not created: 0 accreds");
                }
            } else {
                profileInitializer.syncEPerson(context, epflPerson, ePerson);
            }
        } catch (Exception e) {
            LOGGER.error("Unable to sync profile " + epflPerson.getSciper(), e);
        }
    }

}
