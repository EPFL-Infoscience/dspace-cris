/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class that provides methods to check if a given string is an ORCID ID.
 * 
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class OrcidCheck {

    // Regular expression to check whether an input string is a valid ORCID id.
    private static final String ORCID_REGEX =
            "^\\s*(?:(?:https?://)?orcid.org/)?[0-9]{4}\\-[0-9]{4}\\-[0-9]{4}\\-[0-9]{3}[0-9X]{1}\\s*$";

    private OrcidCheck() {}

    public static boolean isOrcid(final String value) {
        Pattern ORCID_PATTERN = Pattern.compile(ORCID_REGEX);
        Matcher m = ORCID_PATTERN.matcher(value);
        return m.matches();
    }

}