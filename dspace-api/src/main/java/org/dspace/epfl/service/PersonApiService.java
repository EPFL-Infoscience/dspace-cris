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

public interface PersonApiService {

    public List<MetadataValueDTO> getMetadataValues(String sciper);

    public Optional<InputStream> getPersonalPicture(String sciper);

    public String getSciperMetadataField();

}
