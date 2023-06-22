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
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.mail.MessagingException;

import org.apache.commons.cli.ParseException;
import org.dspace.authenticate.service.ProfileInitializer;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.EPersonServiceImpl;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.client.EpflApiClientImpl;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.epfl.script.parser.CSVParserImpl;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;


public class EpflUserSynchronizationScript
    extends DSpaceRunnable<EpflUserSynchronizationScriptConfiguration<EpflUserSynchronizationScript>> {

    private String query;
    private String log;
    private int createdPersonCount = 0;
    private int updatedPersonCount = 0;

    private Context context;
    private EPersonServiceImpl ePersonService;
    private EpflApiClientImpl epflApiClient;
    private ProfileInitializer profileInitializer;
    private ConfigurationService configurationService;


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

        query = commandLine.getOptionValue('f');

        log = "";
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        if (query == null) {
            executeScriptWithOutQuery();
        } else {
            executeScriptWithQuery();
        }

        finalLogging();
    }

    private void executeScriptWithOutQuery() throws SQLException, AuthorizeException {
        List<EPerson> ePersonList = ePersonService.findAll(context, 0);
        for (EPerson ePerson : ePersonList) {
            String ePersonNetid = ePerson.getNetid();
            if (ePersonNetid != null) {
                int endIndex = ePersonNetid.indexOf("@");
                String sciper = ePersonNetid.substring(0, endIndex);
                Optional<PersonDTO> epflPerson = epflApiClient.getPerson(sciper, EpflApiClient.Language.EN);
                if (epflPerson.isPresent()) {
                    syncEPerson(epflPerson.get(), ePerson);
                    updatedPersonCount++;
                }
            }
        }
    }

    private void executeScriptWithQuery() throws AuthorizeException, IOException, SQLException {
        List<String> sciperList = parseCSVFromQuery();

        for (String sciper : sciperList) {
            List<PersonDTO> epflPersonList = epflApiClient.getPersons(sciper, EpflApiClient.Language.EN);
            for (PersonDTO epflPerson : epflPersonList) {
                EPerson ePerson = ePersonService.findByNetid(context, epflPerson.getSciper() + "@epfl.ch");
                if (ePerson == null) {
                    createAndSyncEPerson(epflPerson);
                    createdPersonCount++;
                } else {
                    syncEPerson(epflPerson, ePerson);
                    updatedPersonCount++;
                }
            }
        }
    }

    private void syncEPerson(PersonDTO epflPerson, EPerson ePerson) throws SQLException, AuthorizeException {
        ePerson.setEmail(epflPerson.getEmail());
        ePerson.setFirstName(context, epflPerson.getFirstname());
        ePerson.setLastName(context, epflPerson.getName());

        ePersonService.setMetadataSingleValue(context, ePerson,
                                              "epfl", "synchronization",
                                              "date", null,
                                              new Timestamp(new Date().getTime()).toString());
        ePersonService.update(context, ePerson);
        if (epflPerson.getAccreds() != null && epflPerson.getAccreds().length != 0) {
            profileInitializer.initialize(context, ePerson);
        }

        logInfo(
            "Person with uuid: " + ePerson.getID() + ", sciperId: " + ePerson.getNetid() + " being verified");
    }

    private void createAndSyncEPerson(PersonDTO epflPerson) throws SQLException, AuthorizeException {
        EPerson newEPerson = ePersonService.create(context);
        newEPerson.setNetid(epflPerson.getSciper() + "@epfl.ch");
        syncEPerson(epflPerson, newEPerson);
    }

    private void finalLogging() {
        if (createdPersonCount == 0 && updatedPersonCount == 0) {
            logInfo("There are no changes to import");
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

    private List<String> parseCSVFromQuery() throws AuthorizeException, IOException {
        InputStream inputStream = handler.getFileStream(context, query)
                                         .orElseThrow(() -> new IllegalArgumentException(
                                             "Error reading file, the file couldn't be "
                                                 + "found for filename: " + query));

        return new CSVParserImpl().parseCSV(inputStream, ",");
    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
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
            email.addArgument(log);
            email.send();
        } catch (IOException | MessagingException e) {
            handler.logInfo("An error occurs sending the email related to the user synchronization " + e);
        }
    }

}
