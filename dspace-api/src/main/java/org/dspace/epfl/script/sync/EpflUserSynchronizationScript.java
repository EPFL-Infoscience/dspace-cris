/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.sync;


import static org.dspace.core.I18nUtil.getEmailFilename;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.mail.MessagingException;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.authenticate.service.ProfileInitializer;
import org.dspace.authority.service.AuthorityValueService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.ItemServiceImpl;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.CrisConstants;
import org.dspace.core.Email;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.EPersonServiceImpl;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.client.EpflApiClientImpl;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.epfl.script.parser.CSVParserImpl;
import org.dspace.epfl.service.PersonApiService;
import org.dspace.epfl.service.impl.PersonApiServiceImpl;
import org.dspace.profile.ResearcherProfile;
import org.dspace.profile.service.ResearcherProfileService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;


public class EpflUserSynchronizationScript
    extends DSpaceRunnable<EpflUserSynchronizationScriptConfiguration<EpflUserSynchronizationScript>> {

    private static final String SUBMITTERS = "Submitter";
    private String inputFile;
    private String query;
    private String log;
    private int createdPersonCount = 0;
    private int updatedPersonCount = 0;

    private String email;

    private Context context;
    private ResearcherProfileService researcherProfileService;
    private EPersonServiceImpl ePersonService;
    private EpflApiClientImpl epflApiClient;
    private ProfileInitializer profileInitializer;
    private ConfigurationService configurationService;
    private PersonApiService personApiService;
    private ItemService itemService;

    private GroupService groupService;


    @Override
    @SuppressWarnings("unchecked")
    public EpflUserSynchronizationScriptConfiguration<EpflUserSynchronizationScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("epfl-user-synchronization",
                                                                 EpflUserSynchronizationScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        ePersonService = new DSpace().getServiceManager()
                                     .getServiceByName("org.dspace.eperson.EPersonServiceImpl",
                                                       EPersonServiceImpl.class);
        epflApiClient = new DSpace().getServiceManager()
                                    .getServiceByName("org.dspace.epfl.client.EpflApiClientImpl",
                                                      EpflApiClientImpl.class);
        profileInitializer = new DSpace().getSingletonService(ProfileInitializer.class);
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        personApiService = new DSpace().getSingletonService(PersonApiServiceImpl.class);
        researcherProfileService = new DSpace().getSingletonService(ResearcherProfileService.class);
        itemService = new DSpace().getSingletonService(ItemServiceImpl.class);
        groupService = EPersonServiceFactory.getInstance().getGroupService();
        inputFile = commandLine.getOptionValue('f');
        query = commandLine.getOptionValue('q');
        email = commandLine.getOptionValue('e');


        log = "";
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();
        if (query != null && inputFile != null) {
            throw new IllegalArgumentException("query and file parameter cannot be set both when process runs");
        }

        try {
            context.turnOffAuthorisationSystem();

            if (inputFile == null && query == null) {
                executeScriptWithOutQuery();
            } else {
                executeScriptWithQuery();
            }

            context.complete();
            finalLogging();
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private void executeScriptWithOutQuery() throws SQLException, AuthorizeException {
        List<EPerson> ePersonList = ePersonService.findAll(context, 0);
        for (EPerson ePerson : ePersonList) {
            String ePersonNetid = ePerson.getNetid();
            if (ePersonNetid != null) {
                int endIndex = ePersonNetid.indexOf("@");
                if (endIndex < 1) {
                    logInfo("User  " + ePerson.getID() + " has sciper in the wrong form");
                    continue;
                }
                String sciper = ePersonNetid.substring(0, endIndex);
                Optional<PersonDTO> epflPerson = epflApiClient.getPerson(sciper, EpflApiClient.Language.EN);
                if (epflPerson.isPresent()) {
                    try {
                        syncEPerson(epflPerson.get(), ePerson);
                    }  catch (IllegalStateException e) {
                        logInfo("Unable to sync profile " + epflPerson.get().getSciper() + ": " + e.getMessage());
                    }
                } else {
                    closeAffiliations(ePerson, sciper);
                    setSynchronizationMetadata(ePerson);
                }
            }
        }
    }

    private void closeAffiliations(EPerson ePerson, String sciper) throws SQLException, AuthorizeException {
        logInfo("Person with sciper: " + sciper + " is not active anymore, affiliations have been set as ended.");
        ResearcherProfile researcherProfile = researcherProfileService.findById(context, ePerson.getID());
        Group submitters = groupService.findByName(context, SUBMITTERS);
        groupService.removeMember(context, submitters, ePerson);
        updatedPersonCount++;
        if (researcherProfile == null) {
            return;
        }
        Item person = researcherProfile.getItem();
        itemService.setMetadataSingleValue(context, researcherProfile.getItem(),
                                           "epfl", "sciper",
                                           "active", null,
                                           "false");

        int affiliations =
            itemService.getMetadata(person, "oairecerif.person.affiliation", Item.ANY).size();
        if (affiliations == 0) {
            return;
        }
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

    private void executeScriptWithQuery() throws AuthorizeException, IOException, SQLException {
        List<String> queryStrings = extractQueryParameters();

        for (String query : queryStrings) {
            List<PersonDTO> epflPersonList = epflApiClient.getPersons(query, EpflApiClient.Language.EN);
            for (PersonDTO epflPerson : epflPersonList) {
                EPerson ePerson = findPerson(epflPerson);
                try {
                    if (ePerson == null) {
                        createAndSyncEPerson(epflPerson);
                    } else {
                        syncEPerson(epflPerson, ePerson);
                    }
                } catch (IllegalStateException e) {
                    logInfo("Unable to sync profile " + epflPerson.getSciper() + ": " + e.getMessage());
                }
            }
        }
    }

    private EPerson findPerson(PersonDTO epflPerson) throws SQLException {
        EPerson byNetid = ePersonService.findByNetid(context, epflPerson.getSciper() + "@epfl.ch");
        if (byNetid != null) {
            return byNetid;
        }
        return ePersonService.findByEmail(context, epflPerson.getEmail());
    }

    private void syncEPerson(PersonDTO epflPerson, EPerson ePerson) throws SQLException, AuthorizeException {
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

        if (isNeedToSyncAffiliations(ePerson, epflPerson)) {
            if (epflPerson.getAccreds() != null && epflPerson.getAccreds().length != 0) {
                needsToBEUpdated = true;
            }
        }

        if (needsToBEUpdated) {
            profileInitializer.initialize(context, ePerson);
            setSynchronizationMetadata(ePerson);
            ePersonService.update(context, ePerson);
            updatedPersonCount++;
            logInfo(
                "Person with uuid: " + ePerson.getID() + ", sciperId: " + epflPerson.getSciper() + " was updated");
        } else {
            logInfo(
                "Person with uuid: " + ePerson.getID() + ", sciperId: " + epflPerson.getSciper() +
                    " does not need to be updated");
        }
    }

    private void setSynchronizationMetadata(EPerson ePerson) throws SQLException, AuthorizeException {
        String synchronizationDate = DCDate.getCurrent().toString();
        ePersonService.setMetadataSingleValue(context, ePerson,
                                              "epfl", "synchronization",
                                              "date", null,
                                              synchronizationDate);
        ResearcherProfile researcherProfile = researcherProfileService.findById(context, ePerson.getID());
        if (researcherProfile == null) {
            String sciper = StringUtils.substringBefore(ePerson.getNetid(), "@epfl.ch");
            logInfo("Researcher profile for ePerson " + ePerson.getID() + ", sciper "
                        + sciper + " has not been created, " +
                                "the ePerson is not affiliated to OrgUnits present in the repository.");
            return;
        }
        itemService.setMetadataSingleValue(context, researcherProfile.getItem(),
                                              "epfl", "synchronization",
                                              "date", null,
                                              synchronizationDate);
    }

    private boolean isValueNeedsToBeUpdated(String ePersonValue, String epflPersonValue) {
        if (ePersonValue == null) {
            return epflPersonValue != null;
        }
        return !ePersonValue.equals(epflPersonValue);
    }

    private void createAndSyncEPerson(PersonDTO epflPerson) throws SQLException, AuthorizeException {
        if (epflPerson.getAccreds() == null || epflPerson.getAccreds().length == 0) {
            logInfo(
                "Person with sciperId " + epflPerson.getSciper() + " was not created: 0 accreds");
            return;
        }
        EPerson newEPerson = ePersonService.create(context);

        newEPerson.setNetid(epflPerson.getSciper() + "@epfl.ch");
        newEPerson.setEmail(Optional.ofNullable(epflPerson.getEmail()).orElse(epflPerson.getSciper() + "@epfl.ch"));
        newEPerson.setFirstName(context, epflPerson.getFirstname());
        newEPerson.setLastName(context, epflPerson.getName());
        newEPerson.setCanLogIn(true);

        profileInitializer.initialize(context, newEPerson);

        setSynchronizationMetadata(newEPerson);
        ePersonService.update(context, newEPerson);
        createdPersonCount++;
        logInfo(
            "Person with uuid: " + newEPerson.getID() + ", sciperId: " + newEPerson.getNetid() + " was created");

    }

    private void finalLogging() {
        if (createdPersonCount == 0 && updatedPersonCount == 0) {
            logInfo("No changes were made by the script");
        } else {
            logInfo("Changes:");
            logInfo("Number of created persons: " + createdPersonCount);
            logInfo("Number of updated persons: " + updatedPersonCount);
        }
        sendEmail();
    }

    private void logInfo(String message) {
        handler.logInfo(message);
        log = log.concat(message + "\n");
    }

    private List<String> extractQueryParameters() throws AuthorizeException, IOException {
        if (inputFile != null) {
            InputStream inputStream = handler.getFileStream(context, inputFile)
                                             .orElseThrow(() -> new IllegalArgumentException(
                                                 "Error reading file, the file couldn't be "
                                                     + "found for filename: " + inputFile));

            return new CSVParserImpl().parseCSV(inputStream, ",");
        }
        return Collections.singletonList(query);
    }

    private void assignCurrentUserInContext() throws SQLException {
        if (StringUtils.isNotBlank(this.email)) {
            EPerson eperson = ePersonService.findByEmail(context, this.email);
            context.setCurrentUser(eperson);
            return;
        }
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = ePersonService.find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }

    private void sendEmail() {
        try {
            Email email = Email.getEmail(getEmailFilename(context.getCurrentLocale(), "epfl-user-synchronization_log"));
            email.addRecipient(configurationService.getProperty("mail.admin"));
            email.setSubject("INFOSCIENCE - User synchronization process report");
            email.addArgument(log);
            email.send();
        } catch (IOException | MessagingException e) {
            handler.logInfo("An error occurs sending the email related to the user synchronization " + e);
            handler.logInfo("Mail Message content: " + log);
        }
    }

    private boolean isNeedToSyncAffiliations(EPerson ePerson, PersonDTO epflPerson) {
        Optional<ResearcherProfile> researcherProfileOptional;
        try {
            researcherProfileOptional = profileInitializer
                    .findProfile(context, ePerson)
                    .or(() -> personApiService.findProfileBySciper(
                            context, ePerson,
                            epflPerson.getSciper()));
        } catch (Exception e) {
            logInfo("Error during sync of user " + ePerson.getID() + ": " + e.getMessage());
            return false;
        }
        ResearcherProfile researcherProfile = null;

        if (epflPerson.getMainAffiliation().isEmpty() && researcherProfileOptional.isEmpty()) {
            return false;
        }
        if (researcherProfileOptional.isEmpty() ^ epflPerson.getMainAffiliation().isEmpty()) {
            return true;
        }
        if (researcherProfileOptional.isPresent()) {
            researcherProfile = researcherProfileOptional.get();
        }
        return isEPersonAndEpflPersonAccredsNotMatch(researcherProfile, epflPerson);
    }

    private boolean isEPersonAndEpflPersonAccredsNotMatch(ResearcherProfile researcherProfile,
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
        if (org.apache.commons.lang.StringUtils.isBlank(metadataValue.getAuthority())) {
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

}
