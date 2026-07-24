/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.citation;

import java.util.Map;

import org.dspace.content.Item;
import org.dspace.core.Context;

/**
 * Service for generating CSL citations for items.
 * Encapsulates the logic of resolving the appropriate crosswalk per entity type
 * and producing formatted citations.
 *
 * @author Daniele Ninfo (daniele.ninfo at 4science.com)
 */
public interface CitationService {

    /**
     * Generates a citation for the given item using the specified style.
     * The style is resolved to a crosswalk based on the item's entity type
     * (e.g. style "apa" + entity type "Publication" → crosswalk "publication-apa").
     *
     * @param context the DSpace context
     * @param item    the item to generate a citation for
     * @param style   the crosswalk style suffix (e.g. "apa", "chicago")
     * @return the formatted citation string, or null if the item's entity type is not supported
     */
    String generateCitation(Context context, Item item, String style);

    /**
     * Generates the CSL JSON intermediate representation for the given item.
     * Uses any available style crosswalk (the JSON is the same regardless of style).
     *
     * @param context the DSpace context
     * @param item    the item
     * @return the CSL JSON string, or null if the item's entity type is not supported
     */
    String generateCslJson(Context context, Item item);

    /**
     * Generates all citations configured for the item's entity type, plus the CSL JSON.
     * The styles are read from the configuration keys "citation-filter.&lt;entityType&gt;".
     *
     * @param context the DSpace context
     * @param item    the item
     * @return a map from qualifier name (style or "cslitem") to the generated value,
     *         or an empty map if the item's entity type is not supported.
     *         The style keys match the qualifier in epfl.citation.&lt;qualifier&gt;.
     */
    Map<String, String> generateAllCitations(Context context, Item item);
}
