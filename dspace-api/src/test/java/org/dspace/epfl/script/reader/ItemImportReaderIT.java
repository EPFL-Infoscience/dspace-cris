/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.content.dto.BitstreamDTO;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.CrisConstants;
import org.dspace.epfl.script.model.ItemsImportMapping;
import org.dspace.epfl.script.service.MarcXmlParser;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.junit.Test;
import org.w3c.dom.Node;


public class ItemImportReaderIT extends AbstractIntegrationTestWithDatabase {

    private static final String NOT_FOUND_VALUE = "NotFound";

    private MarcXmlParser marcXmlParser;
    private ItemsImportMapping mapping;
    private ConfigurationService configurationService;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        this.marcXmlParser = new DSpace().getServiceManager().getServicesByType(MarcXmlParser.class).get(0);
        String configuration = configurationService.getProperty("epfl.items-import.mapping-configuration.path");
        mapping = marcXmlParser.parseMapping(configuration);
    }

    @Test
    public void testSimpleStringValueReader() {
        String issnValue = "issnValue";
        String descriptionNotesValue = "descriptionNotesValue";
        String citationIssueValue = "citationIssueValue";
        String url = "https://url.com";
        String urlDescription = "urlDescription";
        String test = "<record> \n" +
                "<datafield tag=\"022\" ind1=\" \" ind2=\" \">\n" +
                " <subfield code=\"a\">" + issnValue + "</subfield>\n" +
                "</datafield>\n" +
                "<datafield tag=\"500\" ind1=\" \" ind2=\" \">\n" +
                " <subfield code=\"a\">" + descriptionNotesValue + "</subfield>\n" +
                "</datafield>\n" +
                "<datafield tag=\"773\" ind1=\" \" ind2=\" \">\n" +
                " <subfield code=\"k\">" + citationIssueValue + "</subfield>\n" +
                "</datafield>\n" +
                "<datafield tag=\"856\" ind1=\"4\" ind2=\"1\">\n" +
                " <subfield code=\"u\">" + url + "</subfield>\n" +
                " <subfield code=\"y\">" + urlDescription + "</subfield>\n" +
                "</datafield>\n" +
                "</record>";
        InputStream inputStream = new ByteArrayInputStream(test.getBytes());

        Node record = marcXmlParser.parse(inputStream, mapping.getItemXPath());

        List<MetadataValueDTO> itemMetadata = marcXmlParser.readItemMetadataValues(context, record, mapping);

        assertEquals(issnValue, getFirstMetadataValue(itemMetadata, "dc.relation.issn"));
        assertEquals(descriptionNotesValue, getFirstMetadataValue(itemMetadata, "dc.description.notes"));
        assertEquals(citationIssueValue, getFirstMetadataValue(itemMetadata, "oaire.citation.issue"));
        assertEquals(url, getFirstMetadataValue(itemMetadata, "epfl.url"));
        assertEquals(urlDescription, getFirstMetadataValue(itemMetadata, "epfl.url.description"));
    }

    @Test
    public void testTitleReader() {
        String firstTitle = "firstTitle";
        String secondTitle = "secondTitle";
        String subTitle = "subTitle";
        String test = " <record> \n" +
                "<datafield tag=\"245\" ind1=\" \" ind2=\" \">\n" +
                "<subfield code=\"a\">" + firstTitle + "</subfield>\n" +
                "<subfield code=\"a\">" + secondTitle + "</subfield>\n" +
                "<subfield code=\"a\">" + secondTitle + "</subfield>\n" +
                "<subfield code=\"b\">" + subTitle + "</subfield>\n" +
                "  </datafield>\n" +
                "</record>";
        InputStream inputStream = new ByteArrayInputStream(test.getBytes());

        Node record = marcXmlParser.parse(inputStream, mapping.getItemXPath());

        List<MetadataValueDTO> itemMetadata = marcXmlParser.readItemMetadataValues(context, record, mapping);

        String delimiter = " : ";

        assertEquals(firstTitle + delimiter + secondTitle + delimiter + subTitle,
                     getFirstMetadataValue(itemMetadata, "dc.title"));
    }

    @Test
    public void testThatAccessRightWillNotBeImported() {
        String accessRightDefinition = "accessRightDefinition";
        String accessRightURI = "accessRightURI";
        String test = " <record> \n" +
                "<datafield tag=\"542\" ind1=\" \" ind2=\" \">\n" +
                "<subfield code=\"a\">" + accessRightDefinition + "</subfield>\n" +
                "<subfield code=\"u\">" + accessRightURI + "</subfield>\n" +
                "  </datafield>\n" +
                "</record>";
        InputStream inputStream = new ByteArrayInputStream(test.getBytes());

        Node record = marcXmlParser.parse(inputStream, mapping.getItemXPath());

        List<MetadataValueDTO> itemMetadata = marcXmlParser.readItemMetadataValues(context, record, mapping);

        assertEquals(NOT_FOUND_VALUE, getFirstMetadataValue(itemMetadata, "dc.rights.accessRights"));
    }

    @Test
    public void testThesisReader() {
        String authorityPrefix = "will be referenced::ACRONYM::";
        String faculty = "faculty";
        String section = "section";
        String institute = "institute";
        String doctoralSchool = "doctoralSchool";
        String originalUnit = "originalUnit";
        String test = "<record> \n" +
            "<datafield tag=\"918\" ind1=\" \" ind2=\" \">\n" +
            "   <subfield code=\"a\">" + faculty + "</subfield>\n" +
            "   <subfield code=\"b\">" + section + "</subfield>\n" +
            "   <subfield code=\"c\">" + institute + "</subfield>\n" +
            "   <subfield code=\"d\">" + doctoralSchool + "</subfield>\n" +
            "</datafield>\n" +
            "<datafield tag=\"919\" ind1=\" \" ind2=\" \">\n" +
            "   <subfield code=\"a\">" + originalUnit + "</subfield>\n" +
            "</datafield>\n" +
            "</record>";
        InputStream inputStream = new ByteArrayInputStream(test.getBytes());

        Node record = marcXmlParser.parse(inputStream, mapping.getItemXPath());

        List<MetadataValueDTO> itemMetadata = marcXmlParser.readItemMetadataValues(context, record, mapping);

        checkValueAndAuthority(itemMetadata, "epfl.thesis.faculty", faculty, authorityPrefix);
        checkValueAndAuthority(itemMetadata, "epfl.thesis.section", section, authorityPrefix);
        checkValueAndAuthority(itemMetadata, "epfl.thesis.institute", institute, authorityPrefix);
        checkValueAndAuthority(itemMetadata, "epfl.thesis.doctoralSchool", doctoralSchool, authorityPrefix);
        checkValueAndAuthority(itemMetadata, "epfl.thesis.originalUnit", originalUnit, authorityPrefix);
    }

    @Test
    public void testFunderReader() {
        String test =
                "<record> \n" +
                  "<datafield tag=\"536\" ind1=\" \" ind2=\" \">" +
                  "     <subfield code=\"a\">US foundations</subfield>" +
                  "  <subfield code=\"c\">Applied Technology Council</subfield>" +
                  "</datafield>" +
                  "<datafield tag=\"536\" ind1=\" \" ind2=\" \">" +
                  "  <subfield code=\"a\">US foundations</subfield>" +
                  "  <subfield code=\"c\">National Institute of standards and Technology</subfield>" +
                  "</datafield>" +
                  "<datafield tag=\"536\" ind1=\" \" ind2=\" \">" +
                  "  <subfield code=\"a\">US foundations</subfield>" +
                  "</datafield>" +
                  "<datafield tag=\"536\" ind1=\" \" ind2=\" \">" +
                  "  <subfield code=\"a\">FNS</subfield>" +
                  "  <subfield code=\"c\">200021_169248</subfield>" +
                  "</datafield>" +
                "</record>";
        InputStream inputStream = new ByteArrayInputStream(test.getBytes());

        Node record = marcXmlParser.parse(inputStream, mapping.getItemXPath());

        List<MetadataValueDTO> itemMetadata = marcXmlParser.readItemMetadataValues(context, record, mapping);

        checkMetadataValue("US foundations", itemMetadata, "oairecerif.funder", 0);
        // the value below looks odd but the issue is in the epfl mess data where many
        // records have the funder name in the 'c' subfield instead than the 'a' field
        // where in such case a sort of "funder category" is present
        checkMetadataValue("Applied Technology Council", itemMetadata, "dc.relation.grantno", 0);

        checkMetadataValue("US foundations", itemMetadata, "oairecerif.funder", 1);
        // the value below looks odd but the issue is in the epfl mess data where many
        // records have the funder name in the 'c' subfield instead than the 'a' field
        // where in such case a sort of "funder category" is present
        checkMetadataValue("National Institute of standards and Technology", itemMetadata, "dc.relation.grantno", 1);

        checkMetadataValue("US foundations", itemMetadata, "oairecerif.funder", 2);
        // we want to test that the grant no stay in sync with the funder name
        checkMetadataValue(CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE, itemMetadata, "dc.relation.grantno", 2);

        checkMetadataValue("FNS", itemMetadata, "oairecerif.funder", 3);
        checkMetadataValue("200021_169248", itemMetadata, "dc.relation.grantno", 3);
    }

    @Test
    public void testBitstreamOaireVersion() {
        String expectedStoreData = "http://purl.org/coar/version/c_970fb48d4fbd8a85";
        String test = " <record> \n" +
                " <datafield tag=\"856\" ind1=\"4\" ind2=\" \">\n" +
                "    <subfield code=\"9\">1fbdd269-53f8-4f43-b6d0-ce5a4ef11a7c</subfield>\n" +
                "    <subfield code=\"e\">Public</subfield>\n" +
                "    <subfield code=\"s\">6540918</subfield>\n" +
                "    <subfield code=\"u\">https://infoscience.epfl.ch/record/262694/files/Peirera%20et%20all</subfield>\n" +
                "    <subfield code=\"2\">cf740d1afe73b6f79135973139a53778</subfield>\n" +
                "</datafield>\n" +
                "    <datafield tag=\"856\" ind1=\"4\" ind2=\" \">\n" +
                "    <subfield code=\"9\">9b9b8e83-a2f8-48ab-a660-cad9fbe95d35</subfield>\n" +
                "    <subfield code=\"0\">Publisher's version</subfield>\n" +
                "    <subfield code=\"s\">6544064</subfield>\n" +
                "    <subfield code=\"u\">https://infoscience.epfl.ch/record/262694/files/Pereira%20et%20all.pdf</subfield>\n" +
                "    <subfield code=\"e\">Public</subfield>\n" +
                "    <subfield code=\"2\">db9bdf79b5f3d34a89463aaf05b8b707</subfield>\n" +
                "</datafield>\n" +
                "</record>";
        InputStream inputStream = new ByteArrayInputStream(test.getBytes());
        Node record = marcXmlParser.parse(inputStream, mapping.getItemXPath());
        List<MetadataValueDTO> itemMetadata = marcXmlParser.readItemMetadataValues(context, record, mapping);
        List<BitstreamDTO> bitstreams = marcXmlParser.readBitstreams(context, "12345", record, mapping);
        assertEquals(expectedStoreData, getFirstMetadataValue(itemMetadata, "oaire.version"));
        assertEquals(NOT_FOUND_VALUE, getFirstMetadataValue(bitstreams.get(0).getMetadataValues(), "oaire.version"));
        assertEquals(expectedStoreData, getFirstMetadataValue(bitstreams.get(1).getMetadataValues(), "oaire.version"));
    }

    @Test
    public void testUniqueMetadataReader() {
        String emailValue = "emailValue";
        String test = " <record> \n" +
                "<datafield tag=\"856\" ind1=\"0\" ind2=\" \">\n" +
                "<subfield code=\"f\">" + emailValue + "</subfield>\n" +
                "  </datafield>\n" +
                "<datafield tag=\"856\" ind1=\"0\" ind2=\" \">\n" +
                "<subfield code=\"f\">" + emailValue + "</subfield>\n" +
                "  </datafield>\n" +
                "</record>";
        InputStream inputStream = new ByteArrayInputStream(test.getBytes());

        Node record = marcXmlParser.parse(inputStream, mapping.getItemXPath());

        List<MetadataValueDTO> itemMetadata = marcXmlParser.readItemMetadataValues(context, record, mapping);

        int itemMetadataCount = (int) itemMetadata.stream()
                .filter(metadataValueDTO -> metadataValueDTO.getMetadataField().equals("epfl.lastmodified.email"))
                .count();

        assertEquals(itemMetadataCount, 1);
    }

    private void checkValueAndAuthority(List<MetadataValueDTO> metadataValues, String field, String expectedValue,
                                        String authorityPrefix) {
        assertEquals(expectedValue, getFirstMetadataValue(metadataValues, field));
        assertEquals(authorityPrefix + expectedValue, getMetadataAuthority(metadataValues, field));
    }

    @Test
    public void testNestedMetadataFieldReader()  {
        String type = "testType";
        String identifier = "testIdentifier";
        String test = " <record> \n" +
            "<datafield tag=\"787\" ind1=\" \" ind2=\" \">\n" +
            "<subfield code=\"e\">" + type + "</subfield>\n" +
            "<subfield code=\"w\">" + identifier + "</subfield>\n" +
            "  </datafield>\n" +
            "</record>";

        InputStream inputStream = new ByteArrayInputStream(test.getBytes());

        Node record = marcXmlParser.parse(inputStream, mapping.getItemXPath());

        List<MetadataValueDTO> itemMetadata = marcXmlParser.readItemMetadataValues(context, record, mapping);

        assertEquals(getFirstMetadataValue(itemMetadata, "epfl.relationpublication.type"),type);
        assertEquals(getFirstMetadataValue(itemMetadata, "epfl.relationpublication.identifier"),identifier);
    }

    @Test
    public void testRelationProductMetadataFieldReader()  {
        String type = "testType";
        String identifier = "testIdentifier";
        String test = " <record> \n" +
            "<datafield tag=\"790\" ind1=\" \" ind2=\" \">\n" +
            "<subfield code=\"e\">" + type + "</subfield>\n" +
            "<subfield code=\"w\">" + identifier + "</subfield>\n" +
            "  </datafield>\n" +
            "</record>";

        InputStream inputStream = new ByteArrayInputStream(test.getBytes());

        Node record = marcXmlParser.parse(inputStream, mapping.getItemXPath());

        List<MetadataValueDTO> itemMetadata = marcXmlParser.readItemMetadataValues(context, record, mapping);

        assertEquals(getFirstMetadataValue(itemMetadata, "epfl.relationproduct.identifier"),identifier);
    }

    @Test
    public void testThatRelationJournalMetadataWithRelationIssnIsPresent() {
        String firstRelationJournal = "first relation";
        String secondRelationJournal = "second relation";
        String type = "Journal Articles";
        String testIssn = "testIssn";
        String test1 = " <record> \n" +
            "<datafield tag=\"773\" ind1=\" \" ind2=\" \">\n" +
            "<subfield code=\"t\">" + firstRelationJournal + "</subfield>\n" +
            "  </datafield>\n" +
            "<datafield tag=\"022\" ind1=\" \" ind2=\" \">\n" +
            "<subfield code=\"a\">" + testIssn + "</subfield>\n" +
            "  </datafield>\n" +
            "<datafield tag=\"336\" ind1=\" \" ind2=\" \">\n" +
            "<subfield code=\"a\">" + type + "</subfield>\n" +
            "  </datafield>\n" +
            "<datafield tag=\"973\" ind1=\" \" ind2=\" \">\n" +
            "<subfield code=\"r\">" + "NON-REVIEWED" + "</subfield>\n" +
            "  </datafield>\n" +
            "</record>";

        String test2 = " <record> \n" +
            "<datafield tag=\"773\" ind1=\" \" ind2=\" \">\n" +
            "<subfield code=\"t\">" + secondRelationJournal + "</subfield>\n" +
            "  </datafield>\n" +
            "<datafield tag=\"336\" ind1=\" \" ind2=\" \">\n" +
            "<subfield code=\"a\">" + type + "</subfield>\n" +
            "  </datafield>\n" +
            "<datafield tag=\"973\" ind1=\" \" ind2=\" \">\n" +
            "<subfield code=\"r\">" + "NON-REVIEWED" + "</subfield>\n" +
            "  </datafield>\n" +
            "</record>";

        InputStream inputStream1 = new ByteArrayInputStream(test1.getBytes());
        InputStream inputStream2 = new ByteArrayInputStream(test2.getBytes());

        Node record1 = marcXmlParser.parse(inputStream1, mapping.getItemXPath());
        Node record2 = marcXmlParser.parse(inputStream2, mapping.getItemXPath());

        List<MetadataValueDTO> itemMetadataWithIssn = marcXmlParser.readItemMetadataValues(context, record1, mapping);
        List<MetadataValueDTO> itemMetadataWithoutIssn = marcXmlParser.readItemMetadataValues(context, record2,
                mapping);

        assertEquals(firstRelationJournal, getFirstMetadataValue(itemMetadataWithIssn, "dc.relation.journal"));

        assertEquals("will be generated::ISSN::" + testIssn,
                getMetadataAuthority(itemMetadataWithIssn, "dc.relation.journal"));

        assertEquals(secondRelationJournal, getFirstMetadataValue(itemMetadataWithoutIssn, "dc.relation.journal"));

        assertEquals(NOT_FOUND_VALUE, getMetadataAuthority(itemMetadataWithoutIssn, "dc.relation.journal"));
    }

    private void checkMetadataValue(String expectedValue, List<MetadataValueDTO> itemMetadata, String field, int pos) {
        Optional<MetadataValueDTO> metadata = getMetadataValue(itemMetadata, field, pos);
        assertTrue(metadata.isPresent());
        assertEquals(expectedValue, metadata.get().getValue());
    }

    private Optional<MetadataValueDTO> getMetadataValue(List<MetadataValueDTO> itemMetadata, String field, int i) {
        return itemMetadata.stream()
                .filter(metadataValueDTO -> metadataValueDTO.getMetadataField().equals(field))
                .skip(i).findFirst();
    }

    private String getFirstMetadataValue(List<MetadataValueDTO> metadata, String field) {
        return metadata.stream()
                       .filter(metadataValueDTO -> metadataValueDTO.getMetadataField().equals(field))
                       .map(MetadataValueDTO::getValue).findFirst().orElse(NOT_FOUND_VALUE);
    }

    private String getMetadataAuthority(List<MetadataValueDTO> metadata, String field) {
        return metadata.stream()
            .filter(metadataValueDTO -> metadataValueDTO.getMetadataField().equals(field))
            .map(MetadataValueDTO::getAuthority)
            .filter(Objects::nonNull).findFirst()
            .orElse(NOT_FOUND_VALUE);
    }
}
