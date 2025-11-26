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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.mail.MessagingException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.authenticate.service.ProfileInitializer;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.EPersonServiceImpl;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.client.EpflApiClientImpl;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class EpflUserSynchronizationScript
    extends DSpaceRunnable<EpflUserSynchronizationScriptConfiguration<EpflUserSynchronizationScript>> {

    private String inputFile;
    private String query;
    private String log;
    private boolean allowDeactivationOnQuery;
    private int createdPersonCount = 0;
    private int updatedPersonCount = 0;

    private String email;

    private Context context;
    private EPersonServiceImpl ePersonService;
    private EpflApiClientImpl epflApiClient;
    private ProfileInitializer profileInitializer;
    private ConfigurationService configurationService;

    private DocumentBuilder documentBuilder;

    private XPath xPath;

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
        inputFile = commandLine.getOptionValue('f');
        query = commandLine.getOptionValue('q');
        email = commandLine.getOptionValue('e');
        allowDeactivationOnQuery = commandLine.hasOption("dq");

        try {
            this.documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }

        xPath = XPathFactory.newInstance().newXPath();

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
        // sort by netid as it gives a performance boost compared to a metadata sorting
        int total = ePersonService.countTotal(context);
        final int pageSize = 10;
        int numIter = total / pageSize + (total % pageSize > 0 ? 1 : 0);
        for (int idx = 0; idx < numIter; idx++) {
            List<EPerson> ePersonList = ePersonService.findAll(context, EPerson.NETID, pageSize, idx * pageSize);
            for (EPerson ePerson : ePersonList) {
                if (StringUtils.isBlank(ePerson.getNetid())) {
                    continue;
                }
                Optional<String> sciper = profileInitializer.getSciperId(ePerson);
                if (sciper.isPresent()) {
                    try {
                        Optional<PersonDTO> epflPerson = epflApiClient.getPerson(sciper.get(),
                                EpflApiClient.Language.EN);
                        if (epflPerson.isPresent()) {
                            if (profileInitializer.syncEPerson(context, epflPerson.get(), ePerson)) {
                                updatedPersonCount++;
                                logInfo(
                                        "EPerson with uuid: " + ePerson.getID() + ", sciperId: " + sciper.get()
                                                + " was updated");
                            } else {
                                logInfo(
                                        "EPerson with uuid: " + ePerson.getID() + ", sciperId: " + sciper.get() +
                                                " does not need to be updated");
                            }
                        } else {
                            if (profileInitializer.isDeactivated(context, ePerson)) {
                                logInfo(
                                        "EPerson with uuid: " + ePerson.getID() + ", netId: " + ePerson.getNetid()
                                                + " was skipped because already deactivated");
                                continue;
                            }
                            profileInitializer.closeAffiliationsAndDeactivateProfile(context, ePerson, sciper.get());
                            logInfo("Person with sciper: " + sciper
                                    + " is not active anymore, affiliations have been set as ended. " +
                                    "ORCID synchronization metadata deleted");
                            updatedPersonCount++;
                        }
                    } catch (Exception e) {
                        logError("Unable to sync profile " + sciper.get() + ": " + e.getMessage());
                    }
                } else {
                    logInfo(
                            "EPerson with uuid: " + ePerson.getID() + ", netId: " + ePerson.getNetid()
                                    + " was skipped because it is not a valid sciper");
                    continue;
                }
            }
            context.commit();
            context.clear();
        }
    }

    private void executeScriptWithQuery() throws AuthorizeException, IOException, SQLException, SAXException {
        List<String> sciperIds = extractQueryParameters();

        int count = 0;

        for (String sciperId : sciperIds) {
            try {
                Optional<PersonDTO> personDTO = epflApiClient.getPerson(sciperId, EpflApiClient.Language.EN);
                if (personDTO.isPresent()) {
                    createOrSynch(personDTO.get());
                } else if (allowDeactivationOnQuery) {
                    EPerson ePerson = ePersonService.findByNetid(context, sciperId + "@epfl.ch");
                    if (ePerson != null) {
                        profileInitializer.closeAffiliationsAndDeactivateProfile(context, ePerson, sciperId);
                        updatedPersonCount++;
                        logInfo("Person with sciper: " + sciperId
                                + " is not active anymore, affiliations have been set as ended. " +
                                "ORCID synchronization metadata deleted");
                    } else {
                        logInfo("Skipped profile #" + (count + 1) + " with sciper " + sciperId
                                + " not found in the search api nor in the database as EPerson");
                    }
                } else {
                    logInfo("Skipped profile #" + (count + 1) + " with sciper " + sciperId
                            + " not found in the search api");
                }
            } catch (Exception e) {
                logError("Unable to sync profile #" + (count + 1) + " with sciper " + sciperId +
                        ": " + e.getMessage());
            } finally {
                count++;
            }

            if (count % 20 == 0) {
                handler.logInfo("Processed " + count + " sciper ids");
                context.commit();
                context.clear();
            }

        }

        context.commit();

    }

    private void createOrSynch(PersonDTO epflPerson) {
        try {
            EPerson ePerson = profileInitializer.findPerson(context, epflPerson);
            if (ePerson == null) {
                EPerson newEPerson = profileInitializer.createAndSyncEPerson(context, epflPerson);
                if (newEPerson != null) {
                    createdPersonCount++;
                    logInfo(
                            "EPerson with uuid: " + newEPerson.getID() + ", sciperId: " + newEPerson.getNetid()
                                    + " was created");
                } else {
                    logInfo(
                            "EPerson with sciperId " + epflPerson.getSciper() + " was not created: 0 accreds");
                }
            } else {
                profileInitializer.syncEPerson(context, epflPerson, ePerson);
            }
        } catch (Exception e) {
            logError("Unable to sync profile " + epflPerson.getSciper() + ": " + e.getMessage());
        }
    }

    private void finalLogging() {
        if (createdPersonCount == 0 && updatedPersonCount == 0) {
            logInfo("No changes were made by the script");
        } else {
            logInfo("Changes:");
            logInfo("Number of created epersons: " + createdPersonCount);
            logInfo("Number of updated epersons: " + updatedPersonCount);
        }
        sendEmail();
    }

    private void logInfo(String message) {
        handler.logInfo(message);
        log = log.concat(message + "\n");
    }

    private void logError(String message) {
        handler.logError(message);
        log = log.concat(message + "\n");
    }

    private List<String> extractQueryParameters() throws AuthorizeException, IOException, SAXException {
        if (inputFile != null) {
            InputStream inputStream = handler.getFileStream(context, inputFile)
                                             .orElseThrow(() -> new IllegalArgumentException(
                                                 "Error reading file, the file couldn't be "
                                                     + "found for filename: " + inputFile));

            return parseInputStream(inputStream);
        }
        return List.of(query);
    }

    private List<String> parseInputStream(InputStream inputStream) throws IOException, SAXException {
        Document document = documentBuilder.parse(inputStream);

        NodeList nodeList = getNodeList(document, "/collection/record/datafield[@tag = '935']/subfield[@code = 'a']");

        List<String> sciperIds = new ArrayList<String>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            sciperIds.add(node.getTextContent());
        }

        handler.logInfo("Found " + sciperIds.size() + " sciper ids to be imported");

        return sciperIds;
    }

    private NodeList getNodeList(Object item, String expression) {
        try {
            return (NodeList) xPath.compile(expression).evaluate(item, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("An error occurs evaluating path " + expression, e);
        }
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
            // handler.logInfo("Mail Message content: " + log);
        }
    }



}
