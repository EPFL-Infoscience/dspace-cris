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

    List<IdentifierProvider> originalProviders;

    IdentifierServiceImpl identifierService;
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        configurationService.setProperty(DOIIdentifierProvider.CFG_PREFIX, PREFIX);
        configurationService.setProperty(DOIIdentifierProvider.CFG_NAMESPACE_SEPARATOR,
                           "");
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
                .withDoiIdentifier("doi:10.9999/publication")
                .withPublisher("A Publisher")
                .withWrittenAt("Not at EPFL")
                .withType("thesis::doctoral thesis", "thesis-coar-types:c_46ec")
                .build();
        Item itemWithPreviousEPFLDOI = ItemBuilder.createItem(context, col)
                .withTitle("itemWithPreviousEPFLDOI")
                .withDoiIdentifier("doi:10.5072/epfl-thesis-old-doi")
                .withPublisher("School of XXX")
                .withWrittenAt("EPFL")
                .withType("thèses::thèse de doctorat", "thesis-coar-types:c_46ec")
                .build();
        Item newItemThatShouldGetDOI = ItemBuilder.createItem(context, col)
                .withTitle("newItemThatShouldGetDOI")
                .withPublisher("School of YYYY")
                .withWrittenAt("EPFL")
                .withType("thesis::doctoral thesis", "thesis-coar-types:c_46ec")
                .build();
        Item itemThatShouldNotGetADOI = ItemBuilder.createItem(context, col)
                .withTitle("itemThatShouldNotGetADOI")
                .withPublisher("School of XXX")
                .withWrittenAt("EPFL")
                .withType("other")
                .build();
        context.restoreAuthSystemState();

        assertThat(identifierService.lookup(context, itemWithPublisherDOI, DOI.class),
                Matchers.isEmptyOrNullString());
        assertThat(identifierService.lookup(context, itemWithPreviousEPFLDOI, DOI.class),
                Matchers.equalTo("doi:10.5072/epfl-thesis-old-doi"));
        assertThat(identifierService.lookup(context, newItemThatShouldGetDOI, DOI.class),
                Matchers.startsWith("doi:10.5072/"));
        assertThat(identifierService.lookup(context, itemThatShouldNotGetADOI, DOI.class),
                Matchers.isEmptyOrNullString());

    }

    private String getFirstDOIMetadata(Item newItemThatShouldGetDOI) {
        return itemService.getMetadataFirstValue(newItemThatShouldGetDOI, "dc", "identifier", "doi", Item.ANY);
    }

}
