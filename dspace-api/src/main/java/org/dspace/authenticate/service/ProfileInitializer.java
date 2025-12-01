/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate.service;

import static java.util.Optional.ofNullable;
import static org.dspace.content.authority.Choices.CF_UNSET;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;
import static org.dspace.core.I18nUtil.getEmailFilename;

import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.mail.MessagingException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.authority.service.AuthorityValueService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.CrisConstants;
import org.dspace.core.Email;
import org.dspace.discovery.SearchServiceException;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.client.EpflApiClientImpl;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.epfl.client.model.PersonDTO.Accred;
import org.dspace.epfl.service.PersonApiService;
import org.dspace.orcid.OrcidQueue;
import org.dspace.orcid.service.OrcidQueueService;
import org.dspace.orcid.service.OrcidTokenService;
import org.dspace.orcid.service.OrcidWebhookService;
import org.dspace.profile.ResearcherProfile;
import org.dspace.profile.service.ResearcherProfileService;
import org.dspace.services.ConfigurationService;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ProfileInitializer {

    private static final List<String> AFFILIATIONS_METADATA =
        List.of("oairecerif.affiliation.role", "oairecerif.person.affiliation",
                "oairecerif.affiliation.startDate", "oairecerif.affiliation.endDate");
    private final static Logger LOGGER = LoggerFactory.getLogger(ProfileInitializer.class);
    public static final String SUBMITTERS = "Submitter";

    @Autowired
    private ResearcherProfileService researcherProfileService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private BitstreamService bitstreamService;

    @Autowired
    private PersonApiService personApiService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private EPersonService epersonService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private OrcidQueueService orcidQueueService;

    @Autowired
    private OrcidWebhookService orcidWebhookService;

    @Autowired
    private OrcidTokenService orcidTokenService;

    @Autowired
    private ProfileInitializer profileInitializer;

    private EpflApiClientImpl epflApiClient;

    public ProfileInitializer() {
        epflApiClient = new DSpace().getServiceManager()
                .getServiceByName("org.dspace.epfl.client.EpflApiClientImpl",
                                  EpflApiClientImpl.class);
    }

    public boolean syncEPerson(Context context, PersonDTO epflPerson, EPerson ePerson)
            throws SQLException, AuthorizeException {
        boolean needsToBEUpdated = false;

        if (isValueNeedsToBeUpdated(ePerson.getEmail(), epflPerson.getEmail())) {
            ePerson.setEmail(epflPerson.getEmail());
            needsToBEUpdated = true;
        }

        if (isValueNeedsToBeUpdated(ePerson.getFirstName(), epflPerson.getFirstname())) {
            ePerson.setFirstName(context, epflPerson.getFirstname());
            needsToBEUpdated = true;
        }
        if (isValueNeedsToBeUpdated(ePerson.getLastName(), epflPerson.getName())) {
            ePerson.setLastName(context, epflPerson.getName());
            needsToBEUpdated = true;
        }
        if (isValueNeedsToBeUpdated(ePerson.getNetid(), epflPerson.getSciper() + "@epfl.ch")) {
            ePerson.setNetid(epflPerson.getSciper() + "@epfl.ch");
            needsToBEUpdated = true;
        }

        // this check would eventually fix also the owner of an existing unlink person item
        if (isNeedToSyncAffiliations(context, ePerson, epflPerson)) {
            if (epflPerson.getAccreds() != null && epflPerson.getAccreds().length != 0) {
                needsToBEUpdated = true;
            }
        }

        if (needsToBEUpdated) {
            ResearcherProfile researcherProfile = initialize(context, ePerson, epflPerson.getSciper(),
                    Optional.of(epflPerson));
            setSynchronizationMetadata(context, ePerson, researcherProfile);
            epersonService.update(context, ePerson);
            if (researcherProfile != null) {
                itemService.update(context, researcherProfile.getItem());
            }
        }
        return needsToBEUpdated;
    }

    /**
     * Closes affiliations and deactivates the profile of a given ePerson in the system. It sets the relevant
     * metadata to indicate the profile is inactive, clears associated metadata like email and affiliations,
     * removes the ePerson from relevant groups, and performs additional cleanup tasks.
     *
     * @param context The DSpace context object used for accessing services and managing operations.
     * @param ePerson The ePerson for whom the profile should be deactivated and affiliations closed.
     * @param sciper A unique identifier (sciper) for the ePerson.
     * @throws SQLException If a database error occurs during the operation.
     * @throws AuthorizeException If the current user does not have authorization to perform the operation.
     */
    public void closeAffiliationsAndDeactivateProfile(Context context, EPerson ePerson, String sciper)
            throws SQLException, AuthorizeException {
        ResearcherProfile researcherProfile = researcherProfileService.findById(context, ePerson.getID());
        Group submitters = groupService.findByName(context, SUBMITTERS);
        groupService.removeMember(context, submitters, ePerson);
        if (researcherProfile == null) {
            return;
        }
        Item person = researcherProfile.getItem();
        itemService.setMetadataSingleValue(context, person,
                                           "epfl", "sciper",
                                           "active", null,
                                           "false");
        itemService.clearMetadata(context, person, "oairecerif", "identifier", "url", "*");
        itemService.clearMetadata(context, person, "person", "affiliation", "name", "*");
        itemService.clearMetadata(context, person, "person", "email", null, "*");

        bitstreamService.deletePersonalPictureAndThumbnail(context, person);

        int affiliations =
            itemService.getMetadata(person, "oairecerif.person.affiliation", Item.ANY).size();
        if (affiliations != 0) {
            String yesterday = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                                .format(LocalDate.now().minusDays(1L));
            Map<Integer, MetadataValue> affiliationEndDates =
                itemService.getMetadata(person, "oairecerif.affiliation.endDate", Item.ANY)
                           .stream().collect(Collectors.toMap(MetadataValue::getPlace, Function.identity()));

            for (int i = 0; i < affiliations; i++) {
                MetadataValue metadataValue = affiliationEndDates.get(i);

                if (metadataValue == null
                    || StringUtils.isBlank(metadataValue.getValue())
                    || CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE.equals(metadataValue.getValue())) {
                    itemService.replaceMetadata(context, researcherProfile.getItem(), "oairecerif", "affiliation",
                                                "endDate", null, yesterday, null, -1, i);
                }
            }
        }
        setSynchronizationMetadata(context, ePerson, researcherProfile);
        deleteOrcidSynchronization(context, ePerson);
        itemService.update(context, researcherProfile.getItem());
    }

    public EPerson findPerson(Context context, PersonDTO epflPerson) throws SQLException {
        EPerson byNetid = findPersonBySciper(context, epflPerson.getSciper());
        if (byNetid != null) {
            return byNetid;
        }
        return epersonService.findByEmail(context, epflPerson.getEmail());
    }

    public EPerson findPersonBySciper(Context context, String sciper) throws SQLException {
        return epersonService.findByNetid(context, sciper + "@epfl.ch");
    }

    public EPerson createAndSyncEPerson(Context context, PersonDTO epflPerson) throws SQLException, AuthorizeException {
        if (epflPerson.getAccreds() == null || epflPerson.getAccreds().length == 0) {
            return null;
        }
        EPerson newEPerson = epersonService.create(context);

        newEPerson.setNetid(epflPerson.getSciper() + "@epfl.ch");
        newEPerson.setEmail(Optional.ofNullable(epflPerson.getEmail()).orElse(epflPerson.getSciper() + "@epfl.ch"));
        newEPerson.setFirstName(context, epflPerson.getFirstname());
        newEPerson.setLastName(context, epflPerson.getName());
        newEPerson.setCanLogIn(true);

        ResearcherProfile profile = createOrUpdateProfile(context, newEPerson);

        setSynchronizationMetadata(context, newEPerson, profile);
        epersonService.update(context, newEPerson);
        return newEPerson;
    }

    public EPerson createBasicEPerson(Context context, String sciper) throws SQLException, AuthorizeException {
        EPerson newEPerson = epersonService.create(context);
        newEPerson.setNetid(sciper + "@epfl.ch");
        newEPerson.setEmail(sciper + "@epfl.ch");
        newEPerson.setFirstName(context, "Unnamed");
        newEPerson.setLastName(context, "Unnamed");
        newEPerson.setCanLogIn(true);
        epersonService.update(context, newEPerson);
        return newEPerson;
    }

    public Optional<PersonDTO> getPersonFromEPFL(String sciperId) {
        try {
            return epflApiClient.getPerson(sciperId, EpflApiClient.Language.EN);
        } catch (Exception e) {
            LOGGER.error("Exception trying to recover the eperson from epfl api for sciperId: " + sciperId, e);
            return null;
        }
    }

    private void setSynchronizationMetadata(Context context, EPerson ePerson, ResearcherProfile researcherProfile)
            throws SQLException, AuthorizeException {
        String synchronizationDate = DCDate.getCurrent().toString();
        epersonService.setMetadataSingleValue(context, ePerson,
                                              "epfl", "synchronization",
                                              "date", null,
                                              synchronizationDate);
        if (researcherProfile != null) {
            itemService.setMetadataSingleValue(context, researcherProfile.getItem(), "epfl", "synchronization", "date",
                    null, synchronizationDate);
        }
    }

    private boolean isValueNeedsToBeUpdated(String ePersonValue, String epflPersonValue) {
        if (ePersonValue == null) {
            return epflPersonValue != null;
        }
        return !ePersonValue.equals(epflPersonValue);
    }

    private boolean isNeedToSyncAffiliations(Context context, EPerson ePerson, PersonDTO epflPerson) {
        Optional<ResearcherProfile> researcherProfileOptional;
        try {
            researcherProfileOptional = findProfile(context, ePerson)
                    .or(() -> personApiService.findProfileBySciperAndFixOwnerIfNeeded(
                            context, ePerson,
                            epflPerson.getSciper()));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        ResearcherProfile researcherProfile = null;

        if (epflPerson.getMainAffiliation().isEmpty() && researcherProfileOptional.isEmpty()) {
            return false;
        }
        if (researcherProfileOptional.isEmpty() ^ epflPerson.getMainAffiliation().isEmpty()) {
            return true;
        }
        // researcherProfileOption must be present at this time otherwise one of the two previous if statements should
        // be executed
        if (researcherProfileOptional.isPresent()) {
            researcherProfile = researcherProfileOptional.get();
        }
        return isEPersonAndEpflPersonAccredsNotMatch(context, researcherProfile, epflPerson);
    }

    private boolean isEPersonAndEpflPersonAccredsNotMatch(Context context, ResearcherProfile researcherProfile,
            PersonDTO epflPerson) {
        List<String> ePersonAccredsAcronym = researcherProfile.getItem().getMetadata().stream()
                        .filter(metadataValue -> metadataValue
                            .getMetadataField()
                            .toString('.')
                            .equals("oairecerif.person.affiliation"))
                        .map(mv -> acronym(context, mv))
                        .collect(Collectors.toList());

        List<String> epflPersonAccredsAcronym = Arrays.stream(epflPerson.getAccreds()).map(PersonDTO.Accred::getAcronym)
                .collect(Collectors.toList());

        Collections.sort(ePersonAccredsAcronym);
        Collections.sort(epflPersonAccredsAcronym);
        return !ePersonAccredsAcronym.equals(epflPersonAccredsAcronym);
    }

    private String acronym(Context context, MetadataValue metadataValue) {
        if (StringUtils.isBlank(metadataValue.getAuthority())
            && StringUtils.isBlank(metadataValue.getValue())) {
            return "PLACEHOLDER";
        }
        if (StringUtils.startsWithAny(metadataValue.getAuthority(),
            new String[] { AuthorityValueService.GENERATE, AuthorityValueService.REFERENCE })) {
            return metadataValue.getAuthority()
                .split(Pattern.quote(AuthorityValueService.SPLIT))[2];
        }
        if (metadataValue.getAuthority() == null) {
            return metadataValue.getValue();
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


    public ResearcherProfile createOrUpdateProfile(Context context, EPerson eperson) {
        context.turnOffAuthorisationSystem();
        try {

            Optional<String> sciper = getSciperId(eperson);
            if (sciper.isPresent()) {
                return initialize(context, eperson, sciper.get());
            } else {
                return null;
            }
        } finally {
            context.restoreAuthSystemState();
        }

    }

    private ResearcherProfile initialize(Context context, EPerson eperson, String sciper) {

        Optional<PersonDTO> personDTO = personApiService.getPerson(sciper);

        if (personDTO.isEmpty()) {
            throw new NoPersonFoundException("No person for sciper " + sciper + " was not found");
        }

        return initialize(context, eperson, sciper, personDTO);
    }

    public ResearcherProfile initialize(Context context, EPerson eperson, String sciper,
            Optional<PersonDTO> personDTO) {
        ResearcherProfile researcherProfile = findProfile(context, eperson)
            .or(() -> personApiService.findProfileBySciperAndFixOwnerIfNeeded(context, eperson, sciper))
            .orElseGet(() -> createPublicProfile(context, eperson, personDTO));

        if (researcherProfile == null) {
            PersonDTO personDTOForEmail = new PersonDTO();
            personDTOForEmail.setSciper(sciper);
            personDTOForEmail.setName(eperson.getName());
            personDTOForEmail.setFirstname(eperson.getFirstName());
            sendEmailForNoValidAffiliations(context, personDTOForEmail);
            LOGGER.warn("No valid accreditations for sciper {} profile not created", sciper);
            return null;
        }

        try {
            setPublicVisibility(context, researcherProfile);
        } catch (AuthorizeException | SQLException e) {
            sendEmailForError(context, personDTO.get());
            throw new RuntimeException(e);
        }

        personDTO
            .ifPresent(person -> enrichProfile(context, person, researcherProfile.getItem(),
                                                eperson, researcherProfile));

        try {
            addToSubmittersGroup(context, eperson, researcherProfile);
        } catch (SQLException e) {
            sendEmailForError(context, personDTO.get());
            throw new RuntimeException(e);
        }
        return researcherProfile;
    }

    public Optional<String> getSciperId(EPerson eperson) {
        return ofNullable(eperson)
                .flatMap(ePerson -> ofNullable(ePerson.getNetid()))
                .map(netId -> org.apache.commons.lang.StringUtils.substringBefore(netId, "@"));
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
            context.turnOffAuthorisationSystem();
            groupService.removeMember(context, submittersGroup, eperson);
            context.restoreAuthSystemState();
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


        if (personDTO.isEmpty()
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
                     .map(Accred::getAcronym)
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
        itemService.setMetadataSingleValue(context, profile.getItem(), "epfl", "sciper", "active", null, "true");
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
        List<PersonAffiliation> apiAffiliations = apiAffiliations(metadataValues);

        List<Integer> affiliationsToClosePositions = affiliationsToBeClosedPositions(item, context,
            epflPerson, personAffiliations, apiAffiliations);
        if (personAffiliations.isEmpty() || personAffiliations.size() == affiliationsToClosePositions.size()) {
            try {
                removeFromSubmittersGroup(context, ePerson, researcherProfile);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        clearMetadataValues(context, item);
        addEndDateToExpiredAccreds(context, item, affiliationsToClosePositions);
        List<PersonAffiliation> alreadySetAffiliations =
            alreadyPresentAffiliations(personAffiliations, apiAffiliations);
        metadataValues.stream().filter(mv -> !isAlreadySetAffiliation(mv, alreadySetAffiliations))
                      .forEach(metadataValue -> addMetadataValue(context, item, metadataValue));
    }

    private boolean isAlreadySetAffiliation(MetadataValueDTO metadataValue,
                                            List<PersonAffiliation> alreadySetAffiliations) {
        return AFFILIATIONS_METADATA.contains(metadataValue.getMetadataField())
            && alreadySetAffiliations.stream().anyMatch(pa -> Objects.equals(pa.position, metadataValue.getPlace()));
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
                                             mv.getValue(),
                                             PLACEHOLDER_PARENT_METADATA_VALUE,
                                             PLACEHOLDER_PARENT_METADATA_VALUE))
            .collect(Collectors.toList());

    }

    private List<PersonAffiliation> affiliations(Context context, Item item) {
        // Group metadata by place for affiliations metadata fields
        Map<Integer, List<MetadataValue>> metadataMap = new HashMap<>();

        for (MetadataValue mv : item.getMetadata()) {
            if (AFFILIATIONS_METADATA.contains(mv.getMetadataField().toString('.'))) {
                int place = mv.getPlace();
                if (!metadataMap.containsKey(place)) {
                    metadataMap.put(place, new ArrayList<>());
                }
                metadataMap.get(place).add(mv);
            }
        }

        // Convert grouped metadata entries to affiliations and collect them into a list
        List<PersonAffiliation> affiliations = new ArrayList<>();
        for (Map.Entry<Integer, List<MetadataValue>> entry : metadataMap.entrySet()) {
            affiliations.add(toAffiliation(context, entry));
        }

        return affiliations;
    }


    private PersonAffiliation toAffiliation(Context context, Map.Entry<Integer, List<MetadataValue>> metadataMap) {
        List<MetadataValue> metadataValues = metadataMap.getValue();
        String acronym = acronym(context, extractMetadata(metadataValues, "oairecerif.person.affiliation"));
        String startDate = metadataValue(metadataValues, "oairecerif.affiliation.startDate");
        String endDate = metadataValue(metadataValues, "oairecerif.affiliation.endDate");
        return new ProfileInitializer.PersonAffiliation(metadataMap.getKey(), acronym, startDate, endDate);
    }

    private String metadataValue(List<MetadataValue> metadataValues, String metadata) {
        return Optional.ofNullable(extractMetadata(metadataValues, metadata))
                       .map(MetadataValue::getValue)
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
                                    null, CF_UNSET, place);
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
        List<PersonAffiliation> personAffiliations, List<PersonAffiliation> apiAffiliations) {

        // Group affiliations by acronym
        Map<String, List<PersonAffiliation>> collect = new HashMap<>();
        for (PersonAffiliation pa : personAffiliations) {
            collect.computeIfAbsent(pa.acronym, k -> new ArrayList<>()).add(pa);
        }

        // Extract the acronyms of the person's accreditations
        List<String> epflPersonAccredsAcronym = new ArrayList<>();
        for (PersonAffiliation pa : apiAffiliations) {
            epflPersonAccredsAcronym.add(pa.acronym);
        }

        // Remove affiliations already present in the person's acronyms list
        for (String acronym : epflPersonAccredsAcronym) {
            collect.remove(acronym);
        }

        // Extract the positions of the remaining affiliations
        Set<Integer> collect1 = new HashSet<>();
        for (Map.Entry<String, List<PersonAffiliation>> entry : collect.entrySet()) {
            for (PersonAffiliation pa : entry.getValue()) {
                collect1.add(pa.position);
            }
        }

        return new ArrayList<>(collect1);
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

    public EpflApiClientImpl getEpflApiClient() {
        return epflApiClient;
    }

    public void setEpflApiClient(EpflApiClientImpl epflApiClient) {
        this.epflApiClient = epflApiClient;
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

    public boolean isDeactivated(Context context, EPerson ePerson) {
        Optional<ResearcherProfile> rpOpt = findProfile(context, ePerson);
        if (!rpOpt.isPresent()) {
            return true;
        }

        Item personItem = rpOpt.get().getItem();
        String val = itemService.getMetadataFirstValue(
                personItem, new MetadataFieldName("epfl.sciper.active"), Item.ANY);

        return StringUtils.equalsIgnoreCase(val, "false");
    }

    public void deleteOrcidSynchronization(Context context, EPerson ePerson) throws SQLException {
        Optional<ResearcherProfile> rpOpt = profileInitializer.findProfile(context, ePerson);

        if (rpOpt.isEmpty()) {
            LOGGER.warn("No ResearcherProfile found for ePerson " + ePerson.getID());
            return;
        }

        Item profile = rpOpt.get().getItem();
        if (profile == null) {
            LOGGER.warn("No profile found for ResearcherProfile " + rpOpt.get().getId());
            return;
        }

        // deactivate the orcid webhook
        unregisterOrcidWebhook(context, profile);

        // erase the synchronization metadata
        clearOrcidMetadata(context, profile);

        // delete token from database
        try {
            orcidTokenService.deleteByProfileItem(context, profile);
        } catch (Exception e) {
            LOGGER.error("Error deleting the orcid token for profile: " + profile.getID(), e);
        }

        // delete orcid queue
        deleteOrcidQueue(context, profile);
    }

    private void unregisterOrcidWebhook(Context context, Item profile) {
        try {
            if (orcidWebhookService.isProfileRegistered(profile)) {
                orcidWebhookService.unregister(context, profile);
            }
        } catch (Exception e) {
            LOGGER.error("Unable to unregister orcid webhook for profile " + profile.getID());
        }
    }

    private void clearOrcidMetadata(Context context, Item profile) throws SQLException {
        try {
            itemService.clearMetadata(context, profile, "dspace", "orcid", "scope", Item.ANY);
            itemService.clearMetadata(context, profile, "dspace", "orcid", "sync-mode", Item.ANY);
            itemService.clearMetadata(context, profile, "dspace", "orcid", "sync-publications", Item.ANY);
            itemService.clearMetadata(context, profile, "dspace", "orcid", "sync-products", Item.ANY);
            itemService.clearMetadata(context, profile, "dspace", "orcid", "sync-patents", Item.ANY);
            itemService.clearMetadata(context, profile, "dspace", "orcid", "sync-fundings", Item.ANY);
            itemService.clearMetadata(context, profile, "dspace", "orcid", "sync-profile", Item.ANY);
        } catch (SQLException e) {
            LOGGER.error("Error deleting orcid metadata for profile " + profile.getID(), e);
        }
    }

    private void deleteOrcidQueue(Context context, Item profile) {
        try {
            List<OrcidQueue> queueRecords = orcidQueueService.findByProfileItemId(context, profile.getID());
            for (OrcidQueue queueRecord : queueRecords) {
                orcidQueueService.delete(context, queueRecord);
            }
        } catch (SQLException e) {
            LOGGER.error("Error deleting the orcid queue for profile " + profile.getID(), e);
        }
    }


}
