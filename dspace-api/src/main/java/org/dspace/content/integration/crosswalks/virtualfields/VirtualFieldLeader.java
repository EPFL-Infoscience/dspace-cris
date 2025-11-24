/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.integration.crosswalks.virtualfields;

import java.util.Map;

import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

public class VirtualFieldLeader implements VirtualField {

    @Autowired
    private ItemService itemService;

    private String typeOfRecord;

    private String defaultTypeOfRecord;

    private String bibliographicLevel;

    private String defaultBibliographicLevel;

    private Map<String, String> typeOfRecordMap;

    private Map<String, String> bibliographicLevelMap;

    @Override
    public String[] getMetadata(Context context, Item item, String fieldName) {

        String type = itemService.getMetadataFirstValue(item, "dc", "type", null, Item.ANY);

        String typeOfRecord = getTypeOfRecord(type);
        String bibliographicLevel = getBibliographicLevel(type);

        StringBuilder leader = new StringBuilder();
        leader.append("00000");             // pos 00–04: record length (fixed)
        leader.append("n");                 // pos 05: record status (fixed)
        leader.append(typeOfRecord);        // pos 06: type of record
        leader.append(bibliographicLevel);  // pos 07: bibliographic level
        leader.append(" ");                 // pos 08: type of control (fixed)
        leader.append("a");                 // pos 09: character coding scheme (fixed)
        leader.append("22");                // pos 10–11: encoding level (fixed)
        leader.append("00000");             // pos 12–16: base address of data (fixed)
        leader.append("3u");                // pos 17–18: encoding level, descriptive cataloging form (fixed)
        leader.append(" ");                 // pos 19: multipart resource record level (fixed)
        leader.append("4500");             // pos 20–23: (fixed)
        return new String[] { leader.toString() };
    }

    public void setTypeOfRecord(String typeOfRecord) {
        this.typeOfRecord = typeOfRecord;
    }

    public void setBibliographicLevel(String bibliographicLevel) {
        this.bibliographicLevel = bibliographicLevel;
    }

    public void setDefaultTypeOfRecord(String defaultTypeOfRecord) {
        this.defaultTypeOfRecord = defaultTypeOfRecord;
    }

    public void setDefaultBibliographicLevel(String defaultBibliographicLevel) {
        this.defaultBibliographicLevel = defaultBibliographicLevel;
    }

    public void setTypeOfRecordMap(Map<String, String> typeOfRecordMap) {
        this.typeOfRecordMap = typeOfRecordMap;
    }

    public void setBibliographicLevelMap(Map<String, String> bibliographicLevelMap) {
        this.bibliographicLevelMap = bibliographicLevelMap;
    }

    public String getTypeOfRecord(String type) {
        return getTypeOfRecordMatchByStartsWith(typeOfRecordMap, type);
    }

    public String getBibliographicLevel(String type) {
        return getBibliographicLevelMatchByStartsWith(bibliographicLevelMap, type);
    }

    private String getTypeOfRecordMatchByStartsWith(Map<String,String> typeOfRecordMap, String typeOfRecord ) {
        if (typeOfRecordMap == null || typeOfRecord == null) {
            return defaultTypeOfRecord;
        }
        for (Map.Entry<String, String> entry : typeOfRecordMap.entrySet()) {
            if (typeOfRecord.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return defaultTypeOfRecord;
    }

    private String getBibliographicLevelMatchByStartsWith(Map<String,String> bibliographicLevelMap,
                                                          String bibliographicLevel ) {
        if (bibliographicLevelMap == null || bibliographicLevel == null) {
            return defaultBibliographicLevel;
        }
        for (Map.Entry<String, String> entry : bibliographicLevelMap.entrySet()) {
            if (bibliographicLevel.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return defaultBibliographicLevel;
    }


}
