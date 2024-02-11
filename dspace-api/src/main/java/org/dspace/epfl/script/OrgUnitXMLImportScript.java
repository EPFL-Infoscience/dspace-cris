/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.EPersonServiceImpl;
import org.dspace.epfl.script.model.OrgUnitXMLToTSV;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * 
 * This script takes an xml file as input. This file represents a list of all
 * the org units to be imported (all_units.xml) The output of the script is a
 * .tsv file that has the same headers of the "incomplete units" tsv file that
 * was used in the previous steps of the migration. This scripts handles the
 * transformation. The idea is to use the tsv in output to import all the
 * orgunits instead of splitting in two iterations
 * 
 * @author Daniele Ninfo (daniele dot ninfo at 4science dot com)
 *
 */
public class OrgUnitXMLImportScript
        extends DSpaceRunnable<OrgUnitXMLImportScriptConfiguration<OrgUnitXMLImportScript>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrgUnitXMLImportScript.class);

    private String filename;

    private Context context;
    private EPersonServiceImpl ePersonService;
    private DocumentBuilder documentBuilder;
    private XPath xPath;

    private int totalRecords;
    private int elaboratedRecords = 0;
    private int skippedRecords = 0;

    @Override
    public void setup() throws ParseException {
        this.filename = commandLine.getOptionValue('f');
        this.ePersonService = new DSpace().getServiceManager().getServiceByName("org.dspace.eperson.EPersonServiceImpl",
                EPersonServiceImpl.class);

        try {
            this.documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            handler.logError("Could not create a new instance of DocumentBuilder", e);
            throw new RuntimeException(e);
        }

        xPath = XPathFactory.newInstance().newXPath();

    }

    @Override
    public void internalRun() throws Exception {
        handler.logDebug("Starting process orgunit-xml-import with filename=" + filename);

        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        try {
            context.turnOffAuthorisationSystem();

            if (StringUtils.isNotBlank(filename)) {
                executeScriptWithFile();
            } else {
                handler.logError("Filename not provided, interrupting the script");
            }

            context.complete();
            finalLogging();
        } catch (Exception e) {
            LOGGER.error("Generic error", e);
            handler.logError("Generic error", e);
            context.complete();
            finalLogging();
        } finally {
            context.restoreAuthSystemState();
        }

        handler.logDebug("End of process orgunit-xml-import");
    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = ePersonService.find(context, uuid);
            context.setCurrentUser(ePerson);
        } else {
            LOGGER.debug("could not find current eperson uuid");
        }
    }

    private void assignSpecialGroupsInContext() {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }

    private void executeScriptWithFile()
            throws IllegalArgumentException, IOException, AuthorizeException, SAXException, SQLException {
        InputStream inputStream = handler.getFileStream(context, filename)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Error reading file, the file couldn't be " + "found for filename: " + filename));
        List<OrgUnitXMLToTSV> elements = parseInputStream(inputStream);
        ByteArrayOutputStream bos = createTsvFile(elements);
        if (bos != null) {
            InputStream is = new ByteArrayInputStream(bos.toByteArray());
            handler.writeFilestream(context, "orgUnits.tsv", is, "text/tab-separated-values", false);
        }
    }

    private List<OrgUnitXMLToTSV> parseInputStream(InputStream inputStream) throws IOException, SAXException {
        List<OrgUnitXMLToTSV> list = new ArrayList<OrgUnitXMLToTSV>();
        Document document = documentBuilder.parse(inputStream);

        // Gets all the "record" nodes.
        NodeList recordList = getNodeList(document, "/collection/record");
        totalRecords = recordList.getLength();
        handler.logInfo("Found " + totalRecords + " records to be evaluated");

        // Every "record" node is mapped in a OrgUnitXMLToTSV containing all the info to
        // be mapped as tsv
        for (int i = 0; i < recordList.getLength(); i++) {
            handler.logInfo("Record " + i + "/" + (totalRecords - 1));
            LOGGER.info("Record " + (i + 1) + "/" + (totalRecords - 1));
            OrgUnitXMLToTSV element = getTsvElementFromNode(recordList.item(i));
            if (element != null && StringUtils.isNotBlank(element.getInfoscienceAuthNum())) {
                list.add(element);
                elaboratedRecords++;
                handler.logInfo("Record " + i + ": " + element.toString());
            } else {
                skippedRecords++;
                handler.logInfo("Record " + i + ": skipped. Node = " + convertNodeToXml(recordList.item(i)));
            }
        }

        return list;
    }

    private ByteArrayOutputStream createTsvFile(List<OrgUnitXMLToTSV> elements) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                CSVPrinter printer = new CSVPrinter(writer, CSVFormat.TDF)) {

            // header
            printer.printRecord("infoscience_auth_num", "infoscience_unit_code", "unit_code", "unit_name-fr",
                    "unit_name-en", "units_acro", "unit_acro-en", "unit_parent_code", "unit_parent_acronym", "unit_url",
                    "head_sciperid", "head_firstname", "head_lastname", "unit_status", "units_type");

            // rows
            for (OrgUnitXMLToTSV element : elements) {
                printer.printRecord(element.getInfoscienceAuthNum(), element.getInfoscienceUnitCode(),
                        element.getUnitCode(), element.getUnitNameFr(), element.getUnitNameEn(), element.getUnitsAcro(),
                        element.getUnitAcroEn(), element.getUnitParentCode(), element.getUnitParentAcronym(),
                        element.getUnitUrl(), element.getHeadSciperid(), element.getHeadFirstname(),
                        element.getHeadLastname(), element.getUnitStatus(), element.getUnitsType());
            }

            writer.flush();
            return out;
        } catch (Exception e) {
            handler.logError("Generic error in creating the tsv file", e);
            return null;
        }
    }

    private OrgUnitXMLToTSV getTsvElementFromNode(Node node) {
        try {
            OrgUnitXMLToTSV element = new OrgUnitXMLToTSV();
            element.setInfoscienceAuthNum(getNodeValue(node, getXPathControlField("001")));
            element.setInfoscienceUnitCode(getNodeValue(node, getXPathDataField("371", "g")));
            element.setUnitCode(getUnitCodeFromInfoscienceUnitCode(element));
            element.setUnitNameFr(getNodeValue(node, getXPathDataField("195", "b")));
            element.setUnitNameEn(getNodeValue(node, getXPathDataField("195", "c")));
            element.setUnitsAcro(getNodeValue(node, getXPathDataField("195", "a")));
            element.setUnitAcroEn(getNodeValue(node, getXPathDataField("195", "a"))); // units_acro and unit_acro_en are
                                                                                      // always equals in all_units.xml
            element.setUnitParentCode(getNodeValue(node, getXPathDataField("791", "0")));
            element.setUnitParentAcronym(getNodeValue(node, getXPathDataField("791", "a")));
            element.setUnitUrl(getNodeValue(node, getXPathDataField("856", "u")));
            element.setHeadSciperid(getNodeValue(node, getXPathDataField("272", "g")));
//            element.setHeadFirstname(getNodeValue(node, ""));
//            element.setHeadLastname(getNodeValue(node, ""));
            element.setUnitStatus(getNodeValue(node, getXPathDataField("921", "a")));
            element.setUnitsType(getNodeValue(node, getXPathDataField("980", "a")));
            return element;
        } catch (Exception e) {
            handler.logError("Generic error while mapping element " + node, e);
            return null;
        }
    }

    private String getXPathControlField(String tag) {
        return ".//controlfield[@tag = '" + tag + "']";
    }

    private String getXPathDataField(String tag, String subfieldCode) {
        if (StringUtils.isBlank(subfieldCode)) {
            return ".//datafield[@tag = '" + tag + "']";
        } else {
            return ".//datafield[@tag = '" + tag + "']/subfield[@code = '" + subfieldCode + "']";
        }
    }

    /**
     * Since the unit code is not directly written in the xml origin file, it will
     * be generated starting from the infoscience unit code.
     * 
     * @param element the current element with the infoscience unit code already
     *                mapped
     * @return a string representing the unit code
     */
    private String getUnitCodeFromInfoscienceUnitCode(OrgUnitXMLToTSV element) {
        String unitCode = "";
        String iuc = element.getInfoscienceUnitCode();
        if (StringUtils.isNotBlank(iuc)) {
            if (iuc.startsWith("U") || iuc.startsWith("S")) {
                unitCode = iuc.substring(1);
            }
            while (StringUtils.isNotBlank(unitCode) && unitCode.startsWith("0")) {
                unitCode = unitCode.substring(1);
            }
        }
        return unitCode;
    }

    private NodeList getNodeList(Object item, String expression) {
        try {
            return (NodeList) xPath.compile(expression).evaluate(item, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("An error occurs evaluating path " + expression, e);
        }
    }

    private String getNodeValue(Object item, String expression) {
        try {
            Node node = (Node) xPath.compile(expression).evaluate(item, XPathConstants.NODE);
            return node != null ? node.getTextContent() : "";
        } catch (XPathExpressionException e) {
            throw new RuntimeException("An error occurs evaluating path " + expression, e);
        }
    }

    public static String convertNodeToXml(Node node) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            return node.toString();
        }
    }

    private void finalLogging() {
        handler.logInfo("Total records: " + totalRecords + ". Elaborated: " + elaboratedRecords + ". Skipped: "
                + skippedRecords);
    }

    @Override
    @SuppressWarnings("unchecked")
    public OrgUnitXMLImportScriptConfiguration<OrgUnitXMLImportScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("orgunit-xml-import",
                OrgUnitXMLImportScriptConfiguration.class);
    }

}
