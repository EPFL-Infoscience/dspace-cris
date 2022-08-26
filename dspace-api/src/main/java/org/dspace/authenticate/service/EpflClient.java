/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate.service;

import java.io.IOException;
import java.nio.charset.Charset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.dspace.services.ConfigurationService;
import org.dspace.util.ThrowingSupplier;

public class EpflClient {

    private ConfigurationService configurationService;

    private ObjectMapper objectMapper = new ObjectMapper();

    public EpflResponse getAccred(String persid) {

        HttpUriRequest httpUriRequest = RequestBuilder.get(getAccredUrl())
            .addParameter("app", getApp())
            .addParameter("caller", getCaller())
            .addParameter("persid", persid)
            .addParameter("password", getPassword())
            .addHeader("Accept", "application/json")
            .build();

        return executeAndParseJson(httpUriRequest, EpflResponse.class);

    }

    private <T> T executeAndParseJson(HttpUriRequest httpUriRequest, Class<T> clazz) {

        HttpClient client = HttpClientBuilder.create().build();

        return executeAndReturns(() -> {

            HttpResponse response = client.execute(httpUriRequest);

            if (isNotSuccessfull(response)) {
                throw new EpflClientException(getStatusCode(response), formatErrorMessage(response));
            }

            return objectMapper.readValue(response.getEntity().getContent(), clazz);

        });

    }

    private boolean isNotSuccessfull(HttpResponse response) {
        int statusCode = getStatusCode(response);
        return statusCode < 200 || statusCode > 299;
    }

    private int getStatusCode(HttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }

    private String formatErrorMessage(HttpResponse response) {
        try {
            return IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset());
        } catch (UnsupportedOperationException | IOException e) {
            return "Generic error";
        }
    }

    private <T> T executeAndReturns(ThrowingSupplier<T, Exception> supplier) {
        try {
            return supplier.get();
        } catch (EpflClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EpflClientException(ex);
        }
    }

    private String getAccredUrl() {
        return configurationService.getProperty("epfl.websrv.accredurl");
    }

    private String getApp() {
        return configurationService.getProperty("epfl.websrv.app");
    }

    private String getCaller() {
        return configurationService.getProperty("epfl.websrv.caller");
    }

    private String getPassword() {
        return configurationService.getProperty("epfl.websrv.password");
    }

}
