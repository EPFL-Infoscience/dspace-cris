/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate.service;

import static java.util.Optional.ofNullable;
import static org.apache.commons.collections.IteratorUtils.toList;
import static org.dspace.content.authority.Choices.CF_ACCEPTED;
import static org.dspace.core.I18nUtil.getEmailFilename;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.mail.MessagingException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.discovery.SearchServiceException;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.epfl.service.OrgUnitApiService;
import org.dspace.epfl.service.PersonApiService;
import org.dspace.profile.ResearcherProfile;
import org.dspace.profile.service.ResearcherProfileService;
import org.dspace.services.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ProfileInitializer {

    private final static Logger LOGGER = LoggerFactory.getLogger(ProfileInitializer.class);

    @Autowired
    private ResearcherProfileService researcherProfileService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private BitstreamService bitstreamService;

    @Autowired
    private PersonApiService personApiService;

    @Autowired
    private OrgUnitApiService orgUnitApiService;

    @Autowired
    private EPersonService ePersonService;

    @Autowired
    private ConfigurationService configurationService;

    public void initialize(Context context, EPerson eperson) {

        context.turnOffAuthorisationSystem();

        try {

            Optional<String> sciper = getSciperId(eperson);
            if (sciper.isPresent()) {
                initialize(context, eperson, sciper.get());
            } else {
                createPublicProfile(context, eperson);
            }

        } finally {
            context.restoreAuthSystemState();
        }

    }

    private void initialize(Context context, EPerson eperson, String sciper) {

        ResearcherProfile researcherProfile = findProfile(context, eperson)
            .or(() -> findProfileBySciper(context, eperson, sciper))
            .orElseGet(() -> createPublicProfile(context, eperson));

        personApiService.getPerson(sciper)
            .map(person -> sendEmailIfSomethingIsWrong(context, person))
            .filter(this::isMainAffiliationActive)
            .ifPresent(person -> enrichProfile(context, person, researcherProfile.getItem()));

    }

    public Optional<ResearcherProfile> findProfile(Context context, EPerson eperson) {
        try {
            return Optional.ofNullable(researcherProfileService.findById(context, eperson.getID()));
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException(e);
        }
    }

    private PersonDTO sendEmailIfSomethingIsWrong(Context context, PersonDTO person) {

        if (ArrayUtils.isEmpty(person.getAccreds())) {
            sendEmailForNoAffiliations(context, person);
        } else if (person.getMainAffiliation().isEmpty()) {
            sendEmailForNoMainAffiliation(context, person);
        }

        return person;
    }

    private Optional<ResearcherProfile> findProfileBySciper(Context context, EPerson eperson, String sciper) {

        String sciperMetadataField = personApiService.getSciperMetadataField();

        List<Item> items = findArchivedByMetadataField(context, sciperMetadataField, sciper);
        if (items.isEmpty()) {
            return Optional.empty();
        }

        if (items.size() > 1) {
            throw new IllegalStateException("Found many items with sciper " + sciper);
        }

        Item item = items.get(0);

        EPerson owner = getOwner(context, item);

        if (owner != null && !owner.equals(eperson)) {
            throw new IllegalStateException("An item with the sciper " + sciper + " is already linked "
                + "to another eperson: " + owner.getID());
        }

        setOwner(context, item, eperson);

        return Optional.of(new ResearcherProfile(item));

    }

    private void setOwner(Context context, Item item, EPerson ePerson) {
        try {
            itemService.clearMetadata(context, item, "dspace", "object", "owner", Item.ANY);
            itemService.addMetadata(context, item, "dspace", "object", "owner", null, ePerson.getName(),
                ePerson.getID().toString(), CF_ACCEPTED);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private EPerson getOwner(Context context, Item item) {
        try {
            return ePersonService.findByProfileItem(context, item);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Item> findArchivedByMetadataField(Context context, String sciperMetadataField, String sciper) {
        try {
            return toList(itemService.findArchivedByMetadataField(context, sciperMetadataField, sciper));
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException(e);
        }
    }

    private ResearcherProfile createPublicProfile(Context context, EPerson eperson) {
        try {
            ResearcherProfile profile = researcherProfileService.createAndReturn(context, eperson);
            if (profile.isVisible()) {
                researcherProfileService.changeVisibility(context, profile, true);
            }
            return profile;
        } catch (AuthorizeException | SQLException | SearchServiceException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isMainAffiliationActive(PersonDTO person) {
        return person.getMainAffiliation()
            .map(accred -> orgUnitApiService.isOrgUnitActive(accred.getAcronym()))
            .orElse(false);
    }

    private void enrichProfile(Context context, PersonDTO person, Item item) {

        List<MetadataValueDTO> metadataValues = personApiService.getMetadataValues(person);
        replaceMetadataValues(context, item, metadataValues);

        String sciper = person.getSciper();

        personApiService.getPersonalPicture(sciper)
            .ifPresent(content -> bitstreamService.replacePersonalPicture(context, item, sciper + ".jpg", content));

        try {
            itemService.update(context, item);
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException(e);
        }

    }

    private void sendEmailForNoAffiliations(Context context, PersonDTO person) {
        sendEmail(context, person, "person_synchronization_no_affiliations");
    }

    private void sendEmailForNoMainAffiliation(Context context, PersonDTO person) {
        sendEmail(context, person, "person_synchronization_no_main_affiliation");
    }

    private void sendEmail(Context context, PersonDTO person, String templateName) {
        try {
            Email email = Email.getEmail(getEmailFilename(context.getCurrentLocale(), templateName));
            email.addRecipient(configurationService.getProperty("mail.admin"));
            email.addArgument(person.getSciper());
            email.addArgument(person.getFullName());
            email.send();
        } catch (IOException | MessagingException e) {
            LOGGER.error("An error occurs sending the email related to the user synchronization", e);
        }
    }

    private void replaceMetadataValues(Context context, Item item, List<MetadataValueDTO> metadataValues) {
        clearMetadataValues(context, item);
        metadataValues.forEach(metadataValue -> addMetadataValue(context, item, metadataValue));
    }

    private void addMetadataValue(Context context, Item item, MetadataValueDTO metadataValue) {
        try {
            itemService.addSecuredMetadata(context, item, metadataValue.getSchema(), metadataValue.getElement(),
                metadataValue.getQualifier(), metadataValue.getLanguage(), metadataValue.getValue(),
                metadataValue.getAuthority(), metadataValue.getConfidence(), metadataValue.getSecurityLevel());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void clearMetadataValues(Context context, Item item) {
        personApiService.getMetadataFields()
            .forEach(metadataField -> clearMetadataValues(context, item, metadataField));
    }

    private void clearMetadataValues(Context context, Item item, String metadataField) {
        MetadataFieldName metadataFieldName = new MetadataFieldName(metadataField);
        try {
            itemService.clearMetadata(context, item, metadataFieldName.schema,
                metadataFieldName.element, metadataFieldName.qualifier, Item.ANY);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<String> getSciperId(EPerson eperson) {
        return ofNullable(eperson)
            .flatMap(ePerson -> ofNullable(ePerson.getNetid()))
            .map(netId -> StringUtils.substringBefore(netId, "@"));
    }

}
