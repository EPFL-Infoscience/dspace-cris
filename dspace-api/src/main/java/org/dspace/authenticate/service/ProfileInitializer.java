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
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;
import static org.dspace.core.I18nUtil.getEmailFilename;

import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.mail.MessagingException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.authority.service.AuthorityValueService;
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
import org.dspace.eperson.Group;
import org.dspace.eperson.service.GroupService;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.epfl.service.OrgUnitApiService;
import org.dspace.epfl.service.PersonApiService;
import org.dspace.profile.ResearcherProfile;
import org.dspace.profile.service.ResearcherProfileService;
import org.dspace.services.ConfigurationService;
import org.dspace.util.UUIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ProfileInitializer {

    private static final List<String> AFFILIATIONS_METADATA =
        List.of("oairecerif.affiliation.role", "oairecerif.person.affiliation",
                "oairecerif.affiliation.startDate", "oairecerif.affiliation.endDate");
    private final static Logger LOGGER = LoggerFactory.getLogger(ProfileInitializer.class);
    private static final String SUBMITTERS = "Submitter";

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

    @Autowired
    private GroupService groupService;

    private DCInputsReader dcInputsReader;

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

        Optional<PersonDTO> personDTO = personApiService.getPerson(sciper);

        if (personDTO.isEmpty()) {
            throw new NoPersonFoundException("No person for sciper " + sciper + " was not found");
        }

        ResearcherProfile researcherProfile = findProfile(context, eperson)
            .or(() -> personApiService.findProfileBySciper(context, eperson, sciper))
            .orElseGet(() -> createPublicProfile(context, eperson, personDTO));



        if (researcherProfile == null) {
            PersonDTO personDTOForEmail = new PersonDTO();
            personDTOForEmail.setSciper(sciper);
            personDTOForEmail.setName(eperson.getName());
            personDTOForEmail.setFirstname(eperson.getFirstName());
            sendEmailForNoValidAffiliations(context, personDTOForEmail);
            LOGGER.warn("No valid accreditations for sciper {} profile not created", sciper);
            return;
        }

        try {
            setPublicVisibility(context, researcherProfile);
        } catch (AuthorizeException | SQLException e) {
            sendEmailForError(context, personDTO.get());
            throw new RuntimeException(e);
        }

        personDTO
//            .map(person -> sendEmailIfSomethingIsWrong(context, person))
            .filter(this::isMainAffiliationActive)
            .ifPresent(person -> enrichProfile(context, person, researcherProfile.getItem(),
                                                eperson, researcherProfile));

        try {
            addToSubmittersGroup(context, eperson, researcherProfile);
        } catch (SQLException e) {
            sendEmailForError(context, personDTO.get());
            throw new RuntimeException(e);
        }
        sendEmailForSuccess(context, personDTO.get());
    }

    private void addToSubmittersGroup(Context context, EPerson eperson, ResearcherProfile researcherProfile)
            throws SQLException {
        Group submittersGroup = groupService.findByName(context, SUBMITTERS);
        if (submittersGroup == null) {
            throw new RuntimeException(SUBMITTERS + " group not found, it must be created in order to correctly " +
                                           "synchronize users.");
        }

        if (atLeastAnActiveAccreditation(researcherProfile.getItem())) {
            groupService.addMember(context, submittersGroup, eperson);
        } else if (groupService.isMember(context, eperson, submittersGroup)) {
            groupService.removeMember(context, submittersGroup, eperson);
        }
    }

    private void removeFromSubmittersGroup(Context context, EPerson eperson, ResearcherProfile researcherProfile)
            throws SQLException {

        Group submittersGroup = groupService.findByName(context, SUBMITTERS);
        if (submittersGroup == null) {
            throw new RuntimeException(SUBMITTERS + " group not found, it must be created in order to correctly " +
                    "synchronize users.");
        }

        if (!atLeastAnActiveAccreditation(researcherProfile.getItem())
                && groupService.isMember(context, eperson, submittersGroup)) {
            groupService.removeMember(context, submittersGroup, eperson);
        }
    }

    private boolean atLeastAnActiveAccreditation(Item item) {
        return item.getMetadata().stream()
                   .filter(mv -> "oairecerif.affiliation.endDate".equals(mv.getMetadataField().toString('.')))
                   .anyMatch(mv -> PLACEHOLDER_PARENT_METADATA_VALUE.equals(mv.getValue()));
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


    private ResearcherProfile createPublicProfile(Context context, EPerson eperson, Optional<PersonDTO> personDTO) {


        if (!personDTO.isPresent()
            || noAccredsInDspace(context,
                                 personDTO
                                     .map(p -> sendEmailIfSomethingIsWrong(context, p))
                                     .map(PersonDTO::getAccreds)
                                     .orElse(new PersonDTO.Accred[]{}))) {
            return null;
        }
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

    private boolean noAccredsInDspace(Context context, PersonDTO.Accred[] accreds) {
        return Arrays.stream(accreds)
                     .map(a -> a.getAcronym())
                     .noneMatch(acro -> inDspace(context, acro));
    }

    private boolean inDspace(Context context, String acro) {
        return findItem(context, acro).isPresent();
    }

    private Optional<Item> findItem(Context context, String acro) {
        try {
            Iterator<Item> iterator =
                itemService.findArchivedByMetadataField(context, "oairecerif.acronym", acro);
            while (iterator.hasNext()) {
                Item item = iterator.next();
                String entityType = itemService.getEntityType(item);
                if ("OrgUnit".equals(entityType)) {
                    return Optional.of(item);
                }
            }
        } catch (AuthorizeException | SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    private void setPublicVisibility(Context context, ResearcherProfile profile)
        throws AuthorizeException, SQLException {
        if (!profile.isVisible()) {
            researcherProfileService.changeVisibility(context, profile, true);
        }
        itemService.setMetadataSingleValue(context, profile.getItem(),
                                           "epfl", "sciper",
                                           "active", null,
                                           "true");
    }

    private boolean isMainAffiliationActive(PersonDTO person) {
        return person.getMainAffiliation()
            .map(accred -> orgUnitApiService.isOrgUnitActive(accred.getAcronym()))
            .orElse(false);
    }

    private void enrichProfile(Context context, PersonDTO person, Item item,
                               EPerson ePerson, ResearcherProfile researcherProfile) {

        List<MetadataValueDTO> metadataValues = personApiService.getMetadataValues(context, person);
        replaceMetadataValues(context, item, metadataValues, person, ePerson, researcherProfile);

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

    private void sendEmailForNoValidAffiliations(Context context, PersonDTO person) {
        sendEmail(context, person, "person_synchronization_no_valid_affiliations");
    }

    private void sendEmailForNoMainAffiliation(Context context, PersonDTO person) {
        sendEmail(context, person, "person_synchronization_no_main_affiliation");
    }

    private void sendEmailForSuccess(Context context, PersonDTO person) {
        sendEmail(context, person, "error_during_profile_initialization");
    }

    private void sendEmailForError(Context context, PersonDTO person) {
        sendEmail(context, person, "error_during_profile_initialization");
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
                                       PersonDTO epflPerson, EPerson ePerson, ResearcherProfile researcherProfile) {
        List<PersonAffiliation> personAffiliations = affiliations(context, item);
        List<Integer> affiliationsToClosePositions = affiliationsToBeClosedPositions(item, context,
                                                                                     epflPerson, personAffiliations);
        if (personAffiliations.isEmpty() || personAffiliations.size() == affiliationsToClosePositions.size()) {
            try {
                removeFromSubmittersGroup(context, ePerson, researcherProfile);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        clearMetadataValues(context, item);
        addEndDateToExpiredAccreds(context, item, affiliationsToClosePositions);
        List<PersonAffiliation> apiAffiliations = apiAffiliations(metadataValues);
        List<PersonAffiliation> alreadySetAffiliations =
            alreadyPresentAffiliations(personAffiliations, apiAffiliations);
        metadataValues.stream().filter(mv -> notAnAlreadySetAffiliation(mv, alreadySetAffiliations))
                      .forEach(metadataValue -> addMetadataValue(context, item, metadataValue));
    }

    private boolean notAnAlreadySetAffiliation(MetadataValueDTO metadataValue,
                                               List<PersonAffiliation> alreadySetAffiliations) {
        if (!AFFILIATIONS_METADATA.contains(metadataValue.getMetadataField())) {
            return true;
        }
        return alreadySetAffiliations.stream().noneMatch(pa -> Objects.equals(pa.position, metadataValue.getPlace()));
    }

    private List<PersonAffiliation> alreadyPresentAffiliations(List<PersonAffiliation> personAffiliations,
                                                               List<PersonAffiliation> apiAffiliations) {
        return apiAffiliations.stream()
                              .filter(aff -> {
                                  Predicate<PersonAffiliation> acronymPredicate =
                                      pa -> pa.acronym.equals(aff.acronym);
                                  Predicate<PersonAffiliation> notTerminated =
                                      pa -> StringUtils.isBlank(pa.endDate) ||
                                          pa.endDate.equals(PLACEHOLDER_PARENT_METADATA_VALUE);
                                  return personAffiliations
                                      .stream()
                                      .anyMatch(acronymPredicate.and(notTerminated));
                              }).collect(Collectors.toList());
    }

    private List<PersonAffiliation> apiAffiliations(List<MetadataValueDTO> metadataValues) {
        return metadataValues
            .stream()
            .filter(mv -> "oairecerif.person.affiliation".equals(mv.getMetadataField()))
            .filter(mv -> StringUtils.isNotBlank(mv.getAuthority()))
            .map(mv -> new PersonAffiliation(mv.getPlace(),
                                             getAcronym(mv),
                                             PLACEHOLDER_PARENT_METADATA_VALUE,
                                             PLACEHOLDER_PARENT_METADATA_VALUE))
            .collect(Collectors.toList());

    }

    private static String getAcronym(MetadataValueDTO mv) {
        if (mv.getAuthority().startsWith(AuthorityValueService.GENERATE)) {
            return StringUtils.substringAfter(mv.getAuthority(), AuthorityValueService.GENERATE + "ACRONYM::");
        }
        if (mv.getAuthority().startsWith(AuthorityValueService.REFERENCE)) {
            return StringUtils.substringAfter(mv.getAuthority(), AuthorityValueService.REFERENCE + "ACRONYM::");
        }
        return mv.getAuthority();
    }


    private List<PersonAffiliation> affiliations(Context context, Item item) {
        Map<Integer, List<MetadataValue>> metadataMap = item.getMetadata().stream()
                                                        .filter(mv -> AFFILIATIONS_METADATA.contains(
                                                            mv.getMetadataField().toString('.')))
                                                        .collect(Collectors.groupingBy(mv -> mv.getPlace()));
        List<PersonAffiliation> result = new LinkedList<>();
        return metadataMap.entrySet().stream()
            .map(e -> toAffiliation(context, e))
            .collect(Collectors.toList());
    }

    private PersonAffiliation toAffiliation(Context context, Map.Entry<Integer, List<MetadataValue>> metadataMap) {

        List<MetadataValue> metadataValues = metadataMap.getValue();
        String acronym = acronym(context, extractMetadata(metadataValues, "oairecerif.person.affiliation"));
        String startDate = metadataValue(metadataValues, "oairecerif.affiliation.startDate");
        String endDate = metadataValue(metadataValues, "oairecerif.affiliation.endDate");
        return new ProfileInitializer.PersonAffiliation(metadataMap.getKey(),
                                     acronym, startDate, endDate);
    }

    private String metadataValue(List<MetadataValue> metadataValues, String metadata) {
        return Optional.ofNullable(
                           extractMetadata(metadataValues, metadata)).map(mv -> mv.getValue())
                       .orElse(null);
    }

    private MetadataValue extractMetadata(List<MetadataValue> metadataValues, String metadata) {
        return metadataValues
            .stream()
            .filter(mv -> mv.getMetadataField().toString('.').equals(metadata))
            .findFirst().orElse(null);
    }

    private void addEndDateToExpiredAccreds(Context context, Item item, List<Integer> endDatesMetadataPositions) {
        // if some placeholder values in affiliation nested metadata are missing, this method might throw an exception
        fillAffiliationsMetadata(context, item);
        List<MetadataValue> values = itemService.getMetadataByMetadataString(item, "oairecerif.affiliation.endDate");
        Map<Integer, MetadataValue> endDates =
            values.stream().collect(Collectors.toMap(mv -> mv.getPlace(), Function.identity()));
        endDatesMetadataPositions.forEach(
            pos -> {
                if (valueToBeReplaced(endDates.get(pos))) {
                    try {
                        itemService.replaceMetadata(context, item, "oairecerif", "affiliation",
                                                    "endDate", null, getYesterday(), null, -1,
                                                    pos);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        );
    }

    private void fillAffiliationsMetadata(Context context, Item item) {
        try {
            List<MetadataValue> affiliationMetadata =
                itemService.getMetadataByMetadataString(item, "oairecerif.person.affiliation");
            DCInputSet inputs = dcInputsReader().getInputsByFormName("person-oairecerif-person-affiliation");
            List<String> metadataToAdd =
                inputs.getMetadataFields().stream().filter(s -> !"oairecerif.person.affiliation".equals(s))
                      .collect(Collectors.toList());
            affiliationMetadata.forEach(mv -> addPlaceholders(context, mv.getPlace(), item, metadataToAdd));
            itemService.update(context, item);

        } catch (DCInputsReaderException | SQLException | AuthorizeException e) {
            throw new RuntimeException(e);
        }
    }

    private DCInputsReader dcInputsReader() throws DCInputsReaderException {
        return new DCInputsReader();
    }

    private void addPlaceholders(Context context, int place, Item item, List<String> metadataToAdd) {
        metadataToAdd.stream()
            .filter(s -> notAlreadySet(item, s, place))
            .map(mv -> toMetadataValue(context, item, place, mv))
            .forEach(mv -> addMetadataValueInPosition(context, item, mv));
    }

    private boolean notAlreadySet(Item item, String field, int place) {
        return itemService.getMetadataByMetadataString(item, field)
            .stream().noneMatch(mv -> place == mv.getPlace());
    }

    private MetadataValueDTO toMetadataValue(Context context, Item item, int place, String mv) {
        return new MetadataValueDTO(mv, PLACEHOLDER_PARENT_METADATA_VALUE, place);
    }

    private void addMetadataValueInPosition(Context context, Item item, MetadataValueDTO mv) {
        try {
            itemService.addMetadata(context, item, mv.getSchema(), mv.getElement(), mv.getQualifier(),
                                    null, mv.getValue(), mv.getAuthority(), mv.getConfidence(),
                                    mv.getPlace());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean valueToBeReplaced(MetadataValue metadataValue) {
        return metadataValue == null || StringUtils.isBlank(metadataValue.getValue()) ||
            PLACEHOLDER_PARENT_METADATA_VALUE.equals(metadataValue.getValue());
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
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(cal.getTime());
    }
    private List<Integer> affiliationsToBeClosedPositions(Item item, Context context, PersonDTO epflPerson,
                                                          List<PersonAffiliation> personAffiliations) {

        Map<String, List<PersonAffiliation>> collect =
            personAffiliations.stream().collect(Collectors.groupingBy(pa -> pa.acronym));

        List<String> epflPersonAccredsAcronym = Arrays.stream(epflPerson.getAccreds()).map(PersonDTO.Accred::getAcronym)
                                                      .collect(Collectors.toList());

        epflPersonAccredsAcronym.forEach(collect::remove);
        Set<Integer> collect1 =
            collect.entrySet().stream()
                   .flatMap(e -> e.getValue().stream()).map(pa -> pa.position)
                   .collect(Collectors.toSet());
        return new ArrayList<>(collect1);
    }

    private String acronym(Context context, MetadataValue metadataValue) {
        if (StringUtils.isBlank(metadataValue.getAuthority())) {
            return "PLACEHOLDER";
        }
        if (metadataValue.getAuthority().startsWith(AuthorityValueService.GENERATE)) {
            return metadataValue.getAuthority()
                                .split(AuthorityValueService.SPLIT)[2];
        }
        try {
            Item item = itemService.find(context, UUIDUtils.fromString(metadataValue.getAuthority()));
            if (item == null) {
                return "PLACEHOLDER";
            }
            return itemService.getMetadataFirstValue(item, new MetadataFieldName("oairecerif.acronym"), Item.ANY);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
                        .stream().filter(field -> !AFFILIATIONS_METADATA.contains(field))
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

    private static class PersonAffiliation {
        private final Integer position;
        private final String acronym;
        private final String startDate;
        private final String endDate;

        public PersonAffiliation(Integer position, String acronym, String startDate, String endDate) {

            this.position = position;
            this.acronym = acronym;
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}
