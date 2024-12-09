/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.sync;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.dspace.app.matcher.MetadataValueMatcher.with;
import static org.dspace.core.Constants.READ;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;
import static org.dspace.eperson.Group.ANONYMOUS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
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
import org.dspace.content.MetadataValue;
import org.dspace.content.authority.Choices;
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

public class EpflUserSynchronizationScriptIT extends AbstractIntegrationTestWithDatabase {

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
        context.commit();
        context.restoreAuthSystemState();

    }

    @After
    public void after() throws Exception {

        personApiService.setApiClient(apiClient);
        orgUnitApiService.setApiClient(apiClient);

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testProfileCreation()
            throws SQLException, AuthorizeException, InstantiationException, IllegalAccessException, IOException {

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

        // run script
        String[] args = new String[] { "epfl-user-synchronization", "-e", admin.getEmail()};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
        assertThat(handler.getInfoMessages(), contains(
            is("EPerson with uuid: " + eperson.getID().toString() + ", sciperId: 352234 was updated"),
            is("Changes:"),
            is("Number of created epersons: 0"),
            is("Number of updated epersons: 1")
            )
        );

        eperson = context.reloadEntity(eperson);
        assertThat(eperson.getFirstName(), equalTo("Haitham"));
        assertThat(eperson.getLastName(), equalTo("Al Hassanieh"));
        assertThat(eperson.getEmail(), equalTo("haitham.alhassanieh@epfl.ch"));
        assertThat(eperson.getNetid(), equalTo("352234@epfl.ch"));

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
            with("oairecerif.person.affiliation", "SENS", sens.getID().toString(), Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.affiliation.role", "Associate Professor", 1),
            with("oairecerif.person.affiliation", "SSC-ENS", teaching.getID().toString(), 1, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.role", "Associate Professor", 2),
            with("oairecerif.person.affiliation", "SIN-ENS", sinTeaching.getID().toString(), 2, Choices.CF_ACCEPTED),
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
    @SuppressWarnings("unchecked")
    public void testInitializeWithPersonWithThatSciperAlreadyExisting()
            throws SQLException, AuthorizeException, InstantiationException, IllegalAccessException {

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

        Item sens = ItemBuilder.createItem(context, orgUnits)
                   .withTitle("Laboratory of Sensing and Networking Systems")
                   .withAcronym("SENS").build();

        Item ssc = ItemBuilder.createItem(context, orgUnits)
                   .withTitle("SSC - Teaching")
                   .withAcronym("SSC-ENS").build();

        Item sin = ItemBuilder.createItem(context, orgUnits)
                   .withTitle("SIN - Teaching")
                   .withAcronym("SIN-ENS").build();

        context.restoreAuthSystemState();

        // run script
        String[] args = new String[] { "epfl-user-synchronization", "-e", admin.getEmail()};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
        assertThat(handler.getInfoMessages(), contains(
                is("EPerson with uuid: " + eperson.getID().toString() + ", sciperId: 352234 was updated"),
                is("Changes:"),
                is("Number of created epersons: 0"),
                is("Number of updated epersons: 1")
                )
            );

        eperson = context.reloadEntity(eperson);
        person = context.reloadEntity(person);

        assertThat(eperson.getFirstName(), equalTo("Haitham"));
        assertThat(eperson.getLastName(), equalTo("Al Hassanieh"));
        assertThat(eperson.getEmail(), equalTo("haitham.alhassanieh@epfl.ch"));
        assertThat(eperson.getNetid(), equalTo("352234@epfl.ch"));

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        // we have retrieved the profile using the eperson so if it matches it means that the eperson has been set as
        // owner of the profile
        assertThat(profile, is(person));

        String yesterday = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now().minusDays(1L));
        assertThat(person.getMetadata(), hasItems(
            with("dc.title", "Al Hassanieh, Haitham"),
            with("person.givenName", "Haitham"),
            with("person.familyName", "Al Hassanieh"),
            with("person.email", "haitham.alhassanieh@epfl.ch"),
            with("person.birthDate", "1992-06-26"), // existing extra metadata are preserved
            with("epfl.sciper.active", "true"),
            with("epfl.sciperId", "352234"),
            with("oairecerif.identifier.url", "https://people.epfl.ch/haitham.alhassanieh"),
            with("oairecerif.affiliation.role", "Associate Professor"),
            with("oairecerif.person.affiliation", "SENS", sens.getID().toString(), Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.affiliation.role", "Associate Professor", 1),
            with("oairecerif.person.affiliation", "SSC-ENS", ssc.getID().toString(), 1, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.role", "Associate Professor", 2),
            with("oairecerif.person.affiliation", "SIN-ENS", sin.getID().toString(), 2, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday, 2),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 2)));

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));
    }

    @Test
    public void testInitializeWithResearcherProfileAssignedToAnotherEPerson()
            throws SQLException, AuthorizeException, InstantiationException, IllegalAccessException {

        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Test", "User")
            .withEmail("test@user.it")
            .withNetId("352234@epfl.ch")
            .build();

        EPerson eperson2 = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("Test", "User")
                .withEmail("test2@user.it")
                .withNetId("352235@epfl.ch")
                .build();

        Item profile = ItemBuilder.createItem(context, profiles)
            .withTitle("My User")
            .withMetadata("epfl", "sciperId", null, "352235")
            .withDspaceObjectOwner(admin)
            .build();

        context.restoreAuthSystemState();

        // run script
        String[] args = new String[] { "epfl-user-synchronization", "-e", admin.getEmail()};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        assertThat(handler.getErrorMessages(), contains(
                is("Unable to sync profile 352235: The item " + profile.getID().toString()
                        + " is already linked to another eperson: " + admin.getID().toString()
                        + " cannot be linked to " + eperson2.getID().toString())));
        assertThat(handler.getWarningMessages(), empty());
        assertThat(handler.getInfoMessages(), contains(
                is("EPerson with uuid: " + eperson.getID().toString() + ", sciperId: 352234 was updated"),
                is("Changes:"),
                is("Number of created epersons: 0"),
                is("Number of updated epersons: 1")
                )
            );
    }

    @Test
    public void testUpdateAffiliations()
            throws SQLException, AuthorizeException, InstantiationException, IllegalAccessException {

        context.turnOffAuthorisationSystem();


        Item sens = ItemBuilder.createItem(context, orgUnits)
                   .withTitle("Laboratory of Sensing and Networking Systems")
                   .withAcronym("SENS").build();

        Item ssc = ItemBuilder.createItem(context, orgUnits)
                   .withTitle("SSC - Teaching")
                   .withAcronym("SSC-ENS").build();

        Item sin = ItemBuilder.createItem(context, orgUnits)
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

        // run script
        String[] args = new String[] { "epfl-user-synchronization", "-e", admin.getEmail()};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
        assertThat(handler.getInfoMessages(), contains(
                is("EPerson with uuid: " + eperson.getID().toString() + ", sciperId: 352234 was updated"),
                is("Changes:"),
                is("Number of created epersons: 0"),
                is("Number of updated epersons: 1")
                )
            );

        eperson = context.reloadEntity(eperson);
        assertThat(eperson.getFirstName(), equalTo("Haitham"));
        assertThat(eperson.getLastName(), equalTo("Al Hassanieh"));
        assertThat(eperson.getEmail(), equalTo("haitham.alhassanieh@epfl.ch"));
        assertThat(eperson.getNetid(), equalTo("352234@epfl.ch"));

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);
        assertThat(researcherProfile.getItem().getID(), is(existingProfile.getID()));

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
            with("oairecerif.affiliation.role", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.person.affiliation", "SIN-CLS", closedAff.getID().toString(), Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", "2022-01-01"),
            with("oairecerif.affiliation.endDate", yesterday),
            with("oairecerif.affiliation.role", "Associate Professor", 1),
            with("oairecerif.person.affiliation", "SENS", sens.getID().toString(), 1, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.role", "Associate Professor", 2),
            with("oairecerif.person.affiliation", "SSC-ENS", ssc.getID().toString(), 2, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday, 2),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 2),
            with("oairecerif.affiliation.role", "Associate Professor", 3),
            with("oairecerif.person.affiliation", "SIN-ENS", sin.getID().toString(), 3, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday, 3),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 3)));

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));


    }

    @Test
    @Ignore // ignored because the update of the affiliation is not needed anymore
    public void testUpdateAffiliationsWithProfileHavingWrongAffiliationName()
        throws SQLException, AuthorizeException, InstantiationException, IllegalAccessException {

        context.turnOffAuthorisationSystem();


        Item sensAff = ItemBuilder.createItem(context, orgUnits)
                                  .withTitle("Laboratory of Sensing and Networking Systems")
                                  .withAcronym("SENS").build();

        Item ssc = ItemBuilder.createItem(context, orgUnits)
                              .withTitle("SSC - Teaching")
                              .withAcronym("SSC-ENS").build();

        Item sin = ItemBuilder.createItem(context, orgUnits)
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

        Item existingProfile = ItemBuilder
            .createItem(context, profiles)
            .withDspaceObjectOwner(eperson)
            .withTitle("User, Test")
            .withPersonAffiliation("Laboratory of Sensing and Networking Systems", sensAff.getID().toString())
            .withPersonAffiliationStartDate("2022-01-01")
            .withPersonAffiliationEndDate(PLACEHOLDER_PARENT_METADATA_VALUE).build();

        context.restoreAuthSystemState();

        // run script
        String[] args = new String[] { "epfl-user-synchronization", "-e", admin.getEmail()};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
        assertThat(handler.getInfoMessages(), contains(
                       is("EPerson with uuid: " + eperson.getID().toString() + ", sciperId: 352234 was updated"),
                       is("Changes:"),
                       is("Number of created epersons: 0"),
                       is("Number of updated epersons: 1")
                   )
        );

        eperson = context.reloadEntity(eperson);
        assertThat(eperson.getFirstName(), equalTo("Haitham"));
        assertThat(eperson.getLastName(), equalTo("Al Hassanieh"));
        assertThat(eperson.getEmail(), equalTo("haitham.alhassanieh@epfl.ch"));
        assertThat(eperson.getNetid(), equalTo("352234@epfl.ch"));

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);
        assertThat(researcherProfile.getItem().getID(), is(existingProfile.getID()));

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
            with("oairecerif.affiliation.role", PLACEHOLDER_PARENT_METADATA_VALUE, 0),
            with("oairecerif.person.affiliation", "SENS", sensAff.getID().toString(), 0, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", "2022-01-01", 0),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 0),
            with("oairecerif.affiliation.role", "Associate Professor", 1),
            with("oairecerif.person.affiliation", "SSC-ENS", ssc.getID().toString(), 1, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.role", "Associate Professor", 2),
            with("oairecerif.person.affiliation", "SIN-ENS", sin.getID().toString(), 2, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday, 2),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 2)));

        List<MetadataValue> affiliations = getMetadataValuesByMetadataString(profile, "oairecerif_person_affiliation");
        assertEquals(3, affiliations.size());

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "352234.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));
    }

    @Test
    public void testUpdateAffiliationsWithNotActiveAffiliations()
            throws SQLException, AuthorizeException, InstantiationException, IllegalAccessException {

        context.turnOffAuthorisationSystem();


        Item sensAff = ItemBuilder.createItem(context, orgUnits)
                .withTitle("Laboratory of Sensing and Networking Systems")
                .withAcronym("SENS").build();

        ItemBuilder.createItem(context, orgUnits)
                .withTitle("SCI-CDH-FGB")
                .withAcronym("SCI-CDH-FGB").build();

        ItemBuilder.createItem(context, orgUnits)
                .withTitle("SHS-ENS")
                .withAcronym("SHS-ENS").build();

        EPerson eperson = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("Graezer", "Bideau")
                .withEmail("florence.graezerbideau@epfl.ch")
                .withNetId("196358@epfl.ch")
                .build();

        Item existingProfile = ItemBuilder
                .createItem(context, profiles)
                .withDspaceObjectOwner(eperson)
                .withTitle("Graezer, Bideau").build();

        context.restoreAuthSystemState();

        // run script
        String[] args = new String[]{"epfl-user-synchronization", "-e", admin.getEmail()};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        //After actually sync all values check that process will run with no problem and no changes
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

    }

    @Test
    public void testProfileCreationWithActiveAffiliationsButNoMainAffiliation()
        throws SQLException, AuthorizeException, InstantiationException, IllegalAccessException {

        context.turnOffAuthorisationSystem();

        Item upa = ItemBuilder.createItem(context, orgUnits)
                              .withTitle("Prof. Ablasser Group")
                              .withAcronym("UPABLASSER").build();

        Item lvg = ItemBuilder.createItem(context, orgUnits)
                              .withTitle("Laboratory of Virology and Genetics")
                              .withAcronym("LVG").build();

        EPerson eperson = EPersonBuilder.createEPerson(context)
                                        .withNameInMetadata("Test", "User")
                                        .withEmail("test@user.it")
                                        .withNetId("375968@epfl.ch")
                                        .build();

        context.restoreAuthSystemState();

        // run script
        String[] args = new String[] { "epfl-user-synchronization", "-e", admin.getEmail()};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
        assertThat(handler.getInfoMessages(), contains(
            is("EPerson with uuid: " + eperson.getID().toString() + ", sciperId: 375968 was updated"),
            is("Changes:"),
            is("Number of created epersons: 0"),
            is("Number of updated epersons: 1")
        ));

        eperson = context.reloadEntity(eperson);
        assertThat(eperson.getFirstName(), equalTo("Iris Arianna"));
        assertThat(eperson.getLastName(), equalTo("Dorschel"));
        assertThat(eperson.getEmail(), equalTo("arianna.dorschel@epfl.ch"));
        assertThat(eperson.getNetid(), equalTo("375968@epfl.ch"));

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        Item profile = researcherProfile.getItem();
        String yesterday = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now().minusDays(1L));
        assertThat(profile.getMetadata(), hasItems(
            with("dc.title", "Dorschel, Iris Arianna"),
            with("person.givenName", "Iris Arianna"),
            with("person.familyName", "Dorschel"),
            with("person.email", "arianna.dorschel@epfl.ch"),
            with("epfl.sciper.active", "true"),
            with("epfl.sciperId", "375968"),
            with("oairecerif.identifier.url", "https://people.epfl.ch/arianna.dorschel"),
            with("oairecerif.affiliation.role", "Doctoral Assistant", 0),
            with("oairecerif.person.affiliation", "UPABLASSER", upa.getID().toString(), 0, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday, 0),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 0),
            with("oairecerif.affiliation.role", "Doctoral Assistant", 1),
            with("oairecerif.person.affiliation", "LVG", lvg.getID().toString(), 1, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1)));

        List<MetadataValue> affiliations = getMetadataValuesByMetadataString(profile, "oairecerif_person_affiliation");
        assertEquals(2, affiliations.size());

        List<MetadataValue> mainAffiliations = getMetadataValuesByMetadataString(profile, "person_affiliation_name");
        assertEquals(1, mainAffiliations.size());

        Bitstream picture = bitstreamService.getBitstreamByName(profile, "ORIGINAL", "375968.jpg");
        assertThat(picture, notNullValue());
        assertThat(picture.getMetadata(), hasItem(with("dc.type", "personal picture")));
    }

    @Test
    public void testProfileCreationDcTitle()
            throws SQLException, InstantiationException, IllegalAccessException, AuthorizeException {
        context.turnOffAuthorisationSystem();

        Item cosecSti = ItemBuilder.createItem(context, orgUnits)
                .withMetadata("dc", "title", null, "fr", "COSEC - STI", null, -1)
                .withMetadata("dc", "title", null, "en", "COSEC - STI", null, -1)
                .withMetadata("dc", "type", null, null, "DIVERS", null, -1)
                .withMetadata("oairecerif", "acronym", null, "fr", "COSEC-STI", null, -1)
                .withMetadata("oairecerif", "acronym", null, "en", "COSEC-STI", null, -1)
                .build();

        Item ptmhGe = ItemBuilder.createItem(context, orgUnits)
                .withMetadata("dc", "title", null, "fr",
                        "Plateforme technologique machines hydrauliques - Gestion", null, -1)
                .withMetadata("dc", "title", null, "en", "Hydraulic Machines Platform - Administration", null, -1)
                .withMetadata("dc", "type", null, null, "CENTRE", null, -1)
                .withMetadata("oairecerif", "acronym", null, "fr", "PTMH-GE", null, -1)
                .withMetadata("oairecerif", "acronym", null, "en", "PTMH-GE", null, -1)
                .build();

        context.commit();

        EPerson eperson = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("Louis", "Vina")
                .withEmail("louis.vina@epfl.ch")
                .withNetId("251859@epfl.ch")
                .build();

        Item profile = ItemBuilder.createItem(context, profiles)
                .withMetadata("epfl", "sciperId", null, "251859")
                .withMetadata("oairecerif", "affiliation", "endDate", "#PLACEHOLDER_PARENT_METADATA_VALUE#")
                .withMetadata("oairecerif", "affiliation", "startDate", "2024-07-09")
                .withMetadata("oairecerif", "affiliation", "role", "Safety Delegate")
                .withMetadata("oairecerif", "person", "affiliation",
                        null, "COSEC-STI", cosecSti.getID().toString(), 600)
                .build();

        context.commit();

        String[] args = new String[] { "epfl-user-synchronization", "-e", admin.getEmail(), "-q", "251859"};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        profile = researcherProfile.getItem();

        assertThat(profile.getMetadata(), hasItem(with("dc.title", "Vina, Louis")));
        assertThat(profile.getMetadata(),
                hasItem(with("oairecerif.person.affiliation", "COSEC-STI", cosecSti.getID().toString(), 0, 600)));
        assertThat(profile.getMetadata(),
                hasItem(with("oairecerif.person.affiliation", "PTMH-GE", ptmhGe.getID().toString(), 1, 600)));

        context.restoreAuthSystemState();
    }

    @Test
    public void testProfileCreationDcTitleWithTwoAffiliations()
            throws SQLException, InstantiationException, IllegalAccessException, AuthorizeException {
        context.turnOffAuthorisationSystem();

        Item cosecSti = ItemBuilder.createItem(context, orgUnits)
                .withMetadata("dc", "title", null, "fr", "COSEC - STI", null, -1)
                .withMetadata("dc", "title", null, "en", "COSEC - STI", null, -1)
                .withMetadata("dc", "type", null, null, "DIVERS", null, -1)
                .withMetadata("oairecerif", "acronym", null, "fr", "COSEC-STI", null, -1)
                .withMetadata("oairecerif", "acronym", null, "en", "COSEC-STI", null, -1)
                .build();

        Item ptmhGe = ItemBuilder.createItem(context, orgUnits)
                .withMetadata("dc", "title", null, "fr",
                        "Plateforme technologique machines hydrauliques - Gestion", null, -1)
                .withMetadata("dc", "title", null, "en", "Hydraulic Machines Platform - Administration", null, -1)
                .withMetadata("dc", "type", null, null, "CENTRE", null, -1)
                .withMetadata("oairecerif", "acronym", null, "fr", "PTMH-GE", null, -1)
                .withMetadata("oairecerif", "acronym", null, "en", "PTMH-GE", null, -1)
                .build();

        context.commit();

        EPerson eperson = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("Louis", "Vina")
                .withEmail("louis.vina@epfl.ch")
                .withNetId("251859@epfl.ch")
                .build();

        Item profile = ItemBuilder.createItem(context, profiles)
                .withMetadata("dc", "title", null, "Vina, Louis")
                .withMetadata("epfl", "sciperId", null, "251859")
                .withMetadata("oairecerif", "affiliation", "endDate", "#PLACEHOLDER_PARENT_METADATA_VALUE#")
                .withMetadata("oairecerif", "affiliation", "startDate", "2024-07-09")
                .withMetadata("oairecerif", "affiliation", "role", "Safety Delegate")
                .withMetadata("oairecerif", "person", "affiliation",
                        null, "COSEC-STI", cosecSti.getID().toString(), 600)
                .withMetadata("oairecerif", "affiliation", "endDate", "#PLACEHOLDER_PARENT_METADATA_VALUE#")
                .withMetadata("oairecerif", "affiliation", "startDate", "2024-07-09")
                .withMetadata("oairecerif", "affiliation", "role", "Safety Delegate")
                .withMetadata("oairecerif", "person", "affiliation",
                        null, "PTMH-GE", ptmhGe.getID().toString(), 600)
                .build();

        context.commit();

        String[] args = new String[] { "epfl-user-synchronization", "-e", admin.getEmail(), "-q", "251859"};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        ResearcherProfile researcherProfile = researcherProfileService.findById(context, eperson.getID());
        assertThat(researcherProfile, notNullValue());
        assertVisible(researcherProfile);

        profile = researcherProfile.getItem();

        assertThat(profile.getMetadata(), hasItem(with("dc.title", "Vina, Louis")));
        assertThat(profile.getMetadata(),
                hasItem(with("oairecerif.person.affiliation", "COSEC-STI", cosecSti.getID().toString(), 0, 600)));
        assertThat(profile.getMetadata(),
                hasItem(with("oairecerif.person.affiliation", "PTMH-GE", ptmhGe.getID().toString(), 1, 600)));

        context.restoreAuthSystemState();
    }

    private void assertVisible(ResearcherProfile researcherProfile) throws SQLException {
        List<ResourcePolicy> resourcePolicies = resourcePolicyService.find(context, researcherProfile.getItem());
        boolean visible = resourcePolicies
            .stream()
            .filter(policy -> policy.getGroup() != null)
            .anyMatch(policy -> READ == policy.getAction() && ANONYMOUS.equals(policy.getGroup().getName()));

        assertThat(visible, is(true));
    }

    private List<MetadataValue> getMetadataValuesByMetadataString(Item item, String metadataString) {
        return item.getMetadata().stream()
                   .filter(mv -> metadataString.equals(mv.getMetadataField().toString()))
                   .collect(Collectors.toList());
    }
}
