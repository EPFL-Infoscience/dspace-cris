/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.citation;

/**
 * Holds the result of generating a citation for a single item:
 * both the formatted citation string and the CSL JSON representation.
 *
 * @author Daniele Ninfo (daniele.ninfo at 4science.com)
 */
public class CitationResult {

    private final String citation;
    private final String cslJson;

    public CitationResult(String citation, String cslJson) {
        this.citation = citation;
        this.cslJson = cslJson;
    }

    /**
     * Returns the formatted citation string (e.g. HTML or plain text depending on format).
     */
    public String getCitation() {
        return citation;
    }

    /**
     * Returns the CSL JSON representation of the item.
     */
    public String getCslJson() {
        return cslJson;
    }
}
