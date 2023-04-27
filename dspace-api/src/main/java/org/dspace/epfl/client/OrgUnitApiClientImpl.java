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

import java.io.IOException;
import java.util.Optional;
import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.dspace.epfl.client.model.OrgUnitDTO;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

public class OrgUnitApiClientImpl implements OrgUnitApiClient {

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

        HttpResponse response = performGetRequest(acronym, language);

        if (isNotFound(response)) {
            return Optional.empty();
        }

        if (isNotSuccessfull(response)) {
            String message = "Not successfully response incoming from OrgUnit API. "
                + "Status: " + getStatusCode(response) + " - Content: " + getContent(response);
            throw new RuntimeException(message);
        }

        OrgUnitDTO orgUnit = parseResponse(response);

        return Optional.ofNullable(orgUnit)
            .filter(OrgUnitDTO::isNotEmpty);
    }

    private HttpResponse performGetRequest(String acronym, Language language) {
        try {
            HttpUriRequest httpUriRequest = buildGetRequest(acronym, language);
            return HttpClientBuilder.create().build().execute(httpUriRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpUriRequest buildGetRequest(String acronym, Language language) {
        return get(getOrgUnitApiUrl())
            .addParameter("acro", acronym)
            .addParameter("hl", language.name().toLowerCase())
            .build();
    }

    private OrgUnitDTO parseResponse(HttpResponse response) {
        try {
            return objectMapper.readValue(getContent(response), OrgUnitDTO.class);
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

}
