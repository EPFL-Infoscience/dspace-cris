/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.profile.ResearcherProfile;
import org.dspace.app.profile.service.ResearcherProfileService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ProfileInitializerIT extends AbstractIntegrationTestWithDatabase {

    private ProfileInitializer profileInitializer = new DSpace().getSingletonService(ProfileInitializer.class);

    private ResearcherProfileService researcherProfileService = new DSpace()
        .getServiceManager().getServicesByType(ResearcherProfileService.class).get(0);

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();

    private EpflClient epflClient;

    private EpflClient mockEpflClient = mock(EpflClient.class);

    @Before
    public void setup() throws Exception {

        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();

        CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Profile Collection")
            .withEntityType("Person")
            .withTemplateItem()
            .build();

        CollectionBuilder.createCollection(context, parentCommunity)
            .withName("OrgUnit Collection")
            .withEntityType("OrgUnit")
            .withTemplateItem()
            .build();

        context.restoreAuthSystemState();

        epflClient = profileInitializer.getClient();
        profileInitializer.setClient(mockEpflClient);

    }

    @After
    public void after() {
        profileInitializer.setClient(epflClient);
    }

    @Test
    public void testProfileCreation() throws SQLException, AuthorizeException {

        context.turnOffAuthorisationSystem();

        EPerson user = EPersonBuilder.createEPerson(context)
            .withEmail("user@example.com")
            .withNameInMetadata("User", "Example")
            .withPassword(password)
            .withNetId("123456@epfl.ch ")
            .build();

        EPerson anotherUser = EPersonBuilder.createEPerson(context)
            .withEmail("another@example.com")
            .withNameInMetadata("Another User", "Example")
            .withPassword(password)
            .withNetId("99999@test.ch ")
            .build();

        context.restoreAuthSystemState();

        when(mockEpflClient.getAccred("123456")).thenReturn(createEpflResponse());
        when(mockEpflClient.getAccred("99999")).thenReturn(createEpflResponse());

        boolean initialized = profileInitializer.initialize(context, user);
        assertThat(initialized, is(true));

        ResearcherProfile profile = researcherProfileService.findById(context, user.getID());
        assertThat(profile, notNullValue());
        assertThat(profile.isVisible(), is(false));
        context.commit();

        Item profileItem = profile.getItem();
        assertThat(getMetadataValue(profileItem, "dc.title"), is("User Example"));
        assertThat(getMetadataValue(profileItem, "dspace.entity.type"), is("Person"));
        assertThat(getMetadataValue(profileItem, "cris.legacyId"), is("123456"));

        List<MetadataValue> affiliations = itemService.getMetadataByMetadataString(profileItem,
            "person.affiliation.name");

        assertThat(affiliations, hasSize(1));

        MetadataValue affiliation = affiliations.get(0);
        assertThat(affiliation.getValue(), is("UNIT 14214"));
        assertThat(affiliation.getAuthority(), notNullValue());

        Item orgUnit = itemService.find(context, UUIDUtils.fromString(affiliation.getAuthority()));
        assertThat(orgUnit, notNullValue());
        assertThat(getMetadataValue(orgUnit, "dc.title"), is("UNIT 14214"));
        assertThat(getMetadataValue(orgUnit, "dspace.entity.type"), is("OrgUnit"));
        assertThat(getMetadataValue(orgUnit, "cris.legacyId"), is("14214"));

        // verify that we have created the group and added it to the current context special group
        Group group = groupService.findByName(context, "UNIT 14214");
        assertThat(group, notNullValue());
        assertThat(context.getSpecialGroups().contains(group), is(true));

        verify(mockEpflClient).getAccred("123456");
        verifyNoMoreInteractions(mockEpflClient);

        initialized = profileInitializer.initialize(context, user);
        assertThat(initialized, is(false));
        verify(mockEpflClient, times(2)).getAccred("123456");
        verifyNoMoreInteractions(mockEpflClient);

        // update the main affiliation to check if the profile initializer will fix it back
        context.turnOffAuthorisationSystem();
        profileItem = context.reloadEntity(profileItem);
        itemService.clearMetadata(context, profileItem, "person", "affiliation", "name", Item.ANY);
        itemService.update(context, profileItem);
        context.commit();
        context.restoreAuthSystemState();
        initialized = profileInitializer.initialize(context, user);
        context.commit();
        verify(mockEpflClient, times(3)).getAccred("123456");
        verifyNoMoreInteractions(mockEpflClient);
        assertThat(initialized, is(true));
        profileItem = context.reloadEntity(profileItem);
        affiliations = itemService.getMetadataByMetadataString(profileItem,
                "person.affiliation.name");
        assertThat(affiliations, hasSize(1));
        affiliation = affiliations.get(0);
        assertThat(affiliation.getValue(), is("UNIT 14214"));
        assertThat(affiliation.getAuthority(), notNullValue());

        // test with the other profile
        initialized = profileInitializer.initialize(context, anotherUser);
        assertThat(initialized, is(true));

        context.commit();

        profile = researcherProfileService.findById(context, anotherUser.getID());
        assertThat(profile, notNullValue());
        assertThat(profile.isVisible(), is(false));

        profileItem = profile.getItem();
        assertThat(getMetadataValue(profileItem, "dc.title"), is("Another User Example"));
        assertThat(getMetadataValue(profileItem, "dspace.entity.type"), is("Person"));
        assertThat(getMetadataValue(profileItem, "cris.legacyId"), is("99999"));

        affiliations = itemService.getMetadataByMetadataString(profileItem, "person.affiliation.name");

        assertThat(affiliations, hasSize(1));
        assertThat(affiliations.get(0).getValue(), is("UNIT 14214"));
        assertThat(affiliations.get(0).getAuthority(), is(orgUnit.getID().toString()));

        verify(mockEpflClient).getAccred("99999");
        verifyNoMoreInteractions(mockEpflClient);


    }

    private String getMetadataValue(Item item, String metadataField) {
        return itemService.getMetadataFirstValue(item, new MetadataFieldName(metadataField), Item.ANY);
    }

    private EpflResponse createEpflResponse() {
        String json = "{\n" +
            "  \"result\": [\n" +
            "    {\n" +
            "      \"author\": 248177,\n" +
            "      \"classid\": 4,\n" +
            "      \"comment\": \"\",\n" +
            "      \"creator\": \"000000\",\n" +
            "      \"datecreat\": \"2022-06-09 00:40:18\",\n" +
            "      \"datedeb\": \"2022-06-09 00:40:18\",\n" +
            "      \"datefin\": \"\",\n" +
            "      \"datereval\": \"\",\n" +
            "      \"debval\": \"2022-07-07 10:50:12\",\n" +
            "      \"duree\": \"\",\n" +
            "      \"finval\": \"\",\n" +
            "      \"ordre\": 1,\n" +
            "      \"origine\": \"p\",\n" +
            "      \"persid\": 360892,\n" +
            "      \"posid\": 1070,\n" +
            "      \"revalman\": \"n\",\n" +
            "      \"statusid\": 1,\n" +
            "      \"unitid\": 14214\n" +
            "    }\n" +
            "  ]\n" +
            "}";
        try {
            return new ObjectMapper().readValue(json, EpflResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
