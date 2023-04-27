/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.client;

import java.util.Optional;

import org.dspace.epfl.client.model.OrgUnitDTO;

public interface OrgUnitApiClient {

    boolean isOrgUnitActive(String acronym);

    Optional<OrgUnitDTO> getOrgUnit(String acronym, Language language);

    public enum Language {
        EN,
        FR;
    }
}
