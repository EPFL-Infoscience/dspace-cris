/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.metadatamapping.contributor;

import java.util.Collection;

import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.jdom2.Element;

/**
 * A metadata contributor that extends the basic XPath and attribute filtering functionality
 * with a regex-based cleanup capability for the extracted values.
 */
public class RegexCleanupMetadataContributor extends SimpleXpathMetadatumAndAttributeAndSubNodeContributor {

    private String cleanupRegex = ",\\s+$";  // Default regex to remove trailing comma and spaces
    private String replacementValue = "";   // Default replacement value

    @Override
    public Collection<MetadatumDTO> contributeMetadata(Element t) {
        // Use the base class method to initially fetch the metadata
        Collection<MetadatumDTO> metadata = super.contributeMetadata(t);
        // Apply the regex cleanup to each metadata item's value
        for (MetadatumDTO datum : metadata) {
            datum.setValue(cleanValue(datum.getValue()));
        }
        return metadata;
    }

    /**
     * Cleans up the metadata value using the specified regex.
     * 
     * @param value The original metadata value
     * @return A cleaned-up metadata value
     */
    private String cleanValue(String value) {
        if (value != null) {
            return value.replaceAll(cleanupRegex, replacementValue).trim();
        }
        return value;
    }

    /**
     * Sets the regex used for cleaning up the metadata values.
     * 
     * @param regex The regex pattern to use for cleanup
     */
    public void setCleanupRegex(String regex) {
        this.cleanupRegex = regex;
    }

    /**
     * Sets the replacement value used in the regex cleanup.
     * 
     * @param replacement The string to replace matches found using the regex
     */
    public void setReplacementValue(String replacement) {
        this.replacementValue = replacement;
    }
}