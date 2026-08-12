/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks.csl;

/**
 * Holds prepared item data for CSL citation generation: both the CSL JSON
 * representation and the {@link DSpaceListItemDataProvider} used to produce it.
 * This allows callers to inspect the JSON (e.g. to extract type/year) and then
 * generate the final citation without re-processing the item.
 *
 * @author Daniele Ninfo (daniele.ninfo at 4science.com)
 *
 */
public class CSLPreparedItemData {

    private final String json;
    private final DSpaceListItemDataProvider provider;

    public CSLPreparedItemData(String json, DSpaceListItemDataProvider provider) {
        this.json = json;
        this.provider = provider;
    }

    /**
     * Returns the CSL JSON representation of the processed items.
     *
     * @return the CSL JSON string
     */
    public String getJson() {
        return json;
    }

    /**
     * Returns the item data provider that can be passed to a {@link CSLGenerator}
     * to produce the final citation.
     *
     * @return the provider instance
     */
    public DSpaceListItemDataProvider getProvider() {
        return provider;
    }
}
