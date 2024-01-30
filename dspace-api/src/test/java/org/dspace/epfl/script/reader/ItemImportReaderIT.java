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

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.content.dto.ItemDTO;
import org.dspace.epfl.script.model.ItemsImportMapping;
import org.dspace.epfl.script.service.MarcXmlParser;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.junit.Test;
import org.w3c.dom.Node;


public class ItemImportReaderIT extends AbstractIntegrationTestWithDatabase {

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
        String recordType = "research-article";

        ItemDTO item = marcXmlParser.readSingleItem(context, "1234", recordType, record, mapping);

        String delimiter = " : ";

        assertEquals(item.getMetadataValues("dc.title").get(0).getValue(),
                firstTitle + delimiter + secondTitle + delimiter + subTitle);
    }

}