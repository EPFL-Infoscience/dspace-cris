/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.reader;

import java.util.List;

import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.w3c.dom.NodeList;

public interface ItemsImportMetadataFieldReader {

    List<MetadataValueDTO> readValues(Context context, String metadataField, NodeList nodeList);

    String getReaderName();

}
