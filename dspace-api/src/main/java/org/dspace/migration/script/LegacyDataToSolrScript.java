/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.migration.script;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class LegacyDataToSolrScript
    extends DSpaceRunnable<LegacyDataToSolrScriptConfiguration<LegacyDataToSolrScript>> {

    private static final Logger log = LoggerFactory.getLogger(LegacyDataToSolrScript.class);

    private AmazonS3 s3Service = null;

    private TransferManager transferManager = null;

    private String bucketName;
    private HttpSolrClient solrClient;

    @Override
    public LegacyDataToSolrScriptConfiguration<LegacyDataToSolrScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("legacy-data-to-solr",
                                                                 LegacyDataToSolrScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        s3Service = AmazonS3ClientBuilder.defaultClient();
        transferManager = TransferManagerBuilder.standard()
                                                .withAlwaysCalculateMultipartMd5(true)
                                                .withS3Client(s3Service)
                                                .build();

        bucketName = commandLine.getOptionValue("b");
    }

    @Override
    public void internalRun() throws Exception {

        if (StringUtils.isBlank(bucketName)) {
            throw new RuntimeException("Bucket name must be specified");
        }

        handler.logInfo("Starting iteration over s3 bucket " + bucketName);
        Integer limit = Optional.ofNullable(commandLine.getOptionValue("l")).map(Integer::parseInt)
                                .orElse(Integer.MAX_VALUE);
        String startFrom = commandLine.getOptionValue("f");

        Iterator<S3ObjectSummary> iterator = S3Objects.inBucket(s3Service, bucketName).iterator();
        Integer imported = 0;
        boolean startFound = StringUtils.isBlank(startFrom);
        while (iterator.hasNext() && imported < limit) {
            S3ObjectSummary summary = iterator.next();
            if (StringUtils.isNotBlank(startFrom) && !startFrom.equals(summary.getKey()) && !startFound) {
                continue;
            }
            startFound = StringUtils.isNotBlank(startFrom) && summary.getKey().equals(startFrom);

            GetObjectRequest rq = new GetObjectRequest(bucketName, summary.getKey());
            File file = File.createTempFile("s3-import-download", ".zip");
            file.deleteOnExit();
            try {
                Download download = transferManager.download(rq, file);
                download.waitForCompletion();
                handler.logInfo("Storing to solr content of file " + summary.getKey());
                toSolr(file, summary.getKey());
                handler.logInfo("Content of file " + summary.getKey() + " stored to solr");
                imported++;
            } catch (Exception e) {
                handler.logWarning("Error while importing content of file " + summary.getKey() + ": " + e.getMessage());
                log.warn(e.getMessage(), e);
            } finally {
                file.delete();
            }
        }
        handler.logInfo("Process finished: " + imported + " objects have been imported");
    }

    private void toSolr(File file, String legacyId) throws IOException {
        File metadataFile = metadataFile(file, legacyId);
        Document document = readXMLDocumentFromFile(metadataFile);
        storeToSolr(document);
    }

    private File metadataFile(File zip, String legacyId) throws IOException {
        File file = File.createTempFile("s3-import-download-metadata", ".xml");
        file.deleteOnExit();
        try (ZipFile zipFile = new ZipFile(zip)) {
            handler.logInfo("parsing file:::" + zip.getName());
            String number = legacyId.substring(0, legacyId.indexOf(".zip"));
            ZipEntry entry = zipFile.getEntry(number + "/metadata.xml");

            FileUtils.copyInputStreamToFile(zipFile.getInputStream(entry), file);
            return file;
        } catch (Exception e) {
            file.delete();
            throw new RuntimeException(e);
        }
    }

    private Document readXMLDocumentFromFile(File fileNameWithPath) {

        //Get Document Builder
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
            Document document = builder.parse(fileNameWithPath);
            document.getDocumentElement().normalize();
            return document;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            handler.handleException("Error while importing from legacy data ", e);
        }
        return null;
    }

    private void storeToSolr(Document document) {
        SolrInputDocument solrInputDocument = new SolrInputDocument();
        try {
            Map<String, List<Map<String, String>>> subtypes = new HashMap<>();
            process(document.getElementsByTagName("controlfield"), solrInputDocument, subtypes);
            process(document.getElementsByTagName("datafield"), solrInputDocument, subtypes);

            addSubfields(solrInputDocument, subtypes);

            solrInputDocument.addField("lastModified_dt", new Date());
            SolrClient solr = getSolr();
            solr.add(solrInputDocument);
            solr.commit();;
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addSubfields(SolrInputDocument document, Map<String, List<Map<String, String>>> subfields) {
        fillMap(subfields);
        subfields.forEach((field, value) -> value.stream().flatMap(map -> map.entrySet().stream())
                                                 .forEach(subtypeEntry -> document.addField(
                                                     "field-" + field + "_" + subtypeEntry.getKey(),
                                                     subtypeEntry.getValue())));
    }

    private static void fillMap(Map<String, List<Map<String, String>>> subfields) {
        for (String key : subfields.keySet()) {
            List<Map<String, String>> maps = subfields.get(key);
            for (int i = 0; i < maps.size(); i++) {
                Map<String, String> stringStringMap = maps.get(i);
                for (String innerKey : stringStringMap.keySet()) {
                    fillOthers(maps, i, innerKey);
                }
            }
        }
    }

    private static void fillOthers(List<Map<String, String>> maps, int index, String key) {
        for (int i = 0; i < maps.size(); i++) {
            if (i == index || maps.get(i).containsKey(key)) {
                continue;
            }
            maps.get(i).put(key, "PLACEHOLDER");
        }
    }

    private static void process(NodeList nList, SolrInputDocument document,
                                Map<String, List<Map<String, String>>> subfields)
        throws SolrServerException, IOException {
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node node = nList.item(temp);


            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) node;

                StringBuilder field = new StringBuilder().append(eElement.getAttribute("tag")).append("_")
                                                         .append(valueOrDefault(eElement.getAttribute("ind1")))
                                                         .append("_")
                                                         .append(valueOrDefault(eElement.getAttribute("ind2")));


                String fieldName = field.toString();
                if ("controlfield".equals(node.getNodeName())) {
                    String value = eElement.getFirstChild().getNodeValue();
                    if ("001_-_-".equals(fieldName)) {
                        document.addField("legacy_id", value);
                    }
                    document.addField("field-" + fieldName, value);
                } else if ("datafield".equals(node.getNodeName())) {
                    NodeList subfield = ((Element) node).getElementsByTagName("subfield");

                    Map<String, String> subfieldValue = new HashMap<>();
                    for (int i = 0; i < subfield.getLength(); i++) {
                        Node item = subfield.item(i);
                        Element e = (Element) item;

                        subfieldValue.put(e.getAttribute("code"), e.getFirstChild().getNodeValue());

                    }
                    if (!subfields.containsKey(fieldName)) {
                        subfields.put(fieldName, new LinkedList<>());
                    }
                    subfields.get(fieldName).add(subfieldValue);
                }

            }
        }
    }

    private static String valueOrDefault(String s) {
        return s.trim().length() == 0 ? "-" : s;
    }

    protected SolrClient getSolr() {
        if (solrClient == null) {
            String solrService = DSpaceServicesFactory.getInstance().getConfigurationService()
                                                      .getProperty("epfl.legacy-data.solr-url",
                                                                   "http://localhost:8983/solr/epflmigration");
            solrClient = new HttpSolrClient.Builder(solrService).build();
        }
        return solrClient;
    }
}
