/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.policy;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Utilities for OpenAire Policies
 * 
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 *
 */
public class OpenAirePolicyUtils {

    public static enum AccessRights {
        OPEN("http://purl.org/coar/access_right/c_abf2", "open access"),
        EMBARGOED("http://purl.org/coar/access_right/c_f1cf", "embargoed access"),
        RESTRICTED("http://purl.org/coar/access_right/c_16ec", "restricted access"),
        METADATA("http://purl.org/coar/access_right/c_14cb", "metadata only access");

        private String conceptURI;
        private String name;

        private AccessRights(String conceptURI, String name) {
            this.conceptURI = conceptURI;
            this.name = name;
        }

        public String getConceptURI() {
            return conceptURI;
        }

        public String getName() {
            return name;
        }
    }

    public static Optional<AccessRights> getPolicyByName(String name) {
        return Stream.of(AccessRights.values())
            .filter(accessRight -> name.equals(accessRight.getName()))
            .findFirst();
    }

    private OpenAirePolicyUtils() {}
}
