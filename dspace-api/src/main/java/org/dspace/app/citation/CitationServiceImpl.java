/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.citation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.integration.crosswalks.CSLItemDataCrosswalk;
import org.dspace.content.integration.crosswalks.StreamDisseminationCrosswalkMapper;
import org.dspace.content.integration.crosswalks.csl.CSLPreparedItemData;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link CitationService}.
 *
 * @author Daniele Ninfo (daniele.ninfo at 4science.com)
 */
public class CitationServiceImpl implements CitationService {

    private static final Logger log = LogManager.getLogger(CitationServiceImpl.class);

    @Autowired
    private ItemService itemService;

    @Autowired
    private StreamDisseminationCrosswalkMapper streamDisseminationCrosswalkMapper;

    @Override
    public String generateCitation(Context context, Item item, String style) {
        String entityType = itemService.getEntityType(item);
        if (!isSupportedEntityType(entityType)) {
            return null;
        }

        String crosswalkType = entityType.toLowerCase(Locale.ROOT) + "-" + normalizeStyle(style);
        CSLItemDataCrosswalk crosswalk = getCrosswalk(crosswalkType);
        if (crosswalk == null) {
            log.warn("No crosswalk found for type '{}' - cannot generate citation", crosswalkType);
            return null;
        }

        try {
            CSLPreparedItemData prepared = crosswalk.prepareItemData(context, item);
            return crosswalk.generateCitation(prepared);
        } catch (CrosswalkException e) {
            log.error("Error generating citation for item {} with style '{}'", item.getID(), style, e);
            return null;
        }
    }

    @Override
    public String generateCslJson(Context context, Item item) {
        String entityType = itemService.getEntityType(item);
        if (!isSupportedEntityType(entityType)) {
            return null;
        }

        // Use "apa" as default — prepareItemData produces the same CSL JSON regardless of style
        String crosswalkType = entityType.toLowerCase(Locale.ROOT) + "-apa";
        CSLItemDataCrosswalk crosswalk = getCrosswalk(crosswalkType);
        if (crosswalk == null) {
            log.warn("No crosswalk found for '{}' - cannot generate CSL JSON", crosswalkType);
            return null;
        }

        try {
            CSLPreparedItemData prepared = crosswalk.prepareItemData(context, item);
            return prepared.getJson();
        } catch (CrosswalkException e) {
            log.error("Error generating CSL JSON for item {}", item.getID(), e);
            return null;
        }
    }

    /**
     * Generates all citations for the given styles, plus the CSL JSON.
     *
     * @param context the DSpace context
     * @param item    the item
     * @param styles  the style suffixes to generate (e.g. "apa", "chicago", "ieee")
     * @return a map from qualifier name (style or "cslitem") to the generated value
     */
    @Override
    public Map<String, String> generateAllCitations(Context context, Item item, String[] styles) {
        Map<String, String> results = new LinkedHashMap<>();

        String entityType = itemService.getEntityType(item);
        if (!isSupportedEntityType(entityType)) {
            return results;
        }

        String entityPrefix = entityType.toLowerCase(Locale.ROOT) + "-";

        if (styles == null || styles.length == 0) {
            return results;
        }

        // First style: use it to get both CSL JSON and its citation
        CSLItemDataCrosswalk firstCrosswalk = getCrosswalk(entityPrefix + styles[0]);
        if (firstCrosswalk != null) {
            try {
                CSLPreparedItemData prepared = firstCrosswalk.prepareItemData(context, item);
                results.put("cslitem", prepared.getJson());
                String citation = firstCrosswalk.generateCitation(prepared);
                if (citation != null) {
                    results.put(styles[0], citation);
                }
            } catch (Exception e) {
                log.warn("Error generating citation for item {} with style '{}': {}",
                        item.getID(), styles[0], e.getMessage());
            }
        }

        // Remaining styles
        for (int i = 1; i < styles.length; i++) {
            String style = styles[i];
            CSLItemDataCrosswalk crosswalk = getCrosswalk(entityPrefix + style);
            if (crosswalk != null) {
                try {
                    CSLPreparedItemData prepared = crosswalk.prepareItemData(context, item);
                    String citation = crosswalk.generateCitation(prepared);
                    if (citation != null) {
                        results.put(style, citation);
                    }
                } catch (Exception e) {
                    log.warn("Error generating citation for item {} with style '{}': {}",
                            item.getID(), style, e.getMessage());
                }
            }
        }

        return results;
    }

    private CSLItemDataCrosswalk getCrosswalk(String type) {
        var crosswalk = streamDisseminationCrosswalkMapper.getByType(type);
        if (crosswalk instanceof CSLItemDataCrosswalk) {
            return (CSLItemDataCrosswalk) crosswalk;
        }
        return null;
    }

    private String normalizeStyle(String style) {
        String normalized = style.trim();
        return StringUtils.removeEndIgnoreCase(normalized, ".csl").toLowerCase(Locale.ROOT);
    }

    private boolean isSupportedEntityType(String entityType) {
        return StringUtils.isNotBlank(entityType)
            && ("Publication".equals(entityType) || "Patent".equals(entityType) || "Product".equals(entityType));
    }
}
