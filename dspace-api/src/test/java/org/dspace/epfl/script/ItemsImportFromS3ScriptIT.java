/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static org.dspace.builder.CollectionBuilder.createCollection;
import static org.dspace.builder.CommunityBuilder.createCommunity;
import static org.dspace.epfl.script.ItemsImportFromS3Script.COLLECTION_PROPERTY_PREFIX;
import static org.dspace.epfl.script.service.impl.MarcXmlParserImpl.TYPE_FILTER_PROPERTY_PREFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.authority.factory.ContentAuthorityServiceFactory;
import org.dspace.content.authority.service.ChoiceAuthorityService;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.epfl.script.service.ItemsS3Service;
import org.dspace.epfl.script.service.impl.MarcXmlParserImpl;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

public class ItemsImportFromS3ScriptIT extends AbstractIntegrationTestWithDatabase {

    private ConfigurationService configurationService;

    private Community community;

    private Collection collection;

    private ItemsImportFromS3Script itemsImportFromS3Script;

    private ItemsS3Service itemsS3Service;

    private ItemService itemService;

    private BitstreamService bitstreamService;

    private MarcXmlParserImpl marcXmlParser;

    private ChoiceAuthorityService choiceAuthorityService;

    @Before
    public void beforeTests() throws SQLException, AuthorizeException {

        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

        itemService = ContentServiceFactory.getInstance().getItemService();

        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

        context.turnOffAuthorisationSystem();
        community = createCommunity(context).build();
        collection = createCollection(context, community)
            .withEntityType("Publication")
            .build();
        context.restoreAuthSystemState();
        context.commit();

        readAllTypes().forEach(type -> setCollectionProperty(type));

        choiceAuthorityService = ContentAuthorityServiceFactory
                .getInstance().getChoiceAuthorityService();
        itemsImportFromS3Script = new ItemsImportFromS3Script();
        itemsS3Service = mock(ItemsS3Service.class);
        marcXmlParser = new MarcXmlParserImpl();
        marcXmlParser.setItemsS3Service(itemsS3Service);
        marcXmlParser.setChoiceAuthorityService(choiceAuthorityService);
        marcXmlParser.setConfigurationService(configurationService);
        marcXmlParser.runSetup();
    }

    @Ignore
    @Test
    public void testPublicationImportMIMEType() throws Exception {

        String key = "167656.zip";

        deleteAllFilesOnExit();

        String[] args = new String[] { "items-import-from-s3", "-k", key };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        when(itemsS3Service.getObject(ArgumentMatchers.any())).thenReturn(getZipResource(key));
        when(itemsS3Service.getCreationDate(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        itemsImportFromS3Script.initialize(args, handler, admin);
        itemsImportFromS3Script.setItemsS3Service(itemsS3Service);
        itemsImportFromS3Script.run();

        // TODO we can use itemService to retrieve all items, it only needs to be one
        UUID importedItemUUID = UUID.randomUUID();
        Bitstream importedBitstream = itemService.find(context, importedItemUUID)
                                                 .getBundles("ORIGINAL")
                                                 .get(0).getBitstreams().get(0);

        BitstreamFormat bitstreamFormat = bitstreamService.getFormat(context, importedBitstream);

        assertEquals(bitstreamFormat.getMIMEType(), "application/pdf");
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
    }

    private void deleteAllFilesOnExit() {
        for (String type : readAllTypes()) {
            File file = new File(type + ".xls");
            file.deleteOnExit();
        }
    }

    private List<String> readAllTypes() {
        return configurationService.getPropertyKeys(TYPE_FILTER_PROPERTY_PREFIX).stream()
            .map(propertyKey -> StringUtils.removeStart(propertyKey, TYPE_FILTER_PROPERTY_PREFIX + "."))
            .collect(Collectors.toList());
    }

    private void setCollectionProperty(String type) {
        configurationService.setProperty(COLLECTION_PROPERTY_PREFIX + "." + type, collection.getID().toString());
    }

    private File getZipResource(String key) throws URISyntaxException {
        URL zipUrl = this.getClass().getResource("s3/" + key);
        return new File(zipUrl.toURI());
    }

}
