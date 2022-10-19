/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks.virtualfields;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A specific implementation for EPFL.
 * To ensure orgunit information of author in epfl-publication-marc-xml.template
 * <datafield tag="909" ind1="C" ind2="0">
 *     <subfield code="p">ACRONYM</subfield>
 *     <subfield code="x">U14214</subfield>
 * </datafield>
 * 
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com) 
 */
public class VirtualEPFLOrgunitFields implements VirtualField {

    private final static Logger log = LoggerFactory.getLogger(VirtualEPFLOrgunitFields.class);

    private final String START = "<datafield tag=\"909\" ind1=\"C\" ind2=\"0\">";
    private final String END = "</datafield>";

    @Autowired
    private ItemService itemService;

    @Override
    public String[] getMetadata(Context context, Item item, String fieldName) {
        List<MetadataValue> authors = itemService.getMetadata(item, "dc", "contributor", "author", Item.ANY);
        String [] result = new String [authors.size()];
        int count = 0;
        for (MetadataValue mv : authors) {
            UUID uuid = StringUtils.isNotBlank(mv.getAuthority()) ? UUID.fromString(mv.getAuthority()) : null;
            Item author = Objects.nonNull(uuid) ? findItem(context, uuid) : null;
            if (Objects.nonNull(author)) {
                List<MetadataValue> orgunits = itemService.getMetadata(author, "person", "affiliation", "name", null);
                String uuidOrgUnit = CollectionUtils.isNotEmpty(orgunits) ? orgunits.get(0).getAuthority() : null;
                Item affiliationOfAuthor = StringUtils.isNotBlank(uuidOrgUnit) ? 
                               findItem(context, UUID.fromString(uuidOrgUnit)) : null;
                if (Objects.nonNull(affiliationOfAuthor)) {
                    String acronym = getMetadataFirstValue(affiliationOfAuthor, "oairecerif", "acronym", null);
                    String legacyId = getMetadataFirstValue(affiliationOfAuthor, "cris", "legacyId", null);
                    if (StringUtils.isNotBlank(acronym) || StringUtils.isNotBlank(legacyId)) {
                        makeElement(acronym, legacyId, result, count);
                    }
                }
            }
            count++;
        }
        return result;
    }

    private void makeElement(String acronym, String legacyId, String[] result, int count) {
        StringBuilder element = new StringBuilder();
        element.append(START);
        if (StringUtils.isNotBlank(acronym)) {
            element.append("<subfield code=\"p\">").append(acronym).append("</subfield>");
        }
        if (StringUtils.isNotBlank(legacyId)) {
            element.append("<subfield code=\"x\">").append("U" + legacyId).append("</subfield>");
        }
        element.append(END);
        result[count] = element.toString();
    }

    private Item findItem(Context context, UUID uuid) {
        Item item = null;
        try {
             item = itemService.find(context, uuid);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        return item;
    }

    private String getMetadataFirstValue(Item item, String schema, String element, String qualifier) {
        return itemService.getMetadataFirstValue(item, schema, element, qualifier, Item.ANY);
    }

}