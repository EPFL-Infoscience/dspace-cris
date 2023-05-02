/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.service;

import java.util.List;

import org.dspace.content.dto.MetadataValueDTO;

public interface OrgUnitApiService {

    public boolean isOrgUnitActive(String acronym);

    public List<MetadataValueDTO> getMetadataValues(String acronym);

}
