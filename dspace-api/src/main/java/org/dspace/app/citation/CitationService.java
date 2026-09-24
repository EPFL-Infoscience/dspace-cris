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
     * Generates all citations for the given styles, plus the CSL JSON intermediate representation.
     *
     * @param context the DSpace context
     * @param item    the item
     * @param styles  the style suffixes to generate (e.g. "apa", "chicago", "ieee").
     *                Each suffix is combined with the entity type prefix.
     * @return a map from qualifier name (style or "cslitem") to the generated value,
     *         or an empty map if the item's entity type is not supported.
     */
    Map<String, String> generateAllCitations(Context context, Item item, String[] styles);

    /**
     * Generates a citation and the CSL JSON for the given item and style, in a single pass.
     * This avoids preparing item data twice when both the citation and the JSON are needed.
     *
     * @param context the DSpace context
     * @param item    the item
     * @param style   the crosswalk style suffix (e.g. "apa", "chicago")
     * @return a {@link CitationResult} containing both the formatted citation and the CSL JSON,
     *         or null if the item's entity type is not supported or no crosswalk is found.
     */
    CitationResult generateCitationAndCslJson(Context context, Item item, String style);

    /**
     * Checks whether the given entity type is supported for citation generation.
     *
     * @param entityType the entity type string (e.g. "Publication", "Patent", "Product")
     * @return true if citations can be generated for this entity type
     */
    boolean isSupportedEntityType(String entityType);
}
