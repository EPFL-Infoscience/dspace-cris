/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.matcher.VocabularyMatcher.matchVocabularyEntry;
import static org.dspace.content.authority.ThematicCuratorEPersonAuthority.THEMATIC_AREA_GROUP_NAME;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.GroupBuilder;
import org.dspace.eperson.EPerson;
import org.hamcrest.Matchers;
import org.junit.Test;

/**
 * This class handles ItemAuthority related IT.
 *
 * @author Aliaksei Bykau
 */
public class ThematicCuratorEPersonAuthorityIT extends AbstractControllerIntegrationTest {

    @Test
    public void checkSearchTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        context.turnOffAuthorisationSystem();
        EPerson firstEPerson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Luca", "Giamminonni")
            .withEmail("giamminonni@test.com")
            .withPassword(password)
            .withCanLogin(true)
            .build();

        EPerson secondEPerson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Lucky", "Bollini")
            .withEmail("bollini@test.com")
            .withPassword(password)
            .withCanLogin(true)
            .build();

        EPerson thirdEPerson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Luca", "Bykau")
            .withEmail("bykau@test.com")
            .withPassword(password)
            .withCanLogin(true)
            .build();

        GroupBuilder.createGroup(context)
            .withName(THEMATIC_AREA_GROUP_NAME)
            .addMember(firstEPerson)
            .addMember(secondEPerson)
            .build();
        context.restoreAuthSystemState();

        // admin
        String tokenAdmin = getAuthToken(admin.getEmail(), password);
        getClient(tokenAdmin).perform(get("/api/submission/vocabularies/ThematicCuratorEPersonAuthority/entries")
                            .param("filter", "Luc"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$._embedded.entries", containsInAnyOrder(
                                matchVocabularyEntry("Luca Giamminonni", "Luca Giamminonni", "vocabularyEntry",
                                    firstEPerson.getID().toString()),
                                matchVocabularyEntry("Lucky Bollini", "Lucky Bollini", "vocabularyEntry",
                                    secondEPerson.getID().toString()))))
                            .andExpect(jsonPath("$.page.totalElements", Matchers.is(2)));

        getClient(tokenAdmin).perform(get("/api/submission/vocabularies/ThematicCuratorEPersonAuthority/entries")
                            .param("filter", "Lucky"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$._embedded.entries", containsInAnyOrder(
                                matchVocabularyEntry("Lucky Bollini", "Lucky Bollini", "vocabularyEntry",
                                    secondEPerson.getID().toString()))))
                            .andExpect(jsonPath("$.page.totalElements", Matchers.is(1)));
        // normal user
        String tokenThirdEPerson = getAuthToken(thirdEPerson.getEmail(), password);
        getClient(tokenThirdEPerson).perform(get("/api/submission/vocabularies/ThematicCuratorEPersonAuthority/entries")
                                    .param("filter", "Luc"))
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$._embedded.entries", containsInAnyOrder(
                                        matchVocabularyEntry("Luca Giamminonni", "Luca Giamminonni", "vocabularyEntry",
                                            firstEPerson.getID().toString()),
                                        matchVocabularyEntry("Lucky Bollini", "Lucky Bollini", "vocabularyEntry",
                                            secondEPerson.getID().toString()))))
                                    .andExpect(jsonPath("$.page.totalElements", Matchers.is(2)));

        getClient(tokenThirdEPerson).perform(get("/api/submission/vocabularies/ThematicCuratorEPersonAuthority/entries")
                                    .param("filter", "Lucky"))
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$._embedded.entries", containsInAnyOrder(
                                        matchVocabularyEntry("Lucky Bollini", "Lucky Bollini", "vocabularyEntry",
                                            secondEPerson.getID().toString()))))
                                    .andExpect(jsonPath("$.page.totalElements", Matchers.is(1)));
    }

    @Test
    public void checkSearchUnauthorizedTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        context.turnOffAuthorisationSystem();
        EPerson firstEPerson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Luca", "Giamminonni")
            .withEmail("giamminonni@test.com")
            .withPassword(password)
            .withCanLogin(true)
            .build();

        EPerson secondEPerson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Lucky", "Bollini")
            .withEmail("bollini@test.com")
            .withPassword(password)
            .withCanLogin(true)
            .build();

        EPerson thirdEPerson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Luca", "Bykau")
            .withEmail("bykau@test.com")
            .withPassword(password)
            .withCanLogin(true)
            .build();

        GroupBuilder.createGroup(context)
            .withName(THEMATIC_AREA_GROUP_NAME)
            .addMember(firstEPerson)
            .addMember(secondEPerson)
            .build();
        context.restoreAuthSystemState();

        getClient().perform(get("/api/submission/vocabularies/ThematicCuratorEPersonAuthority/entries")
                   .param("filter", "Luc"))
                   .andExpect(status().isUnauthorized());
    }

}