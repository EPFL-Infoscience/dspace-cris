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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.epfl.script.service.ItemsS3Service;
import org.dspace.epfl.script.service.impl.MarcXmlParserImpl;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Test class for ItemsImportFromS3Script
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk@4science.com )
 */
public class ItemsImportFromS3ScriptIT extends AbstractIntegrationTestWithDatabase {

    private ItemService itemService;
    private BitstreamService bitstreamService;
    private ConfigurationService configurationService;

    private Community community;
    private Collection collection;

    @Before
    public void beforeTests() throws SQLException, AuthorizeException {
        itemService = ContentServiceFactory.getInstance().getItemService();
        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

        context.turnOffAuthorisationSystem();
        community = createCommunity(context).build();
        collection = createCollection(context, community).withEntityType("Publication")
                                                         .build();
        context.restoreAuthSystemState();
        context.commit();
        readAllTypes().forEach(type -> setCollectionProperty(type));
    }

    @Test
    public void testPublicationImportMIMEType() throws Exception {
        String key = "167656.zip";

        deleteAllFilesOnExit();
        MarcXmlParserImpl marcXmlParserImpl = null;
        ItemsS3Service originalS3serviceOfMarcXmlParserImpl = null;
        ItemsImportFromS3Script itemsImportFromS3Script = new ItemsImportFromS3Script();
        try {
            String[] args = new String[] { "items-import-from-s3", "-k", key };
            TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
            itemsImportFromS3Script.initialize(args, handler, admin);
            marcXmlParserImpl = (MarcXmlParserImpl) itemsImportFromS3Script.getMarcXmlParser();
            originalS3serviceOfMarcXmlParserImpl = marcXmlParserImpl.getItemsS3Service();
            ItemsS3Service itemsS3ServiceMock = spy(originalS3serviceOfMarcXmlParserImpl);
            itemsImportFromS3Script.setItemsS3Service(itemsS3ServiceMock);
            marcXmlParserImpl.setItemsS3Service(itemsS3ServiceMock);

            doReturn(getZipResource(key)).when(itemsS3ServiceMock).getObject(ArgumentMatchers.any());
            doReturn(null).when(itemsS3ServiceMock).getCreationDate(ArgumentMatchers.any(), ArgumentMatchers.any());

            itemsImportFromS3Script.run();

            Iterator<Item> items = itemService.findAll(context);
            assertTrue(items.hasNext());
            Item importedItem = items.next();
            assertFalse(items.hasNext());

            Bitstream importedBitstream = itemService.find(context, importedItem.getID())
                                                     .getBundles("ORIGINAL")
                                                     .get(0).getBitstreams().get(0);

            BitstreamFormat bitstreamFormat = bitstreamService.getFormat(context, importedBitstream);
            assertEquals("2023-05-05T23:51:05Z", itemsS3ServiceMock.getModificationDate(context, "167656"));
            assertEquals(bitstreamFormat.getMIMEType(), "application/pdf");
            assertThat(handler.getErrorMessages(), empty());
            assertThat(handler.getWarningMessages(), empty());
        } finally {
            if (originalS3serviceOfMarcXmlParserImpl != null) {
               marcXmlParserImpl.setItemsS3Service(originalS3serviceOfMarcXmlParserImpl);
               originalS3serviceOfMarcXmlParserImpl.deleteModificationDate(context, "167656");
            }
        }
    }

    @Test
    public void importAnItemFromS3ScriptTest() throws Exception {
        String key = "79707.zip";

        MarcXmlParserImpl marcXmlParserImpl = null;
        ItemsS3Service originalS3serviceOfMarcXmlParserImpl = null;
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        ItemsImportFromS3Script importFromS3Script = new ItemsImportFromS3Script();
        try {
            String[] args = new String[] { "items-import-from-s3", "-k", key };
            importFromS3Script.initialize(args, handler, admin);
            marcXmlParserImpl = (MarcXmlParserImpl) importFromS3Script.getMarcXmlParser();
            originalS3serviceOfMarcXmlParserImpl = marcXmlParserImpl.getItemsS3Service();
            ItemsS3Service itemsS3ServiceMock = spy(originalS3serviceOfMarcXmlParserImpl);
            importFromS3Script.setItemsS3Service(itemsS3ServiceMock);
            marcXmlParserImpl.setItemsS3Service(itemsS3ServiceMock);


            doReturn(getZipResource(key)).when(itemsS3ServiceMock).getObject(ArgumentMatchers.any());
            doReturn("2006-02-21T14:39:08").when(itemsS3ServiceMock).getCreationDate(ArgumentMatchers.any(),
                    ArgumentMatchers.any());
            importFromS3Script.run();
            Iterator<Item> items = itemService.findAll(context);
            assertTrue("We must have at least 1 item", items.hasNext());

            Item importedItem = items.next();
            List<MetadataValue> actualMetadata = importedItem.getMetadata();
            List<MetadataValueDTO> expectedMetadata = getMetadataThatShouldBePresentIntoImportedItem();
            checkMetadata(expectedMetadata, actualMetadata);
            assertEquals("2023-05-05T18:59:01Z", itemsS3ServiceMock.getModificationDate(context, "79707"));
            assertEquals(62, actualMetadata.size());
            assertFalse("check that there are no other items", items.hasNext());
        } finally {
            if (originalS3serviceOfMarcXmlParserImpl != null) {
               marcXmlParserImpl.setItemsS3Service(originalS3serviceOfMarcXmlParserImpl);
               originalS3serviceOfMarcXmlParserImpl.deleteModificationDate(context, "790707");
            }
        }
        //TODO this test could be improved by checking virtual metadata
        // for this you have to create items of type Person
    }

    private void checkMetadata(List<MetadataValueDTO> listOfexpectedMetadata,List<MetadataValue> listOfActualMetadata) {
        for (MetadataValue actualMetadata : listOfActualMetadata) {
            String actualMetadataField = actualMetadata.getMetadataField().toString().replaceAll("_", ".");
            if (itMustBeSkipped(actualMetadataField)) {
                continue;
            }
            String actualMetadataValue = actualMetadata.getValue();
            boolean isPresent = listOfexpectedMetadata.stream()
                                                      .filter(mv ->
                                                              mv.getMetadataField().equals(actualMetadataField) &&
                                                              mv.getValue().equals(actualMetadataValue))
                                                      .findFirst()
                                                      .isPresent();
            assertTrue("The metadata field: " + actualMetadataField + " with value: " + actualMetadataValue +
                       " must be present in the imported item!", isPresent);
        }
    }

    private boolean itMustBeSkipped(String actualMetadataField) {
        Set<String> metadataToSkip = metadataToSkip();
        return metadataToSkip.contains(actualMetadataField);
    }

    private Set<String> metadataToSkip() {
        Set<String> metadataToSkip = new HashSet<>();
        metadataToSkip.add("dc.date.modified");
        metadataToSkip.add("dc.identifier.uri");
        metadataToSkip.add("dc.description.provenance");
        return metadataToSkip;
    }

    private List<MetadataValueDTO> getMetadataThatShouldBePresentIntoImportedItem() {
        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();
        // 14 authors
        MetadataValueDTO author1 = new MetadataValueDTO("dc", "contributor", "author",null, "Nazeeruddin Mohammad, K.");
        MetadataValueDTO author2 = new MetadataValueDTO("dc", "contributor", "author", null, "Wang, Qing");
        MetadataValueDTO author3 = new MetadataValueDTO("dc", "contributor", "author", null, "Cevey, Le");
        MetadataValueDTO author4 = new MetadataValueDTO("dc", "contributor", "author", null, "Aranyos, Viviane");
        MetadataValueDTO author5 = new MetadataValueDTO("dc", "contributor", "author", null, "Liska, Paul");
        MetadataValueDTO author6 = new MetadataValueDTO("dc", "contributor", "author", null, "Figgemeier, Egbert");
        MetadataValueDTO author7 = new MetadataValueDTO("dc", "contributor", "author", null, "Klein, Cedric");
        MetadataValueDTO author8 = new MetadataValueDTO("dc", "contributor", "author", null, "Hirata, Narukuni");
        MetadataValueDTO author9 = new MetadataValueDTO("dc", "contributor", "author", null, "Koops, Sara");
        MetadataValueDTO author10 = new MetadataValueDTO("dc", "contributor", "author", null, "Haque Saif, A.");
        MetadataValueDTO author11 = new MetadataValueDTO("dc", "contributor", "author", null, "Durrant James, R.");
        MetadataValueDTO author12 = new MetadataValueDTO("dc", "contributor", "author", null, "Hagfeldt, Anders");
        MetadataValueDTO author13 = new MetadataValueDTO("dc", "contributor", "author", null, "Lever, A. B. P.");
        MetadataValueDTO author14 = new MetadataValueDTO("dc", "contributor", "author", null, "Gratzel, Michael");
        metadataValues.addAll(Arrays.asList(author1, author2, author3, author4, author5, author6, author7, author8,
                                            author9, author10, author11, author12, author13, author14));
        // date
        MetadataValueDTO dateAccessioned = new MetadataValueDTO("dc","date","accessioned", null, "2006-02-21T14:39:08");
        MetadataValueDTO dateAvailable = new MetadataValueDTO("dc", "date", "available", null, "2006-02-21T14:39:08");
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        MetadataValueDTO dateCreated1 = new MetadataValueDTO("dc", "date", "created", null,
                                                             currentDate.format(formatter));
        MetadataValueDTO dateCreated2 = new MetadataValueDTO("dc", "date", "created", null, "2006-02-21");
        MetadataValueDTO dateIssued = new MetadataValueDTO("dc", "date", "issued", null, "2006");
        metadataValues.addAll(Arrays.asList(dateAccessioned, dateAvailable, dateCreated1, dateCreated2, dateIssued));

        // identifiers
        MetadataValueDTO identifierDoi = new MetadataValueDTO("dc", "identifier", "doi", null, "10.1021/ic051727x");
        MetadataValueDTO identifierIsi = new MetadataValueDTO("dc", "identifier", "isi", null, "WOS:000234905200045");
        MetadataValueDTO identifierDar = new MetadataValueDTO("dc", "identifier", "dar", null, "8067");
        metadataValues.addAll(Arrays.asList(identifierDoi, identifierIsi, identifierDar));

        // description
        var descriptionAbstractValue = "A new Ru(II) complex, Bu4N"
            + " [ruthenium (4-carboxylic acid-4'-carboxylate-2,2'-bipyridine)(4,4'-di(2-(3,6-dimethoxyphenyl)"
            + "ethenyl)-2,2'-bipyridine)(NCS)2] (N945H), was synthesized and characterized by anal., spectroscopic,"
            + " and electrochem. techniques. The absorption spectrum of the N945H sensitizer is dominated by"
            + " metal-to-ligand charge-transfer (MLCT) transitions in the visible region, with the lowest allowed"
            + " MLCT bands appearing at 25,380 and 18,180 cm-1. The molar absorptivities of these bands are"
            + " 34,500 and 18,900 M-1 cm-1, resp., and are significantly higher when compared to than those of"
            + " the std. sensitizer cis-dithiocyanatobis(4,4'-dicarboxylic acid-2,2'-bipyridine)ruthenium(II)."
            + " An INDO/S and DFT study of the electronic and optical properties of N945H and of N945 adsorbed"
            + " on TiO2 was performed. The calcns. point out that the top 3 frontier-filled orbitals have a Ru 4d"
            + " (t2g in the octahedral group) character with a contribution coming from the NCS ligand orbitals."
            + " The calcns. also reveal that in the TiO2-bound N945 sensitizer, excitation directs charge into the"
            + " carboxylbipyridine ligand bound to the TiO2 surface. The photovoltaic data of the N945 sensitizer"
            + " using an electrolyte contg. 0.60M butylmethylimidazolium iodide, 0.03M I2, 0.10M guanidinium"
            + " thiocyanate, and 0.50M tert-butylpyridine in a mixt. of MeCN and valeronitrile (vol. ratio = 85:15)"
            + " had a short-circuit photocurrent d. of 16.50 ± 0.2 mA/cm2, an open-circuit voltage of 790 ± 30 mV,"
            + " and a fill factor of 0.72 ± 0.03. This corresponds to an overall conversion efficiency of 9.6% under"
            + " std. AM 1.5 sunlight and stable performance under light and heat soaking at 80° was confirmed.";
        MetadataValueDTO descriptionAbstract = new MetadataValueDTO("dc", "description", "abstract", null,
                                                                    descriptionAbstractValue);
        MetadataValueDTO sponsorship1 = new MetadataValueDTO("dc", "description", "sponsorship", null, "LPI");
        MetadataValueDTO sponsorship2 = new MetadataValueDTO("dc", "description", "sponsorship", null, "LSPM");
        metadataValues.addAll(Arrays.asList(descriptionAbstract, sponsorship1, sponsorship2));

        // other
        MetadataValueDTO relationJournal = new MetadataValueDTO("dc", "relation", "journal", null,
                                               "Inorganic chemistry");
        MetadataValueDTO subject = new MetadataValueDTO("dc", "subject", null, null,
                                       "ruthenium charge transfer complex solar cell sensitizer DFT model");
        MetadataValueDTO title = new MetadataValueDTO("dc", "title", null, null,
                                     "DFT-INDO/S modeling of new high molar extinction coefficient" +
                                     " charge-transfer sensitizers for solar cell applications");
        MetadataValueDTO type = new MetadataValueDTO("dc", "type", null, null,
                                    "text::journal::journal article::research article");
        MetadataValueDTO legacyId = new MetadataValueDTO("cris", "legacyId", null, null, "79707");
        metadataValues.addAll(Arrays.asList(relationJournal, subject, title, type, legacyId));

        // oaire citation
        MetadataValueDTO volume = new MetadataValueDTO("oaire", "citation", "volume", null, "45");
        MetadataValueDTO issue = new MetadataValueDTO("oaire", "citation", "issue", null, "2");
        MetadataValueDTO startPage = new MetadataValueDTO("oaire", "citation", "startPage", null, "787");
        MetadataValueDTO endPage = new MetadataValueDTO("oaire", "citation", "endPage", null, "797");
        metadataValues.addAll(Arrays.asList(volume, issue, startPage, endPage));

        //dspace metadata
        MetadataValueDTO dataciteRights = new MetadataValueDTO("datacite", "rights", null, null, "metadata-only");
        MetadataValueDTO entityType = new MetadataValueDTO("dspace", "entity", "type", null, "Publication");
        MetadataValueDTO oaiIdentifier = new MetadataValueDTO("dspace", "legacy", "oai-identifier", null,
                                                              "oai:infoscience.tind.io:79707");
        metadataValues.addAll(Arrays.asList(dataciteRights, entityType, oaiIdentifier));

        // epfl
        MetadataValueDTO currentset1 = new MetadataValueDTO("epfl", "oai", "currentset", null, "SB");
        MetadataValueDTO currentset2 = new MetadataValueDTO("epfl", "oai", "currentset", null, "OpenAIREv4");
        MetadataValueDTO currentset3 = new MetadataValueDTO("epfl", "oai", "currentset", null, "article");
        MetadataValueDTO writtenat = new MetadataValueDTO("epfl", "writtenat", null, null, "EPFL");
        MetadataValueDTO peerreviewed = new MetadataValueDTO("epfl", "peerreviewed", null, null, "REVIEWED");
        MetadataValueDTO version = new MetadataValueDTO("epfl", "publication", "version", null,
                                                        "http://purl.org/coar/version/c_970fb48d4fbd8a85");
        MetadataValueDTO submissionform = new MetadataValueDTO("epfl", "legacy", "submissionform", null, "ARTICLE");
        MetadataValueDTO itemtype = new MetadataValueDTO("epfl", "legacy", "itemtype", null, "Journal Articles");
        metadataValues.addAll(Arrays.asList(currentset1, currentset2, currentset3, writtenat, peerreviewed,
                                            version, submissionform, itemtype));
        // epfl legacy
        MetadataValueDTO contributorauthnum1 = new MetadataValueDTO("epfl","legacy","contributorauthnum",null,"240422");
        MetadataValueDTO contributorauthnum2 = new MetadataValueDTO("epfl","legacy","contributorauthnum",null,"240191");
        MetadataValueDTO contributorauthnum3 = new MetadataValueDTO("epfl","legacy","contributorauthnum",null,"240949");
        MetadataValueDTO contributorauthnum4 = new MetadataValueDTO("epfl","legacy","contributorauthnum",null,"240733");
        MetadataValueDTO contributorauthnum5 = new MetadataValueDTO("epfl","legacy","contributorauthnum",null,"240597");
        MetadataValueDTO contributorauthnum6 = new MetadataValueDTO("epfl","legacy","contributorauthnum",null,"248439");
        MetadataValueDTO contributorauthnum7 = new MetadataValueDTO("epfl","legacy","contributorauthnum",null,"240191");
        MetadataValueDTO contributorauthnum8 = new MetadataValueDTO("epfl","legacy","contributorauthnum",null,
                                                                    "#PLACEHOLDER_PARENT_METADATA_VALUE#");
        metadataValues.addAll(Arrays.asList(contributorauthnum1, contributorauthnum2, contributorauthnum3,
                                            contributorauthnum4, contributorauthnum5, contributorauthnum6,
                                            contributorauthnum7, contributorauthnum8));
        return metadataValues;
    }

    @Test
    public void testPublicationImportBitstreamOIRELicense() throws Exception {
        String key = "217849.zip";

        deleteAllFilesOnExit();
        MarcXmlParserImpl marcXmlParserImpl = null;
        ItemsS3Service originalS3serviceOfMarcXmlParserImpl = null;
        ItemsImportFromS3Script itemsImportFromS3Script = new ItemsImportFromS3Script();
        try {
            String[] args = new String[] { "items-import-from-s3", "-k", key };
            TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
            itemsImportFromS3Script.initialize(args, handler, admin);
            marcXmlParserImpl = (MarcXmlParserImpl) itemsImportFromS3Script.getMarcXmlParser();
            originalS3serviceOfMarcXmlParserImpl = marcXmlParserImpl.getItemsS3Service();
            ItemsS3Service itemsS3ServiceMock = spy(originalS3serviceOfMarcXmlParserImpl);
            itemsImportFromS3Script.setItemsS3Service(itemsS3ServiceMock);
            marcXmlParserImpl.setItemsS3Service(itemsS3ServiceMock);

            doReturn(getZipResource(key)).when(itemsS3ServiceMock).getObject(ArgumentMatchers.any());
            doReturn(null).when(itemsS3ServiceMock).getCreationDate(ArgumentMatchers.any(),
                    ArgumentMatchers.any());

            itemsImportFromS3Script.run();

            Iterator<Item> items = itemService.findAll(context);
            assertTrue(items.hasNext());
            Item importedItem = items.next();
            assertFalse(items.hasNext());

            List<Bitstream> bitstreams = itemService.find(context, importedItem.getID())
                    .getBundles("ORIGINAL")
                    .get(0).getBitstreams();
            Bitstream importedBitstream = bitstreams.get(0);
            Bitstream importedBitstream2 = bitstreams.get(1);
            assertEquals("2023-05-06T03:04:56Z", itemsS3ServiceMock.getModificationDate(context, "217849"));
            assertEquals(1, itemService.getMetadataByMetadataString(importedItem, "oaire.version").size());
            assertEquals("http://purl.org/coar/version/c_970fb48d4fbd8a85",
                    itemService.getMetadataByMetadataString(importedItem, "oaire.version").get(0).getValue());
            assertEquals(0, bitstreamService.getMetadataByMetadataString(importedBitstream, "oaire.version").size());
            assertEquals("http://purl.org/coar/version/c_970fb48d4fbd8a85", bitstreamService
                    .getMetadataByMetadataString(importedBitstream2, "oaire.version").get(0).getValue());
        } finally {
            if (originalS3serviceOfMarcXmlParserImpl != null) {
               marcXmlParserImpl.setItemsS3Service(originalS3serviceOfMarcXmlParserImpl);
               originalS3serviceOfMarcXmlParserImpl.deleteModificationDate(context, "217849");
            }
        }
    }

    @Test
    public void testModificationDateMode() throws Exception {
        String key = "217849.zip";

        deleteAllFilesOnExit();
        MarcXmlParserImpl marcXmlParserImpl = null;
        ItemsS3Service originalS3serviceOfMarcXmlParserImpl = null;
        ItemsImportFromS3Script itemsImportFromS3Script = new ItemsImportFromS3Script();
        try {
            String[] args = new String[] { "items-import-from-s3", "-m" };
            TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
            itemsImportFromS3Script.initialize(args, handler, admin);
            marcXmlParserImpl = (MarcXmlParserImpl) itemsImportFromS3Script.getMarcXmlParser();
            originalS3serviceOfMarcXmlParserImpl = marcXmlParserImpl.getItemsS3Service();
            ItemsS3Service itemsS3ServiceMock = spy(originalS3serviceOfMarcXmlParserImpl);
            itemsImportFromS3Script.setItemsS3Service(itemsS3ServiceMock);
            marcXmlParserImpl.setItemsS3Service(itemsS3ServiceMock);

            doAnswer(new Answer<File>() {
                @Override
                public File answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    String key = (String) args[0];
                    return getZipResource(key);
                }
            }).when(itemsS3ServiceMock).getObject(ArgumentMatchers.any());
            doReturn(null).when(itemsS3ServiceMock).getCreationDate(ArgumentMatchers.any(),
                    ArgumentMatchers.any());
            doReturn(listImportTestKeys()).when(itemsS3ServiceMock).getAllItemsKeys();

            itemsImportFromS3Script.run();

            Iterator<Item> items = itemService.findAll(context);
            assertFalse(items.hasNext());
            assertEquals("2023-05-05T23:51:05Z", itemsS3ServiceMock.getModificationDate(context, "167656"));
            assertEquals("2023-05-05T18:59:01Z", itemsS3ServiceMock.getModificationDate(context, "79707"));
            assertEquals("2023-05-06T03:04:56Z", itemsS3ServiceMock.getModificationDate(context, "217849"));
        } finally {
            if (originalS3serviceOfMarcXmlParserImpl != null) {
               marcXmlParserImpl.setItemsS3Service(originalS3serviceOfMarcXmlParserImpl);
               originalS3serviceOfMarcXmlParserImpl.deleteModificationDate(context, "217849");
            }
        }
    }

    private Stream<String> listImportTestKeys() {
         return List.of("167656.zip", "79707.zip", "217849.zip").stream();
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

    private File getZipResource(String key) throws URISyntaxException, IOException {
        URL zipUrl = this.getClass().getResource("s3/" + key);
        File zipFile = spy(new File(zipUrl.toURI()));
        // prevent the import process to delete our test files once consumed
        doReturn(true).when(zipFile).delete();
        doNothing().when(zipFile).deleteOnExit();
        return zipFile;
    }

}
