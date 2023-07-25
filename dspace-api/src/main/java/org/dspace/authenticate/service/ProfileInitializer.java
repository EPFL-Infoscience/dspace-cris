/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate.service;

import static java.util.Optional.ofNullable;
import static org.dspace.content.authority.Choices.CF_ACCEPTED;
import static org.dspace.core.I18nUtil.getEmailFilename;

import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.mail.MessagingException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.discovery.SearchServiceException;
import org.dspace.eperson.EPerson;
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
    private ConfigurationService configurationService;

    public void initialize(Context context, EPerson eperson) {

        context.turnOffAuthorisationSystem();

        try {

            Optional<String> sciper = getSciperId(eperson);
            sciper.ifPresent(s -> initialize(context, eperson, s));

        } finally {
            context.restoreAuthSystemState();
        }

    }

    private void initialize(Context context, EPerson eperson, String sciper) {

        ResearcherProfile researcherProfile = findProfile(context, eperson)
            .or(() -> personApiService.findProfileBySciper(context, eperson, sciper))
            .orElseGet(() -> createPublicProfile(context, eperson));

        try {
            setPublicVisibility(context, researcherProfile);
        } catch (AuthorizeException | SQLException e) {
            throw new RuntimeException(e);
        }

        personApiService.getPerson(sciper)
            .map(person -> sendEmailIfSomethingIsWrong(context, person))
            .filter(this::isMainAffiliationActive)
            .ifPresent(person -> enrichProfile(context, person, researcherProfile.getItem(), eperson));

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


    private ResearcherProfile createPublicProfile(Context context, EPerson eperson) {
        try {
            if (eperson.getEmail() == null) {
                throw new RuntimeException("person does not have email");
            }
            ResearcherProfile profile = researcherProfileService.createAndReturn(context, eperson);
            setPublicVisibility(context, profile);
            return profile;
        } catch (AuthorizeException | SQLException | SearchServiceException e) {
            throw new RuntimeException(e);
        }
    }

    private void setPublicVisibility(Context context, ResearcherProfile profile)
        throws AuthorizeException, SQLException {
        if (!profile.isVisible()) {
            researcherProfileService.changeVisibility(context, profile, true);
        }
    }

    private boolean isMainAffiliationActive(PersonDTO person) {
        return person.getMainAffiliation()
            .map(accred -> orgUnitApiService.isOrgUnitActive(accred.getAcronym()))
            .orElse(false);
    }

    private void enrichProfile(Context context, PersonDTO person, Item item, EPerson ePerson) {

        List<MetadataValueDTO> metadataValues = personApiService.getMetadataValues(person);
        replaceMetadataValues(context, item, metadataValues, person, ePerson);

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

    private void replaceMetadataValues(Context context, Item item, List<MetadataValueDTO> metadataValues,
                                       PersonDTO epflPerson, EPerson ePerson) {
        List<String> ePersonUniqueAccredsNames = getAccredsAcronymsThatInEPersonButNotInEpflPerson(item, epflPerson);
        clearMetadataValues(context, item);
        addEndDateToExpierdedAccreds(context, item, ePersonUniqueAccredsNames, ePerson);
        metadataValues.forEach(metadataValue -> addMetadataValue(context, item, metadataValue));
    }

    private void addEndDateToExpierdedAccreds(Context context, Item item, List<String> ePersonUniqueAccredsNames,
                                              EPerson ePerson) {
        try {
            itemService.clearMetadata(context, item, "oairecerif", "affiliation", "endDate", Item.ANY);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        ePersonUniqueAccredsNames.stream()
                                 .map(name -> item.getMetadata()
                                                  .stream()
                                                  .filter(metadataValue -> metadataValue
                                                      .getValue()
                                                      .equals(name))
                                                  .map(MetadataValue::getPlace)
                                                  .collect(Collectors.toList()))
                                 .flatMap(Collection::stream)
                                 .forEach(place -> addEndDateMetadata(place, context, item, ePerson));
    }

    private void addEndDateMetadata(int place, Context context, Item item, EPerson ePerson) {
        try {

            itemService.addMetadata(context, item,
                                    "oairecerif", "affiliation", "endDate",
                                    null, getYesterday(),
                                    ePerson.getID().toString(), CF_ACCEPTED, place);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    private String getYesterday() {
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        return dateFormat.format(cal.getTime());
    }
    private List<String> getAccredsAcronymsThatInEPersonButNotInEpflPerson(Item item, PersonDTO epflPerson) {
        List<String> ePersonAccredsAcronym = item.getMetadata().stream()
                                                              .filter(metadataValue -> metadataValue
                                                                  .getMetadataField()
                                                                  .toString('.')
                                                                  .equals("oairecerif.person.affiliation"))
                                                              .map(metadataValue -> metadataValue.getAuthority()
                                                                                                 .split("::")[2])
                                                              .collect(Collectors.toList());

        List<String> epflPersonAccredsAcronym = Arrays.stream(epflPerson.getAccreds()).map(PersonDTO.Accred::getAcronym)
                                                      .collect(Collectors.toList());

        ePersonAccredsAcronym.removeAll(epflPersonAccredsAcronym);
        return ePersonAccredsAcronym;
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
        List<String> scipMetadata =
            List.of("oairecerif_affiliation_role", "oairecerif_person_affiliation",
                    "oairecerif_affiliation_startDate", "oairecerif_affiliation_endDate");

        personApiService.getMetadataFields()
                        .stream().filter(field -> !scipMetadata.contains(field))
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
