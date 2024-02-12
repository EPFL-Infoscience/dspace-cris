/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.reader;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.content.dto.MetadataValueDTO;
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
    public void testUniqueMetadataReader() throws Exception {
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


    private String getFirstMetadataValue(List<MetadataValueDTO> metadata, String field) {
        return metadata.stream()
                       .filter(metadataValueDTO -> metadataValueDTO.getMetadataField().equals(field))
                       .map(MetadataValueDTO::getValue).findFirst().orElse(NOT_FOUND_VALUE);
    }

}
