/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks.virtualfields;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link VirtualField} that, given a configurable metadatum, if many occurrences
 * are present, returns the value in the most relevant language (defined in default.locale property)
 *
 * @author Daniele Ninfo (daniele.ninfo at 4science.com)
 *
 */
public class VirtualFieldUniqueMetadata implements VirtualField {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ConfigurationService configurationService;

    private String metadatum;

    @Override
    public String[] getMetadata(Context context, Item item, String fieldName) {
        String defaultLocale = configurationService.getProperty("default.locale");
        List<MetadataValue> metadata = itemService.getMetadataByMetadataString(item, metadatum);
        Optional<String> uniqueValue = Optional.empty();
        if (StringUtils.isNotBlank(defaultLocale)) {
            uniqueValue = metadata.stream()
                .filter(mv -> StringUtils.equalsIgnoreCase(defaultLocale, mv.getLanguage()))
                .map(mv -> mv.getValue())
                .findFirst();
        }
        String[] resultValues;
        if (uniqueValue.isPresent()) {
            resultValues = new String[] {uniqueValue.get()};
        } else if (!metadata.isEmpty()) {
            resultValues = new String[] {metadata.get(0).getValue()};
        } else {
            resultValues = new String[0];
        }
        return resultValues;
    }

    public String getMetadatum() {
        return metadatum;
    }

    public void setMetadatum(String metadatum) {
        this.metadatum = metadatum;
    }

}
