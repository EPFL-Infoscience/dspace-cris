/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.dspace.importer.external.datamodel.ImportRecord;
import org.dspace.importer.external.epo.service.EpoImportMetadataSourceServiceImpl;
import org.dspace.importer.external.liveimportclient.service.LiveImportClientImpl;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for {@link EpoImportMetadataSourceServiceImpl}
 * 
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class EpoImportMetadataSourceServiceIT extends AbstractLiveImportIntegrationTest {

    @Autowired
    private LiveImportClientImpl liveImportClient;

    @Autowired
    private EpoImportMetadataSourceServiceImpl epoServiceImpl;

    @Test
    public void epoImportMetadataGetRecordsTest() throws Exception {
        context.turnOffAuthorisationSystem();
        InputStream file2token = null;
        InputStream file = null;
        InputStream file2 = null;
        InputStream file3 = null;
        String originKey = epoServiceImpl.getConsumerKey();
        String originSecret = epoServiceImpl.getConsumerSecret();
        CloseableHttpClient originalHttpClient = liveImportClient.getHttpClient();
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);

        try {
            file2token = getClass().getResourceAsStream("epo-token.json");
            file = getClass().getResourceAsStream("epo-resp.xml");
            file2 = getClass().getResourceAsStream("epo-first.xml");
            file3 = getClass().getResourceAsStream("epo-second.xml");

            String tokenResp = IOUtils.toString(file2token, Charset.defaultCharset());
            String epoResp = IOUtils.toString(file, Charset.defaultCharset());
            String epoResp2 = IOUtils.toString(file2, Charset.defaultCharset());
            String epoResp3 = IOUtils.toString(file3, Charset.defaultCharset());

            epoServiceImpl.setConsumerKey("test-key");
            epoServiceImpl.setConsumerSecret("test-secret");
            liveImportClient.setHttpClient(httpClient);
            epoServiceImpl.expireLogin();
            CloseableHttpResponse responseWithToken = mockResponse(tokenResp, 200, "OK");
            CloseableHttpResponse response1 = mockResponse(epoResp, 200, "OK");
            CloseableHttpResponse response2 = mockResponse(epoResp2, 200, "OK");
            CloseableHttpResponse response3 = mockResponse(epoResp3, 200, "OK");

            when(httpClient.execute(ArgumentMatchers.any()))
                           .thenReturn(responseWithToken, response1, response2, response3);

            context.restoreAuthSystemState();
            ArrayList<ImportRecord> collection2match = getRecords();
            Collection<ImportRecord> recordsImported = epoServiceImpl.getRecords("test query", 0, 2);
            assertEquals(2, recordsImported.size());
            matchRecords(new ArrayList<ImportRecord>(recordsImported), collection2match);
        } finally {
            if (Objects.nonNull(file2token)) {
                file2token.close();
            }
            if (Objects.nonNull(file)) {
                file.close();
            }
            if (Objects.nonNull(file2)) {
                file2.close();
            }
            if (Objects.nonNull(file3)) {
                file3.close();
            }
            epoServiceImpl.setConsumerKey(originKey);
            epoServiceImpl.setConsumerSecret(originSecret);
            liveImportClient.setHttpClient(originalHttpClient);
        }
    }

    @Test
    public void epoImportMetadataGetRecordsCountTest() throws Exception {
        context.turnOffAuthorisationSystem();
        InputStream file = null;
        InputStream file2 = null;
        String originKey = epoServiceImpl.getConsumerKey();
        String originSecret = epoServiceImpl.getConsumerSecret();
        CloseableHttpClient originalHttpClient = liveImportClient.getHttpClient();
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);

        try {
            file = getClass().getResourceAsStream("epo-token.json");
            file2 = getClass().getResourceAsStream("epo-resp.xml");
            String token = IOUtils.toString(file, Charset.defaultCharset());
            String epoResp = IOUtils.toString(file2, Charset.defaultCharset());

            epoServiceImpl.setConsumerKey("test-key");
            epoServiceImpl.setConsumerSecret("test-secret");
            liveImportClient.setHttpClient(httpClient);
            epoServiceImpl.expireLogin();

            CloseableHttpResponse responseWithToken = mockResponse(token, 200, "OK");
            CloseableHttpResponse response1 = mockResponse(epoResp, 200, "OK");

            when(httpClient.execute(ArgumentMatchers.any())).thenReturn(responseWithToken, response1);

            context.restoreAuthSystemState();
            int tot = epoServiceImpl.getRecordsCount("test query");
            assertEquals(10000, tot);
        } finally {
            if (Objects.nonNull(file)) {
                file.close();
            }
            if (Objects.nonNull(file2)) {
                file2.close();
            }
            epoServiceImpl.setConsumerKey(originKey);
            epoServiceImpl.setConsumerSecret(originSecret);
            liveImportClient.setHttpClient(originalHttpClient);
        }
    }

    @Test
    public void epoImportFromApplicationNumberAndDate() throws Exception {

        context.turnOffAuthorisationSystem();
        InputStream fileToken = null;
        InputStream fileSearch = null;
        InputStream fileDetail = null;
        String originKey = epoServiceImpl.getConsumerKey();
        String originSecret = epoServiceImpl.getConsumerSecret();
        epoServiceImpl.expireLogin();
        CloseableHttpClient originalHttpClient = liveImportClient.getHttpClient();
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);

        try {
            fileToken = getClass().getResourceAsStream("epo-token.json");
            fileSearch = getClass().getResourceAsStream("epo_WO2021EP82712_search.xml");
            fileDetail = getClass().getResourceAsStream("epo_WO2021EP82712_detail.xml");
            String token = IOUtils.toString(fileToken, Charset.defaultCharset());
            String epoRespSearch = IOUtils.toString(fileSearch, Charset.defaultCharset());
            String epoRespDetail = IOUtils.toString(fileDetail, Charset.defaultCharset());

            epoServiceImpl.setConsumerKey("test-key");
            epoServiceImpl.setConsumerSecret("test-secret");
            liveImportClient.setHttpClient(httpClient);

            CloseableHttpResponse responseWithToken = mockResponse(token, 200, "OK");
            CloseableHttpResponse responseSearch = mockResponse(epoRespSearch, 200, "OK");
            CloseableHttpResponse responseDetail = mockResponse(epoRespDetail, 200, "OK");

            when(httpClient.execute(ArgumentMatchers.any()))
                .thenReturn(responseWithToken, responseSearch, responseDetail);

            context.restoreAuthSystemState();

            ImportRecord record = epoServiceImpl.getRecord("WO2021EP82712$$$2021-11-23");
            assertNotNull(record);
            Optional<String> applicationValue = record.getSingleValue("dc", "identifier", "applicationnumber");
            assertTrue(applicationValue.isPresent());
            assertEquals("WO2021EP82712", applicationValue.get());
            Optional<String> dateSubmitted = record.getSingleValue("dcterms", "dateSubmitted", null);
            assertTrue(dateSubmitted.isPresent());
            assertEquals("2021-11-23", dateSubmitted.get());
            // perform the test again, this time we expect that the login is reused
            responseSearch = mockResponse(epoRespSearch, 200, "OK");
            responseDetail = mockResponse(epoRespDetail, 200, "OK");
            when(httpClient.execute(ArgumentMatchers.any()))
                .thenReturn(responseSearch, responseDetail);

            ImportRecord record2 = epoServiceImpl.getRecord("WO2021EP82712$$$2021-11-23");
            assertNotNull(record2);
            Optional<String> applicationValue2 = record.getSingleValue("dc", "identifier", "applicationnumber");
            assertTrue(applicationValue2.isPresent());
            assertEquals("WO2021EP82712", applicationValue2.get());
            Optional<String> dateSubmitted2 = record.getSingleValue("dcterms", "dateSubmitted", null);
            assertTrue(dateSubmitted2.isPresent());
            assertEquals("2021-11-23", dateSubmitted2.get());

        } finally {
            if (Objects.nonNull(fileToken)) {
                fileToken.close();
            }
            if (Objects.nonNull(fileSearch)) {
                fileSearch.close();
            }
            if (Objects.nonNull(fileDetail)) {
                fileDetail.close();
            }
            epoServiceImpl.setConsumerKey(originKey);
            epoServiceImpl.setConsumerSecret(originSecret);
            liveImportClient.setHttpClient(originalHttpClient);
        }
    }

    private ArrayList<ImportRecord> getRecords() {

        //define first record
        List<MetadatumDTO> metadata1  = new ArrayList<MetadatumDTO>();
        metadata1.add(createMetadatumDTO("dc", "identifier", "other", "epodoc:ES2902749T"));
        metadata1.add(createMetadatumDTO("dc", "identifier", "epo", "58098534"));
        metadata1.add(createMetadatumDTO("dc", "identifier", "applicationnumber", "ES20180705153T"));
        metadata1.add(createMetadatumDTO("dc", "identifier", "prioritynumber", "WO2018EP54052"));
        metadata1.add(createMetadatumDTO("dc", "date", "issued", "2022-03-29"));
        metadata1.add(createMetadatumDTO("dcterms", "dateSubmitted", null, "2018-02-19"));
        metadata1.add(createMetadatumDTO("dcterms", "dateAccepted", null, "2018-02-19"));
        metadata1.add(createMetadatumDTO("dcterms", "rightHolder", null, "Panka Blood Test GmbH"));
        metadata1.add(createMetadatumDTO("dc", "contributor", "author", "PANTEL, Klaus"));
        metadata1.add(createMetadatumDTO("dc", "contributor", "author", "BARTKOWIAK, Kai"));
        metadata1.add(createMetadatumDTO("dc", "title", null, "Método para el diagnóstico del cáncer de mama"));
        metadata1.add(createMetadatumDTO("local", "epo", "sourceType", "T3"));
        metadata1.add(createMetadatumDTO("dc", "type", null, "patent"));
        metadata1.add(createMetadatumDTO("dc", "identifier", "patentno", "ES2902749T"));
        metadata1.add(createMetadatumDTO("epfl", "patent", "kindcode", "T3"));
        metadata1.add(createMetadatumDTO("epfl", "patent", "date", "2022-03-29"));
        metadata1.add(createMetadatumDTO("oairecerif", "patent", "country", "ES"));

        //define second record
        List<MetadatumDTO> metadata2  = new ArrayList<MetadatumDTO>();
        metadata2.add(createMetadatumDTO("dc", "identifier", "other", "epodoc:TW202202864"));
        metadata2.add(createMetadatumDTO("dc", "identifier", "epo", "69192062"));
        metadata2.add(createMetadatumDTO("dc", "identifier", "applicationnumber", "TW20200122801"));
        metadata2.add(createMetadatumDTO("dc", "identifier", "prioritynumber", "WO2020EP51540"));
        metadata2.add(createMetadatumDTO("dc", "date", "issued", "2022-01-16"));
        metadata2.add(createMetadatumDTO("dcterms", "dateSubmitted", null, "2020-07-06"));
        metadata2.add(createMetadatumDTO("dcterms", "dateAccepted", null, "2020-01-22"));
        metadata2.add(createMetadatumDTO("dcterms", "rightHolder", null, "ADVANTEST CORPORATION"));
        metadata2.add(createMetadatumDTO("dc", "contributor", "author", "POEPPE, OLAF"));
        metadata2.add(createMetadatumDTO("dc", "contributor", "author", "HILLIGES, KLAUS-DIETER"));
        metadata2.add(createMetadatumDTO("dc", "contributor", "author", "KRECH, ALAN"));
        metadata2.add(createMetadatumDTO("dc", "title", null,
                "Automated test equipment for testing one or more devices under test, method for automated"
                + " testing of one or more devices under test, and computer program using a buffer memory"));
        metadata2.add(createMetadatumDTO("local", "epo", "sourceType", "A"));
        metadata2.add(createMetadatumDTO("dc", "type", null, "patent::utility model"));
        metadata2.add(createMetadatumDTO("dc", "identifier", "patentno", "TW202202864"));
        metadata2.add(createMetadatumDTO("epfl", "patent", "kindcode", "A"));
        metadata2.add(createMetadatumDTO("epfl", "patent", "date", "2022-01-16"));
        metadata2.add(createMetadatumDTO("oairecerif", "patent", "country", "TW"));

        ArrayList<ImportRecord> records = new ArrayList<>();
        records.add(new ImportRecord(metadata1));
        records.add(new ImportRecord(metadata2));

        return records;
    }

}