/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks.virtualfields;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

public class VirtualFieldOpenAireAlternativeDOI implements VirtualField {

    private static final String DOI_METADATA = "dc.identifier.doi";

    @Autowired
    private ItemDOIService itemDOIService;

    @Autowired
    private ItemService itemService;

    @Override
    public String[] getMetadata(Context context, Item item, String fieldName) {
        String[] qualifiers = StringUtils.split(fieldName, ".");
        if (qualifiers.length != 3) {
            throw new IllegalArgumentException("Invalid field name " + fieldName);
        }

        String localPrimaryDoi = itemDOIService.getPrimaryLocalDOIFromItem(item);

        List<String> dois = itemService.getMetadataByMetadataString(item, DOI_METADATA).stream()
                .map(MetadataValue::getValue)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        if (StringUtils.isNotBlank(localPrimaryDoi)) {
            dois = dois.stream()
                    .filter(d -> !d.equals(localPrimaryDoi))
                    .collect(Collectors.toList());
        }

        return dois.toArray(String[]::new);
    }
}