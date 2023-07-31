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
import java.util.Optional;
import java.util.Set;

import org.dspace.content.dto.BitstreamDTO;
import org.dspace.content.dto.ItemDTO;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.dspace.epfl.script.model.ItemsImportMapping;
import org.w3c.dom.Node;

public interface MarcXmlParser {

    ItemsImportMapping parseMapping(String configuration);

    Node parse(InputStream source, String recordXPath);

    Set<String> getAllRecordTypes();

    Optional<String> getRecordType(Node record);

    ItemDTO readSingleItem(Context context, String id, Node record, ItemsImportMapping mapping);

    ItemDTO readSingleItem(Context context, String id, String recordType, Node record, ItemsImportMapping mapping);

    List<List<MetadataValueDTO>> readItems (Context context, InputStream source,
                                            ItemsImportMapping mapping, String expression);

    List<MetadataValueDTO> readItemMetadataValues(Context context, InputStream source, ItemsImportMapping mapping);

    List<MetadataValueDTO> readItemMetadataValues(Context context, Node record, ItemsImportMapping mapping);

    List<BitstreamDTO> readBitstreams(Context context, String id, Node record, ItemsImportMapping mapping);

    String readSubmitter(Context context, Node record, ItemsImportMapping mapping);

}
