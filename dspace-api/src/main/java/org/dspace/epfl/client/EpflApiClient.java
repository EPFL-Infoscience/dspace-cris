/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.client;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.dspace.epfl.client.model.OrgUnitDTO;
import org.dspace.epfl.client.model.PersonDTO;

public interface EpflApiClient {

    boolean isOrgUnitActive(String acronym);

    Optional<OrgUnitDTO> getOrgUnit(String acronym, Language language);

    List<PersonDTO> getPersons(String query, Language language);

    Optional<PersonDTO> getPerson(String sciper, Language language);

    Optional<InputStream> getPersonalPicture(String emailLocalPart);

    public enum Language {
        EN,
        FR;
    }

}
