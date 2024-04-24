/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.doi;

import org.apache.http.client.methods.HttpUriRequest;

/**
 * @author Andrea Bollini
 */
public class MockDataCiteConnector extends DataCiteConnector {

    /**
     * Mock the internal method to send requests prepared by the caller to DataCite
     * to force a specific response (200 for metadata request, 201 for doi request)
     * to allow test of doi registration.
     */
    protected DataCiteResponse sendHttpRequest(HttpUriRequest req, String doi) throws DOIIdentifierException {
        if (req.getURI().getPath().contains(METADATA_PATH)) {
            // metadata request are used to check if the doi is already registered
            return new DataCiteResponse(200, "OK");
        } else {
            return new DataCiteResponse(201, "OK");
        }
    }

}
