/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier;

import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.identifier.factory.IdentifierServiceFactory;
import org.dspace.identifier.generators.FixedConfigurationValueNamespaceGenerator;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

/**
 * This class aims to test the minting DOI feature under the specific configuration of EPFL
 */
public class EpflMintDOIIT extends AbstractIntegrationTestWithDatabase {
    private static final String PREFIX = "10.5072";

    ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    protected FixedConfigurationValueNamespaceGenerator fixedNsGenerator = new DSpace().getServiceManager()
            .getServiceByName("defaultValueNamespace", FixedConfigurationValueNamespaceGenerator.class);

    List<IdentifierProvider> originalProviders;

    IdentifierServiceImpl identifierService;
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        configurationService.setProperty(DOIIdentifierProvider.CFG_PREFIX, PREFIX);
        configurationService.setProperty(DOIIdentifierProvider.CFG_NAMESPACE_SEPARATOR,
                           "");
        fixedNsGenerator.setConfigurationValue(
                configurationService.getProperty(DOIIdentifierProvider.CFG_NAMESPACE_SEPARATOR));
        identifierService = (IdentifierServiceImpl) IdentifierServiceFactory.getInstance().getIdentifierService();
        originalProviders = identifierService.getProviders();
        List<IdentifierProvider> epflProviders = new ArrayList<IdentifierProvider>();
        originalProviders.stream().filter(p -> !p.supports(DOI.class)).forEach(epflProviders::add);
        epflProviders.add(new DSpace().getServiceManager().getServiceByName("epfl-doi-provider",
                VersionedDOIIdentifierProvider.class));
        identifierService.setProviders(epflProviders);
    }

    @Override
    public void destroy() throws Exception {
        super.destroy();
        identifierService.setProviders(originalProviders);
    }

    @Test
    public void mintDOIProcessRespectDOIFilterTest() throws Exception {
        context.turnOffAuthorisationSystem();
        Community community = CommunityBuilder.createCommunity(context)
                                              .withName("Parent Community")
                                              .build();
        Collection col = CollectionBuilder.createCollection(context, community)
                                          .withName("Collection")
                                          .withEntityType("Publication")
                                          .build();

        Item itemWithPublisherDOI = ItemBuilder.createItem(context, col)
                .withTitle("itemWithPublisherDOI")
                .withLegacyId("1")
                .withDoiIdentifier("doi:10.9999/publication")
                .withPublisher("A Publisher")
                .withWrittenAt("Not at EPFL")
                .withType("thesis::doctoral thesis", "thesis-coar-types:c_db06")
                .build();
        Item itemWithPreviousEPFLHttpDOI = ItemBuilder.createItem(context, col)
                .withTitle("itemWithPreviousHttpEPFLDOI")
                .withLegacyId("2")
                .withDoiIdentifier("https://doi.org/10.5072/epfl-thesis-old-http-doi")
                .withPublisher("School of XXX")
                .withWrittenAt("EPFL")
                .withType("thèses::thèse de doctorat", "thesis-coar-types:c_db06")
                .build();
        Item itemWithPreviousEPFLDOI = ItemBuilder.createItem(context, col)
                .withTitle("itemWithPreviousEPFLDOI")
                .withLegacyId("3")
                .withDoiIdentifier("doi:10.5072/epfl-thesis-old-doi")
                .withPublisher("School of XXX")
                .withWrittenAt("EPFL")
                .withType("thèses::thèse de doctorat", "thesis-coar-types:c_db06")
                .build();
        Item itemWithPreviousEPFLPlainDOI = ItemBuilder.createItem(context, col)
                .withTitle("itemWithPreviousPlainEPFLDOI")
                .withLegacyId("4")
                .withDoiIdentifier("10.5072/epfl-thesis-old-plain-doi")
                .withPublisher("School of XXX")
                .withWrittenAt("EPFL")
                .withType("thèses::thèse de doctorat", "thesis-coar-types:c_db06")
                .build();
        Item newItemThatShouldGetDOI = ItemBuilder.createItem(context, col)
                .withTitle("newItemThatShouldGetDOI")
                .withLegacyId("5")
                .withPublisher("School of YYYY")
                .withWrittenAt("EPFL")
                .withType("thesis::doctoral thesis", "thesis-coar-types:c_db06")
                .build();
        Item itemThatShouldNotGetADOI = ItemBuilder.createItem(context, col)
                .withTitle("itemThatShouldNotGetADOI")
                .withPublisher("School of XXX")
                .withWrittenAt("EPFL")
                .withType("thesis::doctoral thesis", "thesis-coar-types:c_db06")
                //.withType("other")
                .build();
        context.restoreAuthSystemState();

        assertThat(identifierService.lookup(context, itemWithPublisherDOI, DOI.class),
                Matchers.isEmptyOrNullString());
        assertThat(identifierService.lookup(context, itemWithPreviousEPFLDOI, DOI.class),
                Matchers.equalTo("doi:10.5072/epfl-thesis-old-doi"));
        assertThat(identifierService.lookup(context, itemWithPreviousEPFLHttpDOI, DOI.class),
                Matchers.equalTo("doi:10.5072/epfl-thesis-old-http-doi"));
        assertThat(identifierService.lookup(context, itemWithPreviousEPFLPlainDOI, DOI.class),
                Matchers.equalTo("doi:10.5072/epfl-thesis-old-plain-doi"));
        assertThat(identifierService.lookup(context, newItemThatShouldGetDOI, DOI.class),
                Matchers.startsWith("doi:10.5072/"));
        assertThat(identifierService.lookup(context, itemThatShouldNotGetADOI, DOI.class),
                Matchers.isEmptyOrNullString());
        assertThat(runDSpaceScript("doi-organiser", "-l"), Matchers.is(0));
        assertThat(runDSpaceScript("doi-organiser", "-u"), Matchers.is(0));
        assertThat(runDSpaceScript("doi-organiser", "-r"), Matchers.is(0));
        context.turnOffAuthorisationSystem();
        itemWithPreviousEPFLHttpDOI = context.reloadEntity(itemWithPreviousEPFLHttpDOI);
        itemWithPreviousEPFLPlainDOI = context.reloadEntity(itemWithPreviousEPFLPlainDOI);
        itemWithPreviousEPFLDOI = context.reloadEntity(itemWithPreviousEPFLDOI);
        newItemThatShouldGetDOI = context.reloadEntity(newItemThatShouldGetDOI);
        itemService.addMetadata(context,
                itemWithPreviousEPFLHttpDOI, "dc", "subject", null, null, "to trigger an update");
        itemService.update(context, itemWithPreviousEPFLHttpDOI);
        itemService.addMetadata(context,
                itemWithPreviousEPFLPlainDOI, "dc", "subject", null, null, "to trigger an update");
        itemService.update(context, itemWithPreviousEPFLPlainDOI);
        itemService.addMetadata(context, itemWithPreviousEPFLDOI, "dc", "subject", null, null, "to trigger an update");
        itemService.update(context, itemWithPreviousEPFLDOI);
        itemService.addMetadata(context, newItemThatShouldGetDOI, "dc", "subject", null, null, "to trigger an update");
        itemService.update(context, newItemThatShouldGetDOI);
        context.commit();
        assertThat(runDSpaceScript("doi-organiser", "-u"), Matchers.is(0));
        itemWithPreviousEPFLHttpDOI = context.reloadEntity(itemWithPreviousEPFLHttpDOI);
        itemWithPreviousEPFLPlainDOI = context.reloadEntity(itemWithPreviousEPFLPlainDOI);
        itemWithPreviousEPFLDOI = context.reloadEntity(itemWithPreviousEPFLDOI);
        newItemThatShouldGetDOI = context.reloadEntity(newItemThatShouldGetDOI);
        assertThat(getFirstDOIMetadata(itemWithPreviousEPFLDOI),
                Matchers.equalTo("doi:10.5072/epfl-thesis-old-doi"));
        assertThat(getFirstDOIMetadata(itemWithPreviousEPFLHttpDOI),
                Matchers.equalTo("https://doi.org/10.5072/epfl-thesis-old-http-doi"));
        assertThat(getFirstDOIMetadata(itemWithPreviousEPFLPlainDOI),
                Matchers.equalTo("10.5072/epfl-thesis-old-plain-doi"));
        assertThat(getFirstDOIMetadata(newItemThatShouldGetDOI),
                Matchers.startsWith("https://doi.org/10.5072/"));
        assertThat(getNumDOIMetadata(itemWithPreviousEPFLDOI), Matchers.is(1));
        assertThat(getNumDOIMetadata(itemWithPreviousEPFLHttpDOI), Matchers.is(1));
        assertThat(getNumDOIMetadata(itemWithPreviousEPFLPlainDOI), Matchers.is(1));
        assertThat(getNumDOIMetadata(newItemThatShouldGetDOI), Matchers.is(1));
        context.restoreAuthSystemState();
        assertThat(runDSpaceScript("doi-organiser", "-l"), Matchers.is(0));
    }

    private String getFirstDOIMetadata(Item newItemThatShouldGetDOI) {
        return itemService.getMetadataFirstValue(newItemThatShouldGetDOI, "dc", "identifier", "doi", Item.ANY);
    }

    private int getNumDOIMetadata(Item newItemThatShouldGetDOI) {
        return itemService.getMetadata(newItemThatShouldGetDOI, "dc", "identifier", "doi", Item.ANY).size();
    }

}
