/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate.service;

import static org.dspace.app.matcher.MetadataValueMatcher.with;
import static org.dspace.core.Constants.READ;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;
import static org.dspace.eperson.Group.ANONYMOUS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;

import java.sql.SQLException;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.eperson.EPerson;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.service.impl.OrgUnitApiServiceImpl;
import org.dspace.epfl.service.impl.PersonApiServiceImpl;
import org.dspace.profile.ResearcherProfile;
import org.dspace.profile.service.ResearcherProfileService;
import org.dspace.utils.DSpace;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ProfileInitializerIT extends AbstractIntegrationTestWithDatabase {

    private ProfileInitializer profileInitializer = new DSpace().getSingletonService(ProfileInitializer.class);

    private ResearcherProfileService researcherProfileService = new DSpace()
        .getSingletonService(ResearcherProfileService.class);

    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    private PersonApiServiceImpl personApiService = new DSpace().getServiceManager()
        .getServicesByType(PersonApiServiceImpl.class).get(0);

    private OrgUnitApiServiceImpl orgUnitApiService = new DSpace().getServiceManager()
        .getServicesByType(OrgUnitApiServiceImpl.class).get(0);

    private EpflApiClient apiClient = new DSpace().getServiceManager()
        .getServicesByType(EpflApiClient.class).get(0);

    private ResourcePolicyService resourcePolicyService = new DSpace().getServiceManager()
        .getServicesByType(ResourcePolicyService.class).get(0);

//    private EpflApiClient mockApiClient;

    private Collection profiles;

    @Before
    public void setup() throws Exception {

        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();

        profiles = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Profile Collection")
            .withEntityType("Person")
            .build();

        context.restoreAuthSystemState();

//        mockApiClient = mock(EpflApiClient.class);

//        personApiService.setApiClient(mockApiClient);
//        orgUnitApiService.setApiClient(mockApiClient);

    }

    @After
    public void after() throws Exception {

        personApiService.setApiClient(apiClient);
        orgUnitApiService.setApiClient(apiClient);

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testProfileCreation() throws SQLException, AuthorizeException {

        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Test", "User")
            .withEmail("test@user.it")
            .withNetId("352234@epfl.ch")
            .build();

        context.restoreAuthSystemState();

        profileInitializer.initialize(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        assertThat(profile.getMetadata(), hasItems(
            with("dc.title", "Al Hassanieh, Haitham"),
            with("person.givenName", "Haitham"),
            with("person.familyName", "Al Hassanieh"),
            with("person.email", "haitham.alhassanieh@epfl.ch"),
            with("person.affiliation.name", "Laboratory of Sensing and Networking Systems",
                "will be generated::ACRONYM::SENS", 400),
            with("epfl.sciper.active", "true"),
            with("epfl.sciperId", "352234"),
            with("oairecerif.identifier.url", "https://people.epfl.ch/haitham.alhassanieh"),
            with("oairecerif.affiliation.role", "Associate Professor"),
            with("oairecerif.person.affiliation", "Laboratory of Sensing and Networking Systems",
                "will be generated::ACRONYM::SENS", 400),
            with("oairecerif.affiliation.startDate", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.affiliation.role", "Associate Professor", 1),
            with("oairecerif.person.affiliation", "SSC - Teaching", "will be generated::ACRONYM::SSC-ENS", 1, 400),
            with("oairecerif.affiliation.startDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.role", "Associate Professor", 2),
            with("oairecerif.person.affiliation", "SIN - Teaching", "will be generated::ACRONYM::SIN-ENS", 2, 400),
            with("oairecerif.affiliation.startDate", PLACEHOLDER_PARENT_METADATA_VALUE, 2),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 2)));

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));

    }

    private void assertVisible(ResearcherProfile researcherProfile) throws SQLException {
        List<ResourcePolicy> resourcePolicies = resourcePolicyService.find(context, researcherProfile.getItem());
        boolean visible = resourcePolicies
            .stream()
            .filter(policy -> policy.getGroup() != null)
            .anyMatch(policy -> READ == policy.getAction() &&
                ANONYMOUS.equals(policy.getGroup().getName()));

        assertThat(visible, is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInitializeWithPersonWithThatSciperAlreadyExisting() throws SQLException, AuthorizeException {

        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Test", "User")
            .withEmail("test@user.it")
            .withNetId("352234@epfl.ch")
            .build();

        Item person = ItemBuilder.createItem(context, profiles)
            .withTitle("My User")
            .withBirthDate("1992-06-26")
            .withMetadata("epfl", "sciperId", null, "352234")
            .build();

        context.restoreAuthSystemState();

        profileInitializer.initialize(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        assertThat(profile, is(person));

        person = context.reloadEntity(person);

        assertThat(person.getMetadata(), hasItems(
            with("dc.title", "Al Hassanieh, Haitham"),
            with("person.givenName", "Haitham"),
            with("person.familyName", "Al Hassanieh"),
            with("person.email", "haitham.alhassanieh@epfl.ch"),
            with("person.birthDate", "1992-06-26"),
            with("person.affiliation.name", "Laboratory of Sensing and Networking Systems",
                "will be generated::ACRONYM::SENS", 400),
            with("epfl.sciper.active", "true"),
            with("epfl.sciperId", "352234"),
            with("oairecerif.identifier.url", "https://people.epfl.ch/haitham.alhassanieh"),
            with("oairecerif.affiliation.role", "Associate Professor"),
            with("oairecerif.person.affiliation", "Laboratory of Sensing and Networking Systems",
                "will be generated::ACRONYM::SENS", 400),
            with("oairecerif.affiliation.startDate", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.affiliation.role", "Associate Professor", 1),
            with("oairecerif.person.affiliation", "SSC - Teaching", "will be generated::ACRONYM::SSC-ENS", 1, 400),
            with("oairecerif.affiliation.startDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.role", "Associate Professor", 2),
            with("oairecerif.person.affiliation", "SIN - Teaching", "will be generated::ACRONYM::SIN-ENS", 2, 400),
            with("oairecerif.affiliation.startDate", PLACEHOLDER_PARENT_METADATA_VALUE, 2),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 2)));

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));

    }

    @Test
    public void testInitializeWithResearcherProfile() throws SQLException, AuthorizeException {

        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Test", "User")
            .withEmail("test@user.it")
            .withNetId("352234@epfl.ch")
            .build();

        Item person = ItemBuilder.createItem(context, profiles)
            .withTitle("My User")
            .withBirthDate("1992-06-26")
            .withDspaceObjectOwner(eperson)
            .build();

        context.restoreAuthSystemState();

        profileInitializer.initialize(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        assertThat(profile, is(person));

        person = context.reloadEntity(person);
        assertThat(person.getMetadata(), hasSize(28));

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));

    }

    @Test
    public void testInitializeWithResearcherProfileAssignedToAnotherEPerson() throws SQLException, AuthorizeException {

        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Test", "User")
            .withEmail("test@user.it")
            .withNetId("352234@epfl.ch")
            .build();

        ItemBuilder.createItem(context, profiles)
            .withTitle("My User")
            .withMetadata("epfl", "sciperId", null, "352234")
            .withDspaceObjectOwner(admin)
            .build();

        context.restoreAuthSystemState();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> profileInitializer.initialize(context, eperson));

        assertThat(exception.getMessage(), is("An item with the sciper 352234 is already linked "
            + "to another eperson: " + admin.getID()));

    }

    @Test
    public void testInitializeWithUpdate() throws SQLException, AuthorizeException {

        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Test", "User")
            .withEmail("test@user.it")
            .withNetId("352234@epfl.ch")
            .build();

        context.restoreAuthSystemState();

        profileInitializer.initialize(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        assertThat(profile.getMetadata(), hasSize(26));

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));

        profileInitializer.initialize(context, eperson);

        researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());

        Item updatedProfile = researcherProfile.getItem();
        assertThat(updatedProfile, is(profile));

        assertThat(updatedProfile.getMetadata(), hasSize(26));

        Bitstream newPicture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(newPicture, notNullValue());
        assertThat(newPicture, is(not(picture)));
        assertThat(newPicture.getMetadata(), hasItem(with("dc.type", "personal picture")));

        assertThat(bitstreamService.getBitstreamByBundleName(updatedProfile, "ORIGINAL"), hasSize(1));

    }

}
