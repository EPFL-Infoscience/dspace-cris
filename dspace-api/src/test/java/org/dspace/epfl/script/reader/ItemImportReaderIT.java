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

    private static final String NOT_FOUNT_VALUE = "NotFound";

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
    public void testSimpleStringValueReader() throws Exception {
        String issnValue = "issnValue";
        String descriptionNotesValue = "descriptionNotesValue";
        String citationIssueValue = "citationIssueValue";
        String test = " <record> \n" +
                "<datafield tag=\"022\" ind1=\" \" ind2=\" \">\n" +
                " <subfield code=\"a\">" + issnValue + "</subfield>\n" +
                " </datafield>\n" +
                "<datafield tag=\"500\" ind1=\" \" ind2=\" \">\n" +
                " <subfield code=\"a\">" + descriptionNotesValue + "</subfield>\n" +
                " </datafield>\n" +
                "<datafield tag=\"773\" ind1=\" \" ind2=\" \">\n" +
                " <subfield code=\"k\">" + citationIssueValue + "</subfield>\n" +
                " </datafield>\n" +
                "</record>";
        InputStream inputStream = new ByteArrayInputStream(test.getBytes());

        Node record = marcXmlParser.parse(inputStream, mapping.getItemXPath());

        List<MetadataValueDTO> itemMetadata = marcXmlParser.readItemMetadataValues(context, record, mapping);

        assertEquals(getFirstMetadataValue(itemMetadata, "dc.relation.issn"), issnValue);
        assertEquals(getFirstMetadataValue(itemMetadata, "dc.description.notes"), descriptionNotesValue);
        assertEquals(getFirstMetadataValue(itemMetadata, "oaire.citation.issue"), citationIssueValue);
    }

    @Test
    public void testTitleReader() throws Exception {
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

        assertEquals(getFirstMetadataValue(itemMetadata, "dc.title"),
                firstTitle + delimiter + secondTitle + delimiter + subTitle);
    }

    @Test
    public void testThatAccessRightWillNotBeImported() throws Exception {
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

        assertEquals(getFirstMetadataValue(itemMetadata, "dc.rights.accessRights"),
                NOT_FOUNT_VALUE);
    }


    private String getFirstMetadataValue(List<MetadataValueDTO> metadata, String field) {
       return  metadata.stream()
               .filter(metadataValueDTO -> metadataValueDTO.getMetadataField().equals(field))
               .map(MetadataValueDTO::getValue).findFirst().orElse(NOT_FOUNT_VALUE);
    }

}
