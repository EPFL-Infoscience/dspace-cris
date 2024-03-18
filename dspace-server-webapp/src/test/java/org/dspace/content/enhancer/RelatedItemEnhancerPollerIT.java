/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.enhancer;

import static org.dspace.app.matcher.MetadataValueMatcher.with;
import static org.dspace.app.matcher.MetadataValueMatcher.withNoPlace;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.sql.SQLException;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.matcher.CustomItemMatcher;
import org.dspace.authority.service.AuthorityValueService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.enhancer.service.ItemEnhancerService;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.ReloadableEntity;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;
import org.junit.Before;
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
        assertThat(metadataValues, hasSize(22));
        assertThat(metadataValues, hasItem(with("cris.virtual.department", "4Science")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.department", personId)));
        assertThat(metadataValues, hasItem(with("cris.virtual.orcid", PLACEHOLDER_PARENT_METADATA_VALUE)));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.orcid", personId)));
        List<MetadataValue> metadataValues2 = publication2.getMetadata();
        assertThat(metadataValues2, hasSize(36));
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
        assertThat(metadataValues3, hasSize(39));
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
        assertThat(metadataValues, hasSize(26));
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
        assertThat(metadataValues2, hasSize(40));
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
        assertThat(metadataValues3, hasSize(39));
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
        assertThat(metadataValues, hasSize(26));
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
        assertThat(metadataValues2, hasSize(40));
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
        assertThat(metadataValues3, hasSize(35));
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

    private List<MetadataValue> getMetadataValues(Item item, String metadataField) {
        return itemService.getMetadataByMetadataString(item, metadataField);
    }

    @SuppressWarnings("rawtypes")
    private <T extends ReloadableEntity> T commitAndReload(T entity) throws SQLException, AuthorizeException {
        context.commit();
        return context.reloadEntity(entity);
    }

}
