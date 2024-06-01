/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.matcher.VocabularyMatcher.matchVocabularyEntry;
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

    private static final String THEMATIC_AREA_GROUP_NAME = "ThematicAreaCurators";

    @Test
    public void checkSearch() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();
        context.turnOffAuthorisationSystem();
        EPerson firstEPersonId = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("Luca", "Giamminonni")
                .build();
        EPerson secondEPersonId = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("Lucky", "Bollini")
                .build();
        EPersonBuilder.createEPerson(context)
                .withNameInMetadata("Luca", "Bykau")
                .build();

        GroupBuilder.createGroup(context)
                .withName(THEMATIC_AREA_GROUP_NAME)
                .addMember(firstEPersonId)
                .addMember(secondEPersonId)
                .build();
        context.restoreAuthSystemState();

        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(get("/api/submission/vocabularies/ThematicCuratorEPersonAuthority/entries")
                        .param("filter", "Luc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.entries", containsInAnyOrder(
                        matchVocabularyEntry("Luca Giamminonni", "Luca Giamminonni", "vocabularyEntry",
                                firstEPersonId.getID().toString()),
                        matchVocabularyEntry("Lucky Bollini", "Lucky Bollini", "vocabularyEntry",
                                secondEPersonId.getID().toString()))))
                .andExpect(jsonPath("$.page.totalElements", Matchers.is(2)));

        getClient(token).perform(get("/api/submission/vocabularies/ThematicCuratorEPersonAuthority/entries")
                        .param("filter", "Lucky"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.entries", containsInAnyOrder(
                        matchVocabularyEntry("Lucky Bollini", "Lucky Bollini", "vocabularyEntry",
                                secondEPersonId.getID().toString()))))
                .andExpect(jsonPath("$.page.totalElements", Matchers.is(1)));
    }
}
