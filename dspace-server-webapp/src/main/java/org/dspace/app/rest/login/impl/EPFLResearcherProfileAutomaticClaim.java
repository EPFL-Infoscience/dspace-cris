/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.login.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.dspace.core.I18nUtil.getEmailFilename;

import java.io.IOException;
import java.util.Optional;
import javax.mail.MessagingException;

import org.dspace.app.rest.login.PostLoggedInAction;
import org.dspace.authenticate.service.NoPersonFoundException;
import org.dspace.authenticate.service.ProfileInitializer;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.eperson.EPerson;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.client.EpflApiClientImpl;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.profile.service.ResearcherProfileService;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link PostLoggedInAction} to add support to retrieve the sciper id from the eperson data (via the
 * netid, see {@link ProfileInitializer#getSciperId(EPerson)}) and use it to sync/create a corresponding profile if
 * appropriate
 */
public class EPFLResearcherProfileAutomaticClaim implements PostLoggedInAction {

    private final static Logger LOGGER = LoggerFactory.getLogger(EPFLResearcherProfileAutomaticClaim.class);

    protected ProfileInitializer profileInitializer = new DSpace().getSingletonService(ProfileInitializer.class);

    @Autowired
    private EpflApiClientImpl epflApiClient;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ResearcherProfileService researcherProfileService;

    @Override
    public void loggedIn(Context context) {
        if (isBlank(researcherProfileService.getProfileType())) {
            return;
        }

        EPerson currentUser = context.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        Optional<String> sciper = profileInitializer.getSciperId(currentUser);
        if (sciper.isPresent()) {
            try {
                context.turnOffAuthorisationSystem();
                Optional<PersonDTO> epflPerson = epflApiClient.getPerson(sciper.get(), EpflApiClient.Language.EN);
                if (epflPerson.isPresent()) {
                    if (profileInitializer.syncEPerson(context, epflPerson.get(), currentUser)) {
                        LOGGER.info(
                                "EPerson with uuid: " + currentUser.getID() + ", sciperId: " + sciper.get()
                                        + " was updated");
                    } else {
                        LOGGER.info(
                            "EPerson with uuid: " + currentUser.getID() + ", sciperId: " + sciper.get() +
                                " does not need to be updated");
                    }
                } else {
                    profileInitializer.closeAffiliationsAndDeactivateProfile(context, currentUser, sciper.get());
                    LOGGER.info("Person with sciper: " + sciper
                            + " is not active anymore, affiliations have been set as ended.");
                }
            } catch (NoPersonFoundException ex) {
                sendEmailForNoPersonFound(context, currentUser);
            } catch (Exception e) {
                LOGGER.error("An error occurs during the user/profile sync by sciper " + sciper.get(), e);
            } finally {
                context.restoreAuthSystemState();
            }
        }
    }

    private void sendEmailForNoPersonFound(Context context, EPerson person) {
        try {
            Email email = Email.getEmail(getEmailFilename(context.getCurrentLocale(),
                    "no_person_found_by_sciper"));
            email.addRecipient(configurationService.getProperty("mail.admin"));
            email.addArgument(profileInitializer.getSciperId(person));
            email.send();
        } catch (IOException | MessagingException e) {
            LOGGER.error("An error occurs sending the email related to the user synchronization", e);
        }
    }

}
