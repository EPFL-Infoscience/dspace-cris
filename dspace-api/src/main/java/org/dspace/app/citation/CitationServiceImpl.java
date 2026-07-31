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
        CitationResult result = generateCitationAndCslJson(context, item, style);
        return result != null ? result.getCitation() : null;
    }

    @Override
    public String generateCslJson(Context context, Item item) {
        CitationResult result = generateCitationAndCslJson(context, item, "apa");
        return result != null ? result.getCslJson() : null;
    }

    @Override
    public Map<String, String> generateAllCitations(Context context, Item item, String[] styles) {
        Map<String, String> results = new LinkedHashMap<>();
        if (styles == null || styles.length == 0) {
            return results;
        }

        for (String style : styles) {
            CitationResult result = generateCitationAndCslJson(context, item, style);
            if (result != null) {
                if (!results.containsKey("cslitem") && result.getCslJson() != null) {
                    results.put("cslitem", result.getCslJson());
                }
                if (result.getCitation() != null) {
                    results.put(style, result.getCitation());
                }
            }
        }

        return results;
    }

    @Override
    public CitationResult generateCitationAndCslJson(Context context, Item item, String style) {
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
            String cslJson = prepared.getJson();
            String citation = crosswalk.generateCitation(prepared);
            return new CitationResult(citation, cslJson);
        } catch (Exception e) {
            log.warn("Error generating citation for item {} with style '{}': {}",
                    item.getID(), style, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isSupportedEntityType(String entityType) {
        return StringUtils.isNotBlank(entityType)
            && ("Publication".equals(entityType) || "Patent".equals(entityType) || "Product".equals(entityType));
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
}
