/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.parser;

import java.io.InputStream;

public interface CSVParser<T> {

    T parseCSV(InputStream inputStream, String csvSeparator);
}
