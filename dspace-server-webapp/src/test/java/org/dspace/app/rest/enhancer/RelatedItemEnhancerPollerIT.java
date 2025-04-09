/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.enhancer;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.dspace.app.matcher.MetadataValueMatcher.with;
import static org.dspace.app.matcher.MetadataValueMatcher.withNoPlace;
import static org.dspace.builder.CollectionBuilder.createCollection;
import static org.dspace.builder.CommunityBuilder.createCommunity;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.matcher.CustomItemMatcher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authority.service.AuthorityValueService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.enhancer.service.ItemEnhancerService;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.ReloadableEntity;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

public class RelatedItemEnhancerPollerIT extends AbstractIntegrationTestWithDatabase {

    private ItemService itemService;
    private ItemEnhancerService itemEnhancerService;
    private ItemEnhancerService spyItemEnhancerService;
    private RelatedItemEnhancerUpdatePoller poller = new RelatedItemEnhancerUpdatePoller();
    private Collection collection;

    @Before
    public void setup() throws InterruptedException {
        final DSpace dspace = new DSpace();
        ConfigurationService configurationService = dspace.getConfigurationService();
        configurationService.setProperty("item.enable-virtual-metadata", false);
        itemService = ContentServiceFactory.getInstance().getItemService();
        itemEnhancerService = dspace.getSingletonService(ItemEnhancerService.class);
        spyItemEnhancerService = spy(itemEnhancerService);
        poller.setItemEnhancerService(spyItemEnhancerService);
        poller.setItemService(itemService);
        // cleanup the queue from any items left behind by other tests
        poller.pollItemToUpdateAndProcess();
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent Community")
            .build();

        collection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Collection")
            .build();
        context.restoreAuthSystemState();
    }

    @Test
    public void testUpdateRelatedItemAreProcessed() throws Exception {

        context.turnOffAuthorisationSystem();

        Item person = ItemBuilder.createItem(context, collection)
            .withTitle("Walter White")
            .withPersonMainAffiliation("4Science")
            .build();

        String personId = person.getID().toString();

        Item person2 = ItemBuilder.createItem(context, collection)
                .withTitle("John Red")
                .build();

        String person2Id = person2.getID().toString();

        Item person3 = ItemBuilder.createItem(context, collection)
                .withTitle("Marc Green")
                .withOrcidIdentifier("orcid-person3")
                .withPersonMainAffiliation("Affiliation 1")
                .withPersonMainAffiliation("Affiliation 2")
                .build();

        String person3Id = person3.getID().toString();

        Item publication = ItemBuilder.createItem(context, collection)
            .withTitle("Test publication")
            .withEntityType("Publication")
            .withAuthor("Walter White", personId)
            .build();

        Item publication2 = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication 2")
                .withEntityType("Publication")
                .withSubject("test")
                .withAuthor("Walter White", personId)
                .withAuthor("John Red", person2Id)
                .build();

        Item publication3 = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication 3")
                .withEntityType("Publication")
                .withAuthor("John Red", person2Id)
                .withAuthor("Marc Green", person3Id)
                .build();

        context.restoreAuthSystemState();
        publication = context.reloadEntity(publication);
        publication2 = context.reloadEntity(publication2);
        publication3 = context.reloadEntity(publication3);

        List<MetadataValue> metadataValues = publication.getMetadata();
        assertThat(metadataValues, hasSize(20));
        assertThat(metadataValues, hasItem(with("cris.virtual.department", "4Science")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.department", personId)));
        assertThat(metadataValues, hasItem(with("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.orcid", personId)));
        List<MetadataValue> metadataValues2 = publication2.getMetadata();
        assertThat(metadataValues2, hasSize(32));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtual.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.department", "4Science"),
                    withNoPlace("cris.virtual.department", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtualsource.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.department", personId),
                    withNoPlace("cris.virtualsource.department", person2Id)));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtual.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE),
                    withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtualsource.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.orcid", personId),
                    withNoPlace("cris.virtualsource.orcid", person2Id)));
        List<MetadataValue> metadataValues3 = publication3.getMetadata();
        assertThat(metadataValues3, hasSize(33));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtual.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.department", PLACEHOLDER_PARENT_METADATA_VALUE),
                    withNoPlace("cris.virtual.department", "Affiliation 1"),
                    withNoPlace("cris.virtual.department", "Affiliation 2")));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtualsource.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.department", person2Id),
                    withNoPlace("cris.virtualsource.department", person3Id),
                    withNoPlace("cris.virtualsource.department", person3Id)));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtual.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE),
                    withNoPlace("cris.virtual.orcid", "orcid-person3")));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtualsource.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.orcid", person2Id),
                    withNoPlace("cris.virtualsource.orcid", person3Id)));

        context.turnOffAuthorisationSystem();
        itemService.addMetadata(context, person, "person", "identifier", "orcid", null, "1234-5678-9101");
        itemService.addMetadata(context, person, "person", "affiliation", "name", null, "Company");
        itemService.update(context, person);
        context.restoreAuthSystemState();
        person = commitAndReload(person);
        Mockito.reset(spyItemEnhancerService);
        poller.pollItemToUpdateAndProcess();
        verify(spyItemEnhancerService).enhance(any(), argThat(new CustomItemMatcher(publication.getID())), eq(true));
        verify(spyItemEnhancerService).enhance(any(), argThat(new CustomItemMatcher(publication2.getID())), eq(true));
        // 2 + 1 iteration as the last poll will return null
        verify(spyItemEnhancerService, times(3)).pollItemToUpdate(any());
        verify(spyItemEnhancerService).saveAffectedItemsForUpdate(any(), eq(publication.getID()));
        verify(spyItemEnhancerService).saveAffectedItemsForUpdate(any(), eq(publication2.getID()));
        verifyNoMoreInteractions(spyItemEnhancerService);
        person = context.reloadEntity(person);
        person2 = context.reloadEntity(person2);
        person3 = context.reloadEntity(person3);
        publication = context.reloadEntity(publication);
        publication2 = context.reloadEntity(publication2);
        publication3 = context.reloadEntity(publication3);

        metadataValues = publication.getMetadata();
        assertThat(metadataValues, hasSize(22));
        assertThat(itemService.getMetadataByMetadataString(publication, "cris.virtual.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.department", "4Science"),
                    withNoPlace("cris.virtual.department", "Company")));
        assertThat(itemService.getMetadataByMetadataString(publication, "cris.virtualsource.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.department", personId),
                    withNoPlace("cris.virtualsource.department", personId)));
        assertThat(metadataValues, hasItem(with("cris.virtual.orcid", "1234-5678-9101")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.orcid", personId)));
        metadataValues2 = publication2.getMetadata();
        assertThat(metadataValues2, hasSize(34));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtual.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.department", "4Science"),
                    withNoPlace("cris.virtual.department", "Company"),
                    withNoPlace("cris.virtual.department", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtualsource.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.department", personId),
                    withNoPlace("cris.virtualsource.department", personId),
                    withNoPlace("cris.virtualsource.department", person2Id)));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtual.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.orcid", "1234-5678-9101"),
                    withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtualsource.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.orcid", personId),
                    withNoPlace("cris.virtualsource.orcid", person2Id)));
        metadataValues3 = publication3.getMetadata();
        assertThat(metadataValues3, hasSize(33));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtual.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.department", PLACEHOLDER_PARENT_METADATA_VALUE),
                    withNoPlace("cris.virtual.department", "Affiliation 1"),
                    withNoPlace("cris.virtual.department", "Affiliation 2")));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtualsource.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.department", person2Id),
                    withNoPlace("cris.virtualsource.department", person3Id),
                    withNoPlace("cris.virtualsource.department", person3Id)));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtual.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE),
                    withNoPlace("cris.virtual.orcid", "orcid-person3")));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtualsource.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.orcid", person2Id),
                    withNoPlace("cris.virtualsource.orcid", person3Id)));
        context.turnOffAuthorisationSystem();
        itemService.clearMetadata(context, person3, "person", "identifier", "orcid", Item.ANY);
        itemService.removeMetadataValues(context, person3,
                List.of(itemService.getMetadataByMetadataString(person3, "person.affiliation.name").get(0)));
        itemService.update(context, person3);
        context.restoreAuthSystemState();
        person3 = commitAndReload(person3);
        Mockito.reset(spyItemEnhancerService);
        poller.pollItemToUpdateAndProcess();
        verify(spyItemEnhancerService).enhance(any(), argThat(new CustomItemMatcher(publication3.getID())), eq(true));
        // 1 + 1 iteration as the last poll will return null
        verify(spyItemEnhancerService, times(2)).pollItemToUpdate(any());
        verify(spyItemEnhancerService).saveAffectedItemsForUpdate(any(), eq(publication3.getID()));
        verifyNoMoreInteractions(spyItemEnhancerService);
        person = context.reloadEntity(person);
        person2 = context.reloadEntity(person2);
        person3 = context.reloadEntity(person3);
        publication = context.reloadEntity(publication);
        publication2 = context.reloadEntity(publication2);
        publication3 = context.reloadEntity(publication3);

        metadataValues = publication.getMetadata();
        assertThat(metadataValues, hasSize(22));
        assertThat(itemService.getMetadataByMetadataString(publication, "cris.virtual.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.department", "4Science"),
                    withNoPlace("cris.virtual.department", "Company")));
        assertThat(itemService.getMetadataByMetadataString(publication, "cris.virtualsource.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.department", personId),
                    withNoPlace("cris.virtualsource.department", personId)));
        assertThat(metadataValues, hasItem(with("cris.virtual.orcid", "1234-5678-9101")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.orcid", personId)));
        metadataValues2 = publication2.getMetadata();
        assertThat(metadataValues2, hasSize(34));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtual.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.department", "4Science"),
                    withNoPlace("cris.virtual.department", "Company"),
                    withNoPlace("cris.virtual.department", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtualsource.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.department", personId),
                    withNoPlace("cris.virtualsource.department", personId),
                    withNoPlace("cris.virtualsource.department", person2Id)));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtual.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.orcid", "1234-5678-9101"),
                    withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(itemService.getMetadataByMetadataString(publication2, "cris.virtualsource.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.orcid", personId),
                    withNoPlace("cris.virtualsource.orcid", person2Id)));
        metadataValues3 = publication3.getMetadata();
        assertThat(metadataValues3, hasSize(31));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtual.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.department", PLACEHOLDER_PARENT_METADATA_VALUE),
                    withNoPlace("cris.virtual.department", "Affiliation 2")));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtualsource.department"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.department", person2Id),
                    withNoPlace("cris.virtualsource.department", person3Id)));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtual.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE),
                    withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(itemService.getMetadataByMetadataString(publication3, "cris.virtualsource.orcid"),
                containsInAnyOrder(
                    withNoPlace("cris.virtualsource.orcid", person2Id),
                    withNoPlace("cris.virtualsource.orcid", person3Id)));

    }

    @Test
    public void testConsumingWillBeReferencedAndWillBeGeneratedOnUnrelatedUnits() throws Exception {

        context.turnOffAuthorisationSystem();
        String ouEPFLAcro = "EPFL";
        Item ouEPFL = ItemBuilder.createItem(context, collection)
                .withEntityType("OrgUnit")
                .withTitle(ouEPFLAcro)
                .withMetadata("oairecerif", "acronym", null, ouEPFLAcro)
                .withMetadata("epfl", "unit", "code", "111")
                .build();

        String ouCRPPAcro = "CRPP";
        Item ouCRPP = ItemBuilder.createItem(context, collection)
                .withEntityType("OrgUnit")
                .withTitle(ouCRPPAcro)
                .withMetadata("oairecerif", "acronym", null, ouCRPPAcro)
                .withMetadata("epfl", "unit", "code", "222")
                .withParentOrganization("UHD", "will be referenced::ACRONYM::UHD")
                .withMetadata("epfl", "orgUnit", "active", "false")
                .build();

        String ouSPCAcro = "SPC";
        Item ouSPC = ItemBuilder.createItem(context, collection)
                .withEntityType("OrgUnit")
                .withTitle(ouSPCAcro)
                .withMetadata("oairecerif", "acronym", null, ouSPCAcro)
                .withMetadata("epfl", "unit", "code", "333")
                .withParentOrganization(ouEPFLAcro, ouEPFL.getID().toString())
                .build();

        Item publication = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication")
                .withEntityType("Publication")
                .withSponsorship(ouCRPPAcro, ouCRPP.getID().toString())
                .withSponsorship(ouSPCAcro, ouSPC.getID().toString())
                .build();

        context.restoreAuthSystemState();

        List<String> parentOrganizationsVirtualSources =
                itemService.getMetadataByMetadataString(publication, "cris.virtualsource.parent-organization")
                .stream()
                .map(MetadataValue::getValue)
                .collect(Collectors.toList());

        assertThat(parentOrganizationsVirtualSources, containsInAnyOrder(
                ouSPC.getID().toString(),
                ouSPC.getID().toString(),
                ouCRPP.getID().toString()));

        String lastModified = itemService.getMetadataFirstValue(publication, "dc", "date", "modified", "*");

        context.turnOffAuthorisationSystem();
        itemService.setMetadataSingleValue(context, ouSPC, "dc", "title", null, null, "modified orgunit title");
        itemService.update(context, ouSPC);

        context.commit();

        // setting the real enhancer service
        poller.setItemEnhancerService(itemEnhancerService);

        // launching the enhancement to create virtual metadata
        poller.pollItemToUpdateAndProcess();

        // restoring the mock for following tests
        poller.setItemEnhancerService(spyItemEnhancerService);

        publication = context.reloadEntity(publication);
        context.restoreAuthSystemState();

        parentOrganizationsVirtualSources =
                itemService.getMetadataByMetadataString(publication, "cris.virtualsource.parent-organization")
                .stream()
                .map(MetadataValue::getValue)
                .collect(Collectors.toList());

        assertThat(parentOrganizationsVirtualSources, containsInAnyOrder(
                ouSPC.getID().toString(),
                ouSPC.getID().toString(),
                ouCRPP.getID().toString()));

        assertThat(itemService.getMetadataFirstValue(publication, "dc", "date", "modified", "*"), is(lastModified));
    }

    @Test
    @Ignore
    public void testOrgUnitHierarchy() throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        Item topOrgUnit = ItemBuilder.createItem(context, collection)
                .withEntityType("OrgUnit")
                .withTitle("University")
                .build();
        Item childOrgUnit = ItemBuilder.createItem(context, collection)
                .withEntityType("OrgUnit")
                .withTitle("Department")
                .withParentOrganization(topOrgUnit.getName(), topOrgUnit.getID().toString())
                .build();
        Item orphanOrgUnit = ItemBuilder.createItem(context, collection)
                .withEntityType("OrgUnit")
                .withTitle("Orphan")
                .build();
        Item labOrgUnit = ItemBuilder.createItem(context, collection)
                .withEntityType("OrgUnit")
                .withTitle("Laboratory")
                .withParentOrganization("Another Department",
                        AuthorityValueService.REFERENCE + "LEGACY-ID::department")
                .build();

        Item person = ItemBuilder.createItem(context, collection)
            .withTitle("Walter White")
            .withEntityType("Person")
            .withPersonMainAffiliation(childOrgUnit.getName(), childOrgUnit.getID().toString())
            .build();

        String personId = person.getID().toString();

        Item person2 = ItemBuilder.createItem(context, collection)
                .withTitle("John Red")
                .withEntityType("Person")
                .withPersonMainAffiliation(topOrgUnit.getName(), topOrgUnit.getID().toString())
                .build();

        String person2Id = person2.getID().toString();

        Item person3 = ItemBuilder.createItem(context, collection)
                .withTitle("Marc Green")
                .withEntityType("Person")
                .withOrcidIdentifier("orcid-person3")
                .withPersonMainAffiliation(orphanOrgUnit.getName(), orphanOrgUnit.getID().toString())
                .build();
        String person3Id = person3.getID().toString();

        Item person4 = ItemBuilder.createItem(context, collection)
                .withTitle("Another Person")
                .withEntityType("Person")
                .withPersonMainAffiliation(labOrgUnit.getName(), labOrgUnit.getID().toString())
                .build();
        String person4Id = person4.getID().toString();

        Item person5 = ItemBuilder.createItem(context, collection)
                .withTitle("An external Person")
                .withEntityType("Person")
                .build();
        String person5Id = person5.getID().toString();

        Item publication = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication")
                .withEntityType("Publication")
                .withAuthor("Walter White", personId)
                .build();

        Item publication2 = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication 2")
                .withEntityType("Publication")
                .withSubject("test")
                .withAuthor("Walter White", personId)
                .withAuthor("John Red", person2Id)
                .build();

        Item publication3 = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication 3")
                .withEntityType("Publication")
                .withAuthor("Marc Green", person3Id)
                .build();

        Item publication4 = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication 4")
                .withEntityType("Publication")
                .withAuthor("Another Person", person4Id)
                .build();

        Item publication5 = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication 5")
                .withEntityType("Publication")
                .withAuthor("An external Person", person5Id)
                .build();
        context.restoreAuthSystemState();
        publication = context.reloadEntity(publication);
        publication2 = context.reloadEntity(publication2);
        publication3 = context.reloadEntity(publication3);
        publication4 = context.reloadEntity(publication4);
        publication5 = context.reloadEntity(publication5);

        assertThat(getMetadataValues(publication, "cris.virtual.parent-organization"), containsInAnyOrder(
                withNoPlace("cris.virtual.parent-organization", childOrgUnit.getName(),
                        childOrgUnit.getID().toString()),
                withNoPlace("cris.virtual.parent-organization", topOrgUnit.getName(),
                        topOrgUnit.getID().toString()),
                withNoPlace("cris.virtual.parent-organization", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(getMetadataValues(publication, "cris.virtualsource.parent-organization"), containsInAnyOrder(
                withNoPlace("cris.virtualsource.parent-organization", personId),
                withNoPlace("cris.virtualsource.parent-organization", personId),
                withNoPlace("cris.virtualsource.parent-organization", personId)));
        assertThat(getMetadataValues(publication, "cris.virtual.orcid"),
                hasItem(with("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(getMetadataValues(publication, "cris.virtualsource.orcid"),
                hasItem(with("cris.virtualsource.orcid", personId)));

        assertThat(getMetadataValues(publication2, "cris.virtual.parent-organization"), containsInAnyOrder(
                withNoPlace("cris.virtual.parent-organization", childOrgUnit.getName(),
                        childOrgUnit.getID().toString()),
                withNoPlace("cris.virtual.parent-organization", topOrgUnit.getName(), topOrgUnit.getID().toString()),
                withNoPlace("cris.virtual.parent-organization",  PLACEHOLDER_PARENT_METADATA_VALUE),
                withNoPlace("cris.virtual.parent-organization",  PLACEHOLDER_PARENT_METADATA_VALUE),
                withNoPlace("cris.virtual.parent-organization", topOrgUnit.getName(), topOrgUnit.getID().toString())));
        assertThat(getMetadataValues(publication2, "cris.virtualsource.parent-organization"), containsInAnyOrder(
                        withNoPlace("cris.virtualsource.parent-organization", personId),
                        withNoPlace("cris.virtualsource.parent-organization", personId),
                        withNoPlace("cris.virtualsource.parent-organization", personId),
                        withNoPlace("cris.virtualsource.parent-organization", person2Id),
                        withNoPlace("cris.virtualsource.parent-organization", person2Id)));
        assertThat(getMetadataValues(publication2, "cris.virtual.orcid"),
                containsInAnyOrder(
                        withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE),
                        withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(getMetadataValues(publication2, "cris.virtualsource.orcid"),
                containsInAnyOrder(
                        withNoPlace("cris.virtualsource.orcid", personId),
                        withNoPlace("cris.virtualsource.orcid", person2Id)));

        assertThat(getMetadataValues(publication3, "cris.virtual.parent-organization"),
                containsInAnyOrder(
                        withNoPlace("cris.virtual.parent-organization", orphanOrgUnit.getName(),
                orphanOrgUnit.getID().toString()),
                        withNoPlace("cris.virtual.parent-organization",  PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(getMetadataValues(publication3, "cris.virtualsource.parent-organization"),
                containsInAnyOrder(
                        withNoPlace("cris.virtualsource.parent-organization", person3Id),
                        withNoPlace("cris.virtualsource.parent-organization", person3Id)));
        assertThat(getMetadataValues(publication3, "cris.virtual.orcid"),
                hasItem(withNoPlace("cris.virtual.orcid", "orcid-person3")));
        assertThat(getMetadataValues(publication3, "cris.virtualsource.orcid"),
                hasItem(withNoPlace("cris.virtualsource.orcid", person3Id)));

        assertThat(getMetadataValues(publication4, "cris.virtual.parent-organization"),
                hasItem(withNoPlace("cris.virtual.parent-organization",
                        labOrgUnit.getName(), labOrgUnit.getID().toString())));
        assertThat(getMetadataValues(publication4, "cris.virtualsource.parent-organization"),
                hasItem(withNoPlace("cris.virtualsource.parent-organization", person4Id)));
        assertThat(getMetadataValues(publication4, "cris.virtual.orcid"),
                hasItem(withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(getMetadataValues(publication4, "cris.virtualsource.orcid"),
                hasItem(withNoPlace("cris.virtualsource.orcid", person4Id)));

        assertThat(getMetadataValues(publication5, "cris.virtual.parent-organization"),
                hasItem(withNoPlace("cris.virtual.parent-organization", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(getMetadataValues(publication5, "cris.virtualsource.parent-organization"),
                hasItem(withNoPlace("cris.virtualsource.parent-organization", person5Id)));
        assertThat(getMetadataValues(publication5, "cris.virtual.orcid"),
                hasItem(withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(getMetadataValues(publication5, "cris.virtualsource.orcid"),
                hasItem(withNoPlace("cris.virtualsource.orcid", person5Id)));

        context.turnOffAuthorisationSystem();
        // this department will be the parent of the laboratory so we are
        // changing the hierarchy for the publication 4
        Item childOrgUnit2 = ItemBuilder.createItem(context, collection)
                .withEntityType("OrgUnit")
                .withTitle("Another Department")
                .withParentOrganization(topOrgUnit.getName(), topOrgUnit.getID().toString()).withLegacyId("department")
                .build();

        assertThat(getMetadataValues(childOrgUnit2, "cris.virtual.parent-organization"), hasItem(
                withNoPlace("cris.virtual.parent-organization", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(getMetadataValues(childOrgUnit2, "cris.virtualsource.parent-organization"), hasItem(
                withNoPlace("cris.virtualsource.parent-organization", topOrgUnit.getID().toString())));

        itemService.addMetadata(context, orphanOrgUnit, "organization", "parentOrganization", null, null,
                topOrgUnit.getName(), topOrgUnit.getID().toString(), 600);
        itemService.update(context, orphanOrgUnit);
        orphanOrgUnit = commitAndReload(orphanOrgUnit);
        person5 = context.reloadEntity(person5);
        itemService.addMetadata(context, person5, "person", "affiliation", "name", null, topOrgUnit.getName(),
                topOrgUnit.getID().toString(), 600);
        itemService.update(context, person5);
        person5 = commitAndReload(person5);
        context.restoreAuthSystemState();
        poller.pollItemToUpdateAndProcess();

        publication = context.reloadEntity(publication);
        publication2 = context.reloadEntity(publication2);
        publication3 = context.reloadEntity(publication3);
        publication4 = context.reloadEntity(publication4);
        publication5 = context.reloadEntity(publication5);

      assertThat(getMetadataValues(publication, "cris.virtual.parent-organization"), containsInAnyOrder(
              withNoPlace("cris.virtual.parent-organization", childOrgUnit.getName(),
                      childOrgUnit.getID().toString()),
              withNoPlace("cris.virtual.parent-organization", topOrgUnit.getName(),
                      topOrgUnit.getID().toString()),
              withNoPlace("cris.virtual.parent-organization", PLACEHOLDER_PARENT_METADATA_VALUE)));
      assertThat(getMetadataValues(publication, "cris.virtualsource.parent-organization"), containsInAnyOrder(
              withNoPlace("cris.virtualsource.parent-organization", personId),
              withNoPlace("cris.virtualsource.parent-organization", personId),
              withNoPlace("cris.virtualsource.parent-organization", personId)));
      assertThat(getMetadataValues(publication, "cris.virtual.orcid"),
              hasItem(with("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
      assertThat(getMetadataValues(publication, "cris.virtualsource.orcid"),
              hasItem(with("cris.virtualsource.orcid", personId)));

      assertThat(getMetadataValues(publication2, "cris.virtual.parent-organization"), containsInAnyOrder(
              withNoPlace("cris.virtual.parent-organization", childOrgUnit.getName(),
                      childOrgUnit.getID().toString()),
              withNoPlace("cris.virtual.parent-organization", topOrgUnit.getName(), topOrgUnit.getID().toString()),
              withNoPlace("cris.virtual.parent-organization",  PLACEHOLDER_PARENT_METADATA_VALUE),
              withNoPlace("cris.virtual.parent-organization",  PLACEHOLDER_PARENT_METADATA_VALUE),
              withNoPlace("cris.virtual.parent-organization", topOrgUnit.getName(), topOrgUnit.getID().toString())));
      assertThat(getMetadataValues(publication2, "cris.virtualsource.parent-organization"), containsInAnyOrder(
                      withNoPlace("cris.virtualsource.parent-organization", personId),
                      withNoPlace("cris.virtualsource.parent-organization", personId),
                      withNoPlace("cris.virtualsource.parent-organization", personId),
                      withNoPlace("cris.virtualsource.parent-organization", person2Id),
                      withNoPlace("cris.virtualsource.parent-organization", person2Id)));
      assertThat(getMetadataValues(publication2, "cris.virtual.orcid"),
              containsInAnyOrder(
                      withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE),
                      withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
      assertThat(getMetadataValues(publication2, "cris.virtualsource.orcid"),
              containsInAnyOrder(
                      withNoPlace("cris.virtualsource.orcid", personId),
                      withNoPlace("cris.virtualsource.orcid", person2Id)));

      assertThat(getMetadataValues(publication3, "cris.virtual.parent-organization"),
              containsInAnyOrder(
                      withNoPlace("cris.virtual.parent-organization",
                              orphanOrgUnit.getName(), orphanOrgUnit.getID().toString()),
                      withNoPlace("cris.virtual.parent-organization",
                              topOrgUnit.getName(), topOrgUnit.getID().toString()),
                      withNoPlace("cris.virtual.parent-organization",  PLACEHOLDER_PARENT_METADATA_VALUE)
      ));
      assertThat(getMetadataValues(publication3, "cris.virtualsource.parent-organization"),
              containsInAnyOrder(
                      withNoPlace("cris.virtualsource.parent-organization", person3Id),
                      withNoPlace("cris.virtualsource.parent-organization", person3Id),
                      withNoPlace("cris.virtualsource.parent-organization", person3Id)));
      assertThat(getMetadataValues(publication3, "cris.virtual.orcid"),
              hasItem(withNoPlace("cris.virtual.orcid", "orcid-person3")));
      assertThat(getMetadataValues(publication3, "cris.virtualsource.orcid"),
              hasItem(withNoPlace("cris.virtualsource.orcid", person3Id)));

      assertThat(getMetadataValues(publication4, "cris.virtual.parent-organization"),
              containsInAnyOrder(
                        withNoPlace("cris.virtual.parent-organization",
                                labOrgUnit.getName(), labOrgUnit.getID().toString()),
                        withNoPlace("cris.virtual.parent-organization", childOrgUnit2.getName(),
                                childOrgUnit2.getID().toString()),
                        withNoPlace("cris.virtual.parent-organization",
                                topOrgUnit.getName(), topOrgUnit.getID().toString()),
                        withNoPlace("cris.virtual.parent-organization", PLACEHOLDER_PARENT_METADATA_VALUE)));
      assertThat(getMetadataValues(publication4, "cris.virtualsource.parent-organization"),
              containsInAnyOrder(withNoPlace("cris.virtualsource.parent-organization", person4Id),
                      withNoPlace("cris.virtualsource.parent-organization", person4Id),
                      withNoPlace("cris.virtualsource.parent-organization", person4Id),
                      withNoPlace("cris.virtualsource.parent-organization", person4Id)));
      assertThat(getMetadataValues(publication4, "cris.virtual.orcid"),
              hasItem(withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
      assertThat(getMetadataValues(publication4, "cris.virtualsource.orcid"),
              hasItem(withNoPlace("cris.virtualsource.orcid", person4Id)));

      assertThat(getMetadataValues(publication5, "cris.virtual.parent-organization"),
                containsInAnyOrder(
                        withNoPlace("cris.virtual.parent-organization", topOrgUnit.getName(),
                                topOrgUnit.getID().toString()),
                        withNoPlace("cris.virtual.parent-organization", PLACEHOLDER_PARENT_METADATA_VALUE)));
      assertThat(getMetadataValues(publication5, "cris.virtualsource.parent-organization"),
              hasItem(withNoPlace("cris.virtualsource.parent-organization", person5Id)));
      assertThat(getMetadataValues(publication5, "cris.virtual.orcid"),
              hasItem(withNoPlace("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
      assertThat(getMetadataValues(publication5, "cris.virtualsource.orcid"),
              hasItem(withNoPlace("cris.virtualsource.orcid", person5Id)));
    }

    @Test
    public void testOrgUnitHierarchyForAffinity() throws Exception {

        context.turnOffAuthorisationSystem();

        Community community = createCommunity(context).build();
        GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
        Group anonymousGroup = groupService.findByName(context, Group.ANONYMOUS);
        Collection collectionOrgunit = createCollection(context, community)
                .withDefaultItemRead(anonymousGroup)
                .withEntityType("OrgUnit")
                .build();
        Collection collectionPublication = createCollection(context, community)
                .withDefaultItemRead(anonymousGroup)
                .withEntityType("Publication")
                .build();

        String orgUnitAcronym_A = "SV";
        Item orgUnit_A = createBaseOrgUnit(orgUnitAcronym_A, collectionOrgunit)
                .build();
        String orgUnitId_A = orgUnit_A.getID().toString();

        String orgUnitAcronym_B = "ISREC";
        Item orgUnit_B = createBaseOrgUnit(orgUnitAcronym_B, collectionOrgunit)
                .withParentOrganization(orgUnitAcronym_A, orgUnitId_A)
                .build();
        String orgUnitId_B = orgUnit_B.getID().toString();

        String orgUnitAcronym_C = "GR-KUHN";
        Item orgUnit_C = createBaseOrgUnit(orgUnitAcronym_C, collectionOrgunit)
                .withParentOrganization(orgUnitAcronym_B, orgUnitId_B)
                .build();
        String orgUnitId_C = orgUnit_C.getID().toString();

        Item person_A = ItemBuilder.createItem(context, collection)
                .withTitle("John Red")
                .withEntityType("Person")
                .withPersonMainAffiliation(orgUnitAcronym_A, orgUnitId_A)
                .build();

        Item itemA1 = ItemBuilder.createItem(context, collectionPublication)
                .withTitle("Title Item A1")
                .withSubject("Subject Item A1")
                .withAuthor("John Red", person_A.getID().toString())
                .withSponsorship(orgUnitAcronym_A, orgUnitId_A)
                .build();

        Item itemA2 = ItemBuilder.createItem(context, collectionPublication)
                .withTitle("Title Item A2")
                .withSubject("Subject Item A2")
                .withAuthor("John Red", person_A.getID().toString())
                .withSponsorship(orgUnitAcronym_A, orgUnitId_A)
                .build();

        Item person_B = ItemBuilder.createItem(context, collection)
                .withTitle("Mario Rossi")
                .withEntityType("Person")
                .withPersonMainAffiliation(orgUnitAcronym_B, orgUnitId_B)
                .build();

        Item itemB1 = ItemBuilder.createItem(context, collectionPublication)
                .withTitle("Title Item B1")
                .withSubject("Subject Item B1")
                .withAuthor("Mario Rossi", person_B.getID().toString())
                .withSponsorship(orgUnitAcronym_B, orgUnitId_B)
                .build();

        Item itemB2 = ItemBuilder.createItem(context, collectionPublication)
                .withTitle("Title Item B2")
                .withSubject("Subject Item B2")
                .withAuthor("Mario Rossi", person_B.getID().toString())
                .withSponsorship(orgUnitAcronym_B, orgUnitId_B)
                .build();

        Item person_C = ItemBuilder.createItem(context, collection)
                .withTitle("Banana Joe")
                .withEntityType("Person")
                .withPersonMainAffiliation(orgUnitAcronym_C, orgUnitId_C)
                .build();

        Item itemC1 = ItemBuilder.createItem(context, collectionPublication)
                .withTitle("Title Item C1")
                .withSubject("Subject Item C1")
                .withAuthor("Banana Joe", person_C.getID().toString())
                .withSponsorship(orgUnitAcronym_C, orgUnitId_C)
                .build();

        Item itemC2 = ItemBuilder.createItem(context, collectionPublication)
                .withTitle("Title Item C2")
                .withSubject("Subject Item C2")
                .withAuthor("Banana Joe", person_C.getID().toString())
                .withSponsorship(orgUnitAcronym_C, orgUnitId_C)
                .build();

        context.restoreAuthSystemState();
        context.commit();

        // setting the real enhancer service
        poller.setItemEnhancerService(itemEnhancerService);

        // launching the enhancement to create virtual metadata
        poller.pollItemToUpdateAndProcess();

        // restoring the mock for following tests
        poller.setItemEnhancerService(spyItemEnhancerService);

        context.commit();

        context.reloadEntity(itemA1);
        context.reloadEntity(itemA2);
        context.reloadEntity(itemB1);
        context.reloadEntity(itemB2);
        context.reloadEntity(itemC1);
        context.reloadEntity(itemC2);

        File xml = new File("research-outputs.json");
        xml.deleteOnExit();

        String[] args = new String[] { "bulk-item-export",
                "-f", "research-outputs-json",
                "-s", orgUnitId_A,
                "-c", "affinitySearch"
        };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat("The xml file should be created", xml.exists(), is(true));

        try (FileInputStream fis = new FileInputStream(xml)) {
            String content = IOUtils.toString(fis, Charset.defaultCharset());
            assertThat(content, containsString("\"unit\": \"" + orgUnitAcronym_A + "\""));
            assertThat(content, containsString("Title Item A1"));
            assertThat(content, containsString("Title Item A2"));
            assertThat(content, containsString("\"unit\": \"" + orgUnitAcronym_B + "\""));
            assertThat(content, containsString("Title Item B1"));
            assertThat(content, containsString("Title Item B2"));
            assertThat(content, containsString("\"unit\": \"" + orgUnitAcronym_C + "\""));
            assertThat(content, containsString("Title Item C1"));
            assertThat(content, containsString("Title Item C2"));
        }

    }

    private ItemBuilder createBaseOrgUnit(String acronym, Collection collection) {
        return ItemBuilder.createItem(context, collection)
                .withTitle(acronym)
                .withMetadata("oairecerif", "acronym", null, acronym);
    }

    private List<MetadataValue> getMetadataValues(Item item, String metadataField) {
        return itemService.getMetadataByMetadataString(item, metadataField);
    }

    @SuppressWarnings("rawtypes")
    private <T extends ReloadableEntity> T commitAndReload(T entity) throws SQLException, AuthorizeException {
        context.commit();
        return context.reloadEntity(entity);
    }

}
