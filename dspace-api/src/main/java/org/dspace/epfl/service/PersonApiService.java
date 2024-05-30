/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.service;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.profile.ResearcherProfile;

public interface PersonApiService {

    public List<PersonDTO> getPersons(String query);

    public Optional<PersonDTO> getPerson(String sciper);

    public List<MetadataValueDTO> getMetadataValues(Context context, PersonDTO person);

    public List<MetadataValueDTO> getMetadataValues(Context context, String sciper);

    public Optional<InputStream> getPersonalPicture(String sciper);

    public String getSciperMetadataField();

    public List<String> getMetadataFields();

    public Optional<ResearcherProfile> findProfileBySciperAndFixOwnerIfNeeded(Context context, EPerson eperson,
            String sciper);

}
