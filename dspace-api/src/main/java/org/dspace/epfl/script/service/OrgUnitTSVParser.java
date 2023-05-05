/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service;

import java.io.InputStream;

import org.dspace.epfl.script.model.OrgUnitTSV;

public interface OrgUnitTSVParser {

    OrgUnitTSV parseTSV(InputStream inputStream);
}
