/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.enhancer.consumer;

import static org.dspace.app.matcher.MetadataValueMatcher.with;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.sql.SQLException;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.GroupBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.ReloadableEntity;
import org.dspace.eperson.Group;
import org.junit.Before;
import org.junit.Test;

public class RelatedEntityGroupItemEnhancerConsumerIT extends AbstractIntegrationTestWithDatabase {

    private ItemService itemService;

    private Collection collection;

    @Before
    public void setup() {

        itemService = ContentServiceFactory.getInstance().getItemService();

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
    public void testWithoutExistingGroupEnhancement() throws Exception {

        context.turnOffAuthorisationSystem();

        Item person1 = ItemBuilder.createItem(context, collection)
                .withTitle("Walter White")
                .withPersonMainAffiliation("4Science")
                .build();

        String person1Id = person1.getID().toString();

        Item publication = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication")
                .withEntityType("Publication")
                .withAuthor("Jessie Pinkman", person1Id)
                .build();

        context.restoreAuthSystemState();

        List<MetadataValue> metadataValues = publication.getMetadata();

        assertThat(metadataValues, hasItem(with("cris.virtual.department", "4Science")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.department", person1Id)));

        assertThat(getMetadataValues(publication, "cris.virtual.epflgroupunits"), empty());
        assertThat(getMetadataValues(publication, "cris.virtualsource.epflgroupunits"), empty());
    }

    @Test
    public void testSingleMetadataValueEnhancement() throws Exception {

        context.turnOffAuthorisationSystem();

        Group fScience = GroupBuilder.createGroup(context)
                .withName("4Science")
                .build();

        String fScienceId = fScience.getID().toString();

        Item person1 = ItemBuilder.createItem(context, collection)
                .withTitle("Walter White")
                .withPersonMainAffiliation(fScience.getName())
                .build();

        String person1Id = person1.getID().toString();

        Item publication = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication")
                .withEntityType("Publication")
                .withAuthor("Jessie Pinkman", person1Id)
                .build();

        context.restoreAuthSystemState();

        List<MetadataValue> metadataValues = publication.getMetadata();

        assertThat(metadataValues, hasItem(with("cris.virtual.department", "4Science")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.department", person1Id)));
        assertThat(metadataValues,
                hasItem(with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 0, 600)));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person1Id, 0)));

        assertThat(getMetadataValues(publication, "cris.virtual.epflgroupunits"), hasSize(1));
        assertThat(getMetadataValues(publication, "cris.virtualsource.epflgroupunits"), hasSize(1));
    }

    @Test
    public void testAddMetadataValueEnhancement() throws Exception {

        context.turnOffAuthorisationSystem();

        Group fScience = GroupBuilder.createGroup(context)
                .withName("4Science")
                .build();
        Group fSociety = GroupBuilder.createGroup(context)
                .withName("F-Society")
                .build();

        String fScienceId = fScience.getID().toString();
        String fSocietyId = fSociety.getID().toString();

        Item person1 = ItemBuilder.createItem(context, collection)
                .withTitle("Walter White")
                .withPersonMainAffiliation(fScience.getName())
                .build();
        Item person2 = ItemBuilder.createItem(context, collection)
                .withTitle("Jessie Pinkman")
                .withPersonMainAffiliation(fScience.getName())
                .build();
        Item person3 = ItemBuilder.createItem(context, collection)
                .withTitle("Mr. Robot")
                .withPersonMainAffiliation(fSociety.getName())
                .build();

        String person1Id = person1.getID().toString();
        String person2Id = person2.getID().toString();
        String person3Id = person3.getID().toString();

        Item publication = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication")
                .withEntityType("Publication")
                .withAuthor("Walter White", person1Id)
                .build();

        context.restoreAuthSystemState();

        List<MetadataValue> metadataValues = publication.getMetadata();

        assertThat(metadataValues, hasItem(with("cris.virtual.department", "4Science")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.department", person1Id)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 0, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person1Id, 0)));

        assertThat(getMetadataValues(publication, "cris.virtual.epflgroupunits"), hasSize(1));
        assertThat(getMetadataValues(publication, "cris.virtualsource.epflgroupunits"), hasSize(1));

        context.turnOffAuthorisationSystem();
        itemService.addMetadata(context, publication, "dc", "contributor", "author",
                null, "Jessie Pinkman", person2Id, 600);
        itemService.update(context, publication);
        publication = commitAndReload(publication);
        context.restoreAuthSystemState();

        metadataValues = publication.getMetadata();

        assertThat(metadataValues, hasItem(with("cris.virtual.department", "4Science")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.department", person1Id)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 0, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person1Id, 0)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 1, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person2Id, 1)));
        assertThat(getMetadataValues(publication, "cris.virtual.epflgroupunits"), hasSize(2));
        assertThat(getMetadataValues(publication, "cris.virtualsource.epflgroupunits"), hasSize(2));

        context.turnOffAuthorisationSystem();
        itemService.addMetadata(context, publication, "dc", "contributor", "editor",
                null, "Mr. Robot", person3Id, 600);
        itemService.update(context, publication);
        publication = commitAndReload(publication);
        context.restoreAuthSystemState();

        metadataValues = publication.getMetadata();

        assertThat(metadataValues, hasItem(with("cris.virtual.department", "4Science")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.department", person1Id)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 0, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person1Id, 0)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 1, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person2Id, 1)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fSociety.getName(), fSocietyId, 2, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person3Id, 2)));

        assertThat(getMetadataValues(publication, "cris.virtual.epflgroupunits"), hasSize(3));
        assertThat(getMetadataValues(publication, "cris.virtualsource.epflgroupunits"), hasSize(3));
    }

    @Test
    public void testMultipleMetadataValueEnhancement() throws Exception {

        context.turnOffAuthorisationSystem();

        Group fScience = GroupBuilder.createGroup(context)
                .withName("4Science")
                .build();

        Group fSociety = GroupBuilder.createGroup(context)
                .withName("F-Society")
                .build();

        String fScienceId = fScience.getID().toString();
        String fSocietyId = fSociety.getID().toString();

        Item person1 = ItemBuilder.createItem(context, collection)
                .withTitle("Walter White")
                .withPersonMainAffiliation(fScience.getName())
                .build();
        Item person2 = ItemBuilder.createItem(context, collection)
                .withTitle("Mr. Robot")
                .withPersonMainAffiliation(fSociety.getName())
                .build();
        Item person3 = ItemBuilder.createItem(context, collection)
                .withTitle("Jessie Pinkman")
                .withPersonMainAffiliation(fScience.getName())
                .build();
        Item person4 = ItemBuilder.createItem(context, collection)
                .withTitle("Test")
                .withPersonMainAffiliation(fScience.getName())
                .build();

        String person1Id = person1.getID().toString();
        String person2Id = person2.getID().toString();
        String person3Id = person3.getID().toString();
        String person4Id = person4.getID().toString();

        Item publication = ItemBuilder.createItem(context, collection)
                .withTitle("Test publication")
                .withEntityType("Publication")
                .withAuthor("Jessie Pinkman", person1Id)
                .withEditor("Mr. Robot", person2Id)
                .withAuthor("Walter White", person3Id)
                .withAuthor("Test", person4Id)
                .build();

        context.restoreAuthSystemState();
        publication = commitAndReload(publication);

        List<MetadataValue> metadataValues = publication.getMetadata();

        assertThat(metadataValues, hasItem(with("cris.virtual.department", "4Science")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.department", person1Id)));
        assertThat(
            metadataValues,
            hasItem(
                with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 0, 600)
            )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person1Id, 0)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 1, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person3Id, 1)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 2, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person4Id, 2)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fSociety.getName(), fSocietyId, 3, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person2Id, 3)));

        assertThat(getMetadataValues(publication, "cris.virtual.epflgroupunits"), hasSize(4));
        assertThat(getMetadataValues(publication, "cris.virtualsource.epflgroupunits"), hasSize(4));
    }

    @Test
    public void testMultipleMetadataValueEnhancementEdit() throws Exception {

        context.turnOffAuthorisationSystem();

        Group fScience = GroupBuilder.createGroup(context)
                .withName("4Science")
                .build();

        Group fSociety = GroupBuilder.createGroup(context)
                .withName("F-Society")
                .build();

        String fScienceId = fScience.getID().toString();
        String fSocietyId = fSociety.getID().toString();

        Item person1 = ItemBuilder.createItem(context, collection)
            .withTitle("Walter White")
            .withPersonMainAffiliation(fScience.getName())
            .build();
        Item person2 = ItemBuilder.createItem(context, collection)
            .withTitle("Mr. Robot")
            .withPersonMainAffiliation(fSociety.getName())
            .build();
        Item person3 = ItemBuilder.createItem(context, collection)
            .withTitle("Jessie Pinkman")
            .withPersonMainAffiliation(fScience.getName())
            .build();

        String person1Id = person1.getID().toString();
        String person2Id = person2.getID().toString();
        String person3Id = person3.getID().toString();

        Item publication = ItemBuilder.createItem(context, collection)
            .withTitle("Test publication")
            .withEntityType("Publication")
            .withAuthor("Jessie Pinkman", person1Id)
            .withEditor("Mr. Robot", person2Id)
            .withAuthor("Walter White", person3Id)
            .build();

        context.restoreAuthSystemState();
        publication = commitAndReload(publication);

        List<MetadataValue> metadataValues = publication.getMetadata();

        assertThat(metadataValues, hasItem(with("cris.virtual.department", "4Science")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.department", person1Id)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 0, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person1Id, 0)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 1, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person3Id, 1)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fSociety.getName(), fSocietyId, 2, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person2Id, 2)));

        assertThat(getMetadataValues(publication, "cris.virtual.epflgroupunits"), hasSize(3));
        assertThat(getMetadataValues(publication, "cris.virtualsource.epflgroupunits"), hasSize(3));

        MetadataValue authorToRemove = getMetadataValues(publication, "dc.contributor.author").get(1);

        context.turnOffAuthorisationSystem();
        itemService.removeMetadataValues(context, publication, List.of(authorToRemove));
        itemService.update(context, publication);
        context.restoreAuthSystemState();
        publication = commitAndReload(publication);

        metadataValues = publication.getMetadata();

        assertThat(metadataValues, hasItem(with("cris.virtual.department", "4Science")));
        assertThat(metadataValues, hasItem(with("cris.virtualsource.department", person1Id)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 0, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person1Id, 0)));
        assertThat(
                metadataValues,
                hasItem(
                    with("cris.virtual.epflgroupunits", fSociety.getName(), fSocietyId, 1, 600)
                )
        );
        assertThat(metadataValues, hasItem(with("cris.virtualsource.epflgroupunits", person2Id, 1)));
        assertThat(
                metadataValues,
                not(
                    hasItem(
                        with("cris.virtual.epflgroupunits", fScience.getName(), fScienceId, 1, 600)
                    )
                )
        );
        assertThat(metadataValues, not(hasItem(with("cris.virtualsource.epflgroupunits", person3Id, 1))));

        assertThat(getMetadataValues(publication, "cris.virtual.epflgroupunits"), hasSize(2));
        assertThat(getMetadataValues(publication, "cris.virtualsource.epflgroupunits"), hasSize(2));

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
