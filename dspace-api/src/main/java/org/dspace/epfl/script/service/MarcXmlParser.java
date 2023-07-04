/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service;

import java.io.InputStream;
import java.util.List;

import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.w3c.dom.Node;

public interface MarcXmlParser {

    Node parse(InputStream source);

    List<MetadataValueDTO> readMetadataValues(Context context, InputStream source);

    List<MetadataValueDTO> readMetadataValues(Context context, Node record);

    String readSubmitter(Context context, Node record);
}
