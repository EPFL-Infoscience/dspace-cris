/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.client.methods.RequestBuilder.get;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.epfl.client.model.OrgUnitDTO;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

public class EpflApiClientImpl implements EpflApiClient {

    private static final Logger LOGGER = LogManager.getLogger(EpflApiClientImpl.class);

    private static final String PERSONAL_PICTURE_URL = "https://people.epfl.ch/%s/photo";

    @Autowired
    private ConfigurationService configurationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    private void setup() {
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public boolean isOrgUnitActive(String acronym) {
        return getOrgUnit(acronym, Language.EN).isPresent();
    }

    @Override
    public Optional<OrgUnitDTO> getOrgUnit(String acronym, Language language) {

        HttpResponse response = performGetRequest(getOrgUnitApiUrl(), "acro", acronym, language);

        if (isNotFound(response)) {
            return Optional.empty();
        }

        if (isNotSuccessfull(response)) {
            String message = "Not successfully response incoming from OrgUnit API. "
                + "Status: " + getStatusCode(response) + " - Content: " + getContent(response);
            throw new RuntimeException(message);
        }

        OrgUnitDTO orgUnit = parseResponse(response, OrgUnitDTO.class);

        return Optional.ofNullable(orgUnit)
            .filter(OrgUnitDTO::isNotEmpty);
    }

    @Override
    public List<PersonDTO> getPersons(String query, Language language) {
        HttpResponse response = performGetRequest(getPersonApiUrl(), "q", query, language);

        if (isNotFound(response)) {
            return List.of();
        }

        if (isNotSuccessfull(response)) {
            String message = "Not successfully response incoming from Person API. "
                    + "Status: " + getStatusCode(response) + " - Content: " + getContent(response);
            throw new RuntimeException(message);
        }

        return List.of(parseResponse(response, PersonDTO[].class));
    }

    @Override
    public Optional<PersonDTO> getPerson(String sciper, Language language) {

        HttpResponse response = performGetRequest(getPersonApiUrl(), "q", sciper, language);

        if (isNotFound(response)) {
            return Optional.empty();
        }

        if (isNotSuccessfull(response)) {
            String message = "Not successfully response incoming from Person API. "
                + "Status: " + getStatusCode(response) + " - Content: " + getContent(response);
            throw new RuntimeException(message);
        }

        PersonDTO[] persons = parseResponse(response, PersonDTO[].class);

        if (ArrayUtils.isEmpty(persons)) {
            return Optional.empty();
        }
        if (persons.length > 1) {
            String message = "Invalid response from Person API. Too much results returned for the scipter " + sciper;
            throw new RuntimeException(message);
        } else if (!StringUtils.equals(persons[0].getSciper(), sciper)) {
            String message = "Invalid response from Person API. The sciper in the response "
                    + persons[0].getSciper() + " doesn't match the requested one " + sciper;
            throw new RuntimeException(message);
        }
        return Optional.of(persons[0]);

    }

    @Override
    public Optional<InputStream> getPersonalPicture(String emailLocalPart) {

        String url = String.format(PERSONAL_PICTURE_URL, emailLocalPart);

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {

            CloseableHttpResponse response = client.execute(new HttpGet(url));
            if (isNotSuccessfull(response)) {
                LOGGER.error("Unexpected response coming during public "
                    + "document download: " + getContent(response));
                return Optional.empty();
            }

            return getContentInputStream(response);

        } catch (UnsupportedOperationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpResponse performGetRequest(String url, String param, String value, Language language) {
        try {

            HttpUriRequest httpUriRequest = buildGetRequest(url, param, value, language);

            return HttpClientBuilder.create().build().execute(httpUriRequest);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpUriRequest buildGetRequest(String url, String param, String value, Language language) {

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(15 * 1000)
            .setConnectionRequestTimeout(15 * 1000)
            .setSocketTimeout(15 * 1000)
            .build();

        return get(url)
            .addParameter(param, value)
            .addParameter("hl", language.name().toLowerCase())
            .setConfig(requestConfig)
            .build();

    }

    private <T> T parseResponse(HttpResponse response, Class<T> clazz) {
        try {
            return objectMapper.readValue(getContent(response), clazz);
        } catch (UnsupportedOperationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<InputStream> getContentInputStream(HttpResponse response) {
        try {
            byte[] content = IOUtils.toByteArray(response.getEntity().getContent());
            return Optional.of(new ByteArrayInputStream(content));
        } catch (UnsupportedOperationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getContent(HttpResponse response) {
        try {
            HttpEntity entity = response.getEntity();
            return entity != null ? IOUtils.toString(entity.getContent(), UTF_8.name()) : null;
        } catch (UnsupportedOperationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isNotSuccessfull(HttpResponse response) {
        int statusCode = getStatusCode(response);
        return statusCode < 200 || statusCode > 299;
    }

    private boolean isNotFound(HttpResponse response) {
        return getStatusCode(response) == HttpStatus.SC_NOT_FOUND;
    }

    private int getStatusCode(HttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }

    private String getOrgUnitApiUrl() {
        return configurationService.getProperty("epfl.orgunit-import.api-url");
    }

    private String getPersonApiUrl() {
        return configurationService.getProperty("epfl.person-import.api-url");
    }

}
