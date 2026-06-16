/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.matcher.MetadataValueMatcher.with;
import static org.dspace.core.Constants.READ;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;
import static org.dspace.eperson.Group.ANONYMOUS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authenticate.ShibAuthentication;
import org.dspace.authenticate.service.ProfileInitializer;
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
import org.dspace.content.authority.Choices;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.profile.ResearcherProfile;
import org.dspace.profile.service.ResearcherProfileService;
import org.dspace.utils.DSpace;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Integration tests for the customization in {@link ShibAuthentication} to initialize the eperson / researcher profile
 * via the {@link ProfileInitializer}
 *
 */
@Ignore("Temporarily ignored at class level")
public class EPFLLoginAndProfileIT extends AbstractControllerIntegrationTest {
    private ResearcherProfileService researcherProfileService = new DSpace()
            .getSingletonService(ResearcherProfileService.class);

    private ResourcePolicyService resourcePolicyService = new DSpace().getServiceManager()
            .getServicesByType(ResourcePolicyService.class).get(0);

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    private GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();

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

        context.restoreAuthSystemState();

    }

    @Test
    public void testResearcherProfileCreatedOnLogin() throws Exception {

        context.turnOffAuthorisationSystem();
        EPerson eperson = EPersonBuilder.createEPerson(context)
                .withNameInMetadata("Test", "User")
                .withEmail("haitham.alhassanieh@epfl.ch")
                .withNetId("352234@epfl.ch")
                .withPassword(password)
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

        // perform the login
        getAuthToken(eperson.getEmail(), password);
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
            with("oairecerif.identifier.url", "https://people.epfl.ch/haitham.alhassanieh", "https://people.epfl.ch/haitham.alhassanieh", 600),
            with("oairecerif.affiliation.role", "Associate Professor"),
            with("oairecerif.person.affiliation", "SENS", sens.getID().toString(), Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE),
            with("oairecerif.affiliation.role", "Associate Professor", 1),
            // FIXME the confidence should be 400... the metadata seems to be created in the right way but once that
            // the item is retrieved from the db it turns to -1
            with("oairecerif.person.affiliation", "SSC-ENS", teaching.getID().toString(), 1, Choices.CF_ACCEPTED),
            with("oairecerif.affiliation.startDate", yesterday, 1),
            with("oairecerif.affiliation.endDate", PLACEHOLDER_PARENT_METADATA_VALUE, 1),
            with("oairecerif.affiliation.role", "Associate Professor", 2),
            // FIXME the confidence should be 400... the metadata seems to be created in the right way but once that
            // the item is retrieved from the db it turns to -1
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
    public void testLoginExistingUnlinkedProfile() throws Exception {
        context.turnOffAuthorisationSystem();

        EPerson eperson = EPersonBuilder.createEPerson(context)
            .withNameInMetadata("Test", "User")
            .withEmail("haitham.alhassanieh@epfl.ch")
            .withNetId("352234@epfl.ch")
            .withPassword(password)
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

        // perform the login
        getAuthToken(eperson.getEmail(), password);

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
            with("oairecerif.identifier.url", "https://people.epfl.ch/haitham.alhassanieh", "https://people.epfl.ch/haitham.alhassanieh", 600),
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

    private void assertVisible(ResearcherProfile researcherProfile) throws SQLException {
        List<ResourcePolicy> resourcePolicies = resourcePolicyService.find(context, researcherProfile.getItem());
        boolean visible = resourcePolicies
            .stream()
            .filter(policy -> policy.getGroup() != null)
            .anyMatch(policy -> READ == policy.getAction() && ANONYMOUS.equals(policy.getGroup().getName()));

        assertThat(visible, is(true));
    }

}
