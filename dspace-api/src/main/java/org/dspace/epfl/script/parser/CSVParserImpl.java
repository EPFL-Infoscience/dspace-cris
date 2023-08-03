/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.parser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CSVParserImpl implements CSVParser<List<String>> {

    public List<String> parseCSV(InputStream inputStream, String csvSeparator) {
        List<String> parsedCSV = new ArrayList<>();

        Scanner scanner = new Scanner(inputStream);
        scanner.useDelimiter(csvSeparator);

        while (scanner.hasNext()) {
            parsedCSV.add(scanner.next());
        }
        parsedCSV.set(parsedCSV.size() - 1, parsedCSV.get(parsedCSV.size() - 1).replace("\n", ""));

        return parsedCSV;
    }
}
