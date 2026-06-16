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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.GroupBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.service.impl.OrgUnitApiServiceImpl;
import org.dspace.epfl.service.impl.PersonApiServiceImpl;
import org.dspace.profile.ResearcherProfile;
import org.dspace.profile.service.ResearcherProfileService;
import org.dspace.utils.DSpace;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Temporarily ignored at class level")
public class ProfileInitializerIT extends AbstractIntegrationTestWithDatabase {

    private ProfileInitializer profileInitializer = new DSpace().getSingletonService(ProfileInitializer.class);

    private ResearcherProfileService researcherProfileService = new DSpace()
        .getSingletonService(ResearcherProfileService.class);

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    private PersonApiServiceImpl personApiService = new DSpace().getServiceManager()
        .getServicesByType(PersonApiServiceImpl.class).get(0);

    private OrgUnitApiServiceImpl orgUnitApiService = new DSpace().getServiceManager()
        .getServicesByType(OrgUnitApiServiceImpl.class).get(0);

    private EpflApiClient apiClient = new DSpace().getServiceManager()
        .getServicesByType(EpflApiClient.class).get(0);

    private ResourcePolicyService resourcePolicyService = new DSpace().getServiceManager()
        .getServicesByType(ResourcePolicyService.class).get(0);

    private GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private EPersonService epersonService = EPersonServiceFactory.getInstance().getEPersonService();
//    private EpflApiClient mockApiClient;

    private Collection profiles;

    private Collection orgUnits;
    private Group submitters;

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

        orgUnits = CollectionBuilder.createCollection(context, parentCommunity)
                                    .withName("OrgUnit Collection")
                                    .withEntityType("OrgUnit")
                                    .build();

        submitters = GroupBuilder.createGroup(context).withName("Submitter").build();

        List<EPerson> ePersonList = epersonService.findAll(context, EPerson.NETID);
        // cleanup any epfl eperson left by previous test
        for (EPerson ePerson : ePersonList) {
            if (StringUtils.isNotBlank(ePerson.getNetid())) {
                epersonService.delete(context, ePerson);
            }
        }
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
    public void testProfileCreation() throws SQLException, AuthorizeException, IOException {

        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Test", "User")
            .withEmail("test@user.it")
            .withNetId("352234@epfl.ch")
            .build();

        Item sens = ItemBuilder.createItem(context, orgUnits)
                               .withTitle("Laboratory of Sensing and Networking Systems")
                               .withAcronym("SENS").build();

        Item teaching = ItemBuilder.createItem(context, orgUnits)
                                   .withTitle("SSC - Teaching")
                                   .withAcronym("SSC-ENS").build();

        Item sinTeaching = ItemBuilder.createItem(context, orgUnits)
                                      .withTitle("SIN - Teaching")
                                      .withAcronym("SIN-ENS").build();

        context.restoreAuthSystemState();

        profileInitializer.createOrUpdateProfile(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        String yesterday = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now().minusDays(1L));
        assertThat(profile.getMetadata(), hasItems(
            with("dc.title", "Al Hassanieh, Haitham"),
            with("person.givenName", "Haitham"),
            with("person.familyName", "Al Hassanieh"),
            with("person.email", "haitham.alhassanieh@epfl.ch"),
            with("epfl.sciper.active", "true"),
            with("epfl.sciperId", "352234"),
            with("oairecerif.identifier.url", "https://people.epfl.ch/haitham.alhassanieh"),
            with("oairecerif.affiliation.role", "Associate Professor"),
            with("oairecerif.person.affiliation", "SENS", "will be referenced::ACRONYM::SENS", 400),
            with("oairecerif.affiliation.startDate", yesterday),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.affiliation.role", "Associate Professor", 1),
            with("oairecerif.person.affiliation", "SSC-ENS", "will be referenced::ACRONYM::SSC-ENS", 1, 400),
            with("oairecerif.affiliation.startDate", yesterday, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.role", "Associate Professor", 2),
            with("oairecerif.person.affiliation", "SIN-ENS", "will be referenced::ACRONYM::SIN-ENS", 2, 400),
            with("oairecerif.affiliation.startDate", yesterday, 2),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 2)));

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));
        assertThat(groupService.allMemberGroupsSet(context, eperson)
                       .stream().anyMatch(g -> g.getName().equals(submitters.getName())), is(true));
        // delete the profile created by the profileInitializer to avoid to mess the test data
        context.turnOffAuthorisationSystem();
        itemService.delete(context, profile);
        context.restoreAuthSystemState();

    }
    @Test
    public void testProfileCreationWithNoDSpaceOrgunits() throws SQLException, AuthorizeException {

        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Test", "User")
            .withEmail("test@user.it")
            .withNetId("352234@epfl.ch")
            .build();

        context.restoreAuthSystemState();

        profileInitializer.createOrUpdateProfile(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, nullValue());
        assertThat(groupService.allMemberGroupsSet(context, eperson)
                               .stream().anyMatch(g -> g.getName().equals(submitters.getName())), is(false));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testProfileCreationWithOnlyOneExistingUnit() throws SQLException, AuthorizeException {

        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
                                        .withNameInMetadata("Test", "User")
                                        .withEmail("test@user.it")
                                        .withNetId("352234@epfl.ch")
                                        .build();

        ItemBuilder.createItem(context, orgUnits)
                   .withTitle("Laboratory of Sensing and Networking Systems")
                   .withAcronym("SENS").build();

        context.restoreAuthSystemState();

        profileInitializer.createOrUpdateProfile(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        String yesterday = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now().minusDays(1L));
        assertThat(profile.getMetadata(), hasItems(
            with("dc.title", "Al Hassanieh, Haitham"),
            with("person.givenName", "Haitham"),
            with("person.familyName", "Al Hassanieh"),
            with("person.email", "haitham.alhassanieh@epfl.ch"),
            with("epfl.sciper.active", "true"),
            with("epfl.sciperId", "352234"),
            with("oairecerif.identifier.url", "https://people.epfl.ch/haitham.alhassanieh"),
            with("oairecerif.affiliation.role", "Associate Professor"),
            with("oairecerif.person.affiliation", "SENS", "will be referenced::ACRONYM::SENS", 400),
            with("oairecerif.affiliation.startDate", yesterday),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE)));

        assertThat(profile.getMetadata(), not(hasItems(
            with("oairecerif.affiliation.role", "Associate Professor", 1),
            with("oairecerif.person.affiliation", "SSC-ENS", "will be referenced::ACRONYM::SSC-ENS", 1, 400),
            with("oairecerif.affiliation.startDate", yesterday, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.role", "Associate Professor", 2),
            with("oairecerif.person.affiliation", "SIN-ENS", "will be referenced::ACRONYM::SIN-ENS", 2, 400),
            with("oairecerif.affiliation.startDate", yesterday, 2),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 2)
        )));

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));
        assertThat(groupService.allMemberGroupsSet(context, eperson)
                               .stream().anyMatch(g -> g.getName().equals(submitters.getName())), is(true));
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

        ItemBuilder.createItem(context, orgUnits)
                   .withTitle("Laboratory of Sensing and Networking Systems")
                   .withAcronym("SENS").build();

        ItemBuilder.createItem(context, orgUnits)
                   .withTitle("SSC - Teaching")
                   .withAcronym("SSC-ENS").build();

        ItemBuilder.createItem(context, orgUnits)
                   .withTitle("SIN - Teaching")
                   .withAcronym("SIN-ENS").build();

        context.restoreAuthSystemState();

        profileInitializer.createOrUpdateProfile(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        assertThat(profile, is(person));

        person = context.reloadEntity(person);

        String yesterday = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now().minusDays(1L));
        assertThat(person.getMetadata(), hasItems(
            with("dc.title", "Al Hassanieh, Haitham"),
            with("person.givenName", "Haitham"),
            with("person.familyName", "Al Hassanieh"),
            with("person.email", "haitham.alhassanieh@epfl.ch"),
            with("person.birthDate", "1992-06-26"),
            with("epfl.sciper.active", "true"),
            with("epfl.sciperId", "352234"),
            with("oairecerif.identifier.url", "https://people.epfl.ch/haitham.alhassanieh"),
            with("oairecerif.affiliation.role", "Associate Professor"),
            with("oairecerif.person.affiliation", "SENS", "will be referenced::ACRONYM::SENS", 400),
            with("oairecerif.affiliation.startDate", yesterday),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.affiliation.role", "Associate Professor", 1),
            with("oairecerif.person.affiliation", "SSC-ENS", "will be referenced::ACRONYM::SSC-ENS", 1, 400),
            with("oairecerif.affiliation.startDate", yesterday, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.role", "Associate Professor", 2),
            with("oairecerif.person.affiliation", "SIN-ENS", "will be referenced::ACRONYM::SIN-ENS", 2, 400),
            with("oairecerif.affiliation.startDate", yesterday, 2),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 2)));

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));

    }

    @Test
    public void testUpdateProfileWithoutDuplicatingAffiliations() throws SQLException, AuthorizeException {

        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Test", "User")
            .withEmail("test@user.it")
            .withNetId("352234@epfl.ch")
            .build();

        Item sens = ItemBuilder.createItem(context, orgUnits)
                               .withTitle("Laboratory of Sensing and Networking Systems")
                               .withAcronym("SENS").build();

        Item ssc = ItemBuilder.createItem(context, orgUnits)
                               .withTitle("SSC - Teaching")
                               .withAcronym("SSC-ENS").build();

        Item sin = ItemBuilder.createItem(context, orgUnits)
                               .withTitle("SIN - Teaching")
                               .withAcronym("SIN-ENS").build();

        Item person = ItemBuilder.createItem(context, profiles)
            .withTitle("My User")
            .withBirthDate("1992-06-26")
            .withMetadata("epfl", "sciperId", null, "352234")
            .withPersonAffiliation("SENS", sens.getID().toString())
            .withPersonAffiliationStartDate(PLACEHOLDER_PARENT_METADATA_VALUE)
            .withPersonAffiliationEndDate(PLACEHOLDER_PARENT_METADATA_VALUE)
            .withPersonAffiliation("SSC-ENS", ssc.getID().toString())
            .withPersonAffiliationStartDate("2022-01-01")
            .withPersonAffiliationEndDate(PLACEHOLDER_PARENT_METADATA_VALUE)
            .withPersonAffiliation("SIN-ENS", sin.getID().toString())
            .withPersonAffiliationStartDate("2021-01-01")
            .withPersonAffiliationEndDate(PLACEHOLDER_PARENT_METADATA_VALUE)

            .build();



        context.restoreAuthSystemState();

        profileInitializer.createOrUpdateProfile(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        assertThat(profile, is(person));

        person = context.reloadEntity(person);

        String yesterday = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now().minusDays(1L));
        assertThat(person.getMetadata(), hasItems(
            with("oairecerif.person.affiliation", "SENS",
                sens.getID().toString(), 600),
            with("oairecerif.affiliation.startDate", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.person.affiliation", "SSC-ENS",
                 ssc.getID().toString(), 1, 600),
            with("oairecerif.affiliation.startDate", "2022-01-01", 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.person.affiliation", "SIN-ENS",
                 sin.getID().toString(), 2, 600),
            with("oairecerif.affiliation.startDate", "2021-01-01", 2),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 2)));

        assertTrue(person.getMetadata().stream().filter(mv -> mv.getMetadataField().toString('.')
                                                                .equals("oairecerif.person.affiliation"))
            .filter(mv -> mv.getValue().equals("SENS"))
            .noneMatch(mv -> mv.getPlace() > 0));


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

        Item sens = ItemBuilder.createItem(context, orgUnits)
                               .withTitle("Laboratory of Sensing and Networking Systems")
                               .withAcronym("SENS").build();

        Item teaching = ItemBuilder.createItem(context, orgUnits)
                                   .withTitle("SSC - Teaching")
                                   .withAcronym("SSC-ENS").build();

        Item sinTeaching = ItemBuilder.createItem(context, orgUnits)
                                      .withTitle("SIN - Teaching")
                                      .withAcronym("SIN-ENS").build();

        context.restoreAuthSystemState();

        profileInitializer.createOrUpdateProfile(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        assertThat(profile, is(person));

        person = context.reloadEntity(person);
        assertThat(person.getMetadata(), hasSize(30));

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

        Item personItem = ItemBuilder.createItem(context, profiles)
            .withTitle("My User")
            .withMetadata("epfl", "sciperId", null, "352234")
            .withDspaceObjectOwner(admin)
            .build();

        context.restoreAuthSystemState();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> profileInitializer.createOrUpdateProfile(context, eperson));

        assertThat(exception.getMessage(), is("The item " + personItem.getID().toString() + " is already linked "
            + "to another eperson: " + admin.getID() + " cannot be linked to " + eperson.getID().toString()));

    }

    @Test
    public void testInitializeWithUpdate() throws SQLException, AuthorizeException {

        context.turnOffAuthorisationSystem();


        ItemBuilder.createItem(context, orgUnits)
                   .withTitle("Laboratory of Sensing and Networking Systems")
                   .withAcronym("SENS").build();

        ItemBuilder.createItem(context, orgUnits)
                   .withTitle("SSC - Teaching")
                   .withAcronym("SSC-ENS").build();

        ItemBuilder.createItem(context, orgUnits)
                   .withTitle("SIN - Teaching")
                   .withAcronym("SIN-ENS").build();

        EPerson eperson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Test", "User")
            .withEmail("test@user.it")
            .withNetId("352234@epfl.ch")
            .build();

        context.restoreAuthSystemState();

        profileInitializer.createOrUpdateProfile(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        assertThat(profile.getMetadata(), hasSize(28));

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));

        // we don't expect any change to be performed
        profileInitializer.createOrUpdateProfile(context, eperson);

        researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());

        Item updatedProfile = researcherProfile.getItem();
        assertThat(updatedProfile, is(profile));

        assertThat(updatedProfile.getMetadata(), hasSize(28));

        Bitstream newPicture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(newPicture, notNullValue());
        assertThat(newPicture, is(not(picture)));
        assertThat(newPicture.getMetadata(), hasItem(with("dc.type", "personal picture")));

        assertThat(bitstreamService.getBitstreamByBundleName(updatedProfile, "ORIGINAL"), hasSize(1));
        assertThat(groupService.allMemberGroupsSet(context, eperson)
                               .stream().anyMatch(g -> g.getName().equals(submitters.getName())), is(true));
    }

    @Test
    public void testNotExistingAffiliationsAreClosed() throws SQLException, AuthorizeException {

        context.turnOffAuthorisationSystem();


        ItemBuilder.createItem(context, orgUnits)
                   .withTitle("Laboratory of Sensing and Networking Systems")
                   .withAcronym("SENS").build();

        ItemBuilder.createItem(context, orgUnits)
                   .withTitle("SSC - Teaching")
                   .withAcronym("SSC-ENS").build();

        ItemBuilder.createItem(context, orgUnits)
                   .withTitle("SIN - Teaching")
                   .withAcronym("SIN-ENS").build();

        Item closedAff = ItemBuilder.createItem(context, orgUnits)
                   .withTitle("SIN - closed")
                   .withAcronym("SIN-CLS").build();

        EPerson eperson = EPersonBuilder.createEPerson(context)
                                        .withNameInMetadata("Test", "User")
                                        .withEmail("test@user.it")
                                        .withNetId("352234@epfl.ch")
                                        .build();

        Item existingProfile = ItemBuilder.createItem(context, profiles)
                               .withDspaceObjectOwner(eperson)
                               .withTitle("User, Test")
                               .withPersonAffiliation("SIN-CLS", closedAff.getID().toString())
                               .withPersonAffiliationStartDate("2022-01-01")
                               .withPersonAffiliationEndDate(PLACEHOLDER_PARENT_METADATA_VALUE).build();

        context.restoreAuthSystemState();

        profileInitializer.createOrUpdateProfile(context, eperson);

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);
        assertThat(researcherProfile.getItem().getID(), is(existingProfile.getID()));

        Item profile = researcherProfile.getItem();
        String yesterday = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now().minusDays(1L));
        assertThat(profile.getMetadata(), hasItems(
            with("oairecerif.person.affiliation", "SIN-CLS", closedAff.getID().toString(), 600),
            with("oairecerif.affiliation.startDate", "2022-01-01"),
            with("oairecerif.affiliation.endDate", yesterday),
            with("oairecerif.person.affiliation", "SENS", "will be referenced::ACRONYM::SENS", 1, 400),
            with("oairecerif.affiliation.startDate", yesterday, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1)
        ));

    }

    @Test
    public void testRemoveFromSubmitters() throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("Test", "User")
                .withEmail("test@user.it")
                .withNetId("352234@epfl.ch")
                .build();

        ItemBuilder.createItem(context, profiles)
                .withTitle("My User")
                .withBirthDate("1992-06-26")
                .withMetadata("epfl", "sciperId", null, "352234")
                .build();

        groupService.addMember(context, submitters, eperson);
        context.restoreAuthSystemState();

        profileInitializer.createOrUpdateProfile(context, eperson);
        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(itemService.getMetadata(researcherProfile.getItem(), "epfl.sciper.active"), is("true"));

        assertThat(groupService.allMemberGroupsSet(context, eperson)
                .stream().anyMatch(g -> g.getName().equals(submitters.getName())), is(false));
    }

    private void assertVisible(ResearcherProfile researcherProfile) throws SQLException {
        List<ResourcePolicy> resourcePolicies = resourcePolicyService.find(context, researcherProfile.getItem());
        boolean visible = resourcePolicies
            .stream()
            .filter(policy -> policy.getGroup() != null)
            .anyMatch(policy -> READ == policy.getAction() && ANONYMOUS.equals(policy.getGroup().getName()));

        assertThat(visible, is(true));
    }
}
