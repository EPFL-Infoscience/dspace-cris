/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service.impl;

import static org.apache.commons.lang3.ArrayUtils.contains;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.dspace.epfl.script.model.OrgUnitTSV;
import org.dspace.epfl.script.model.OrgUnitTSV.OrgUnitRow;
import org.dspace.epfl.script.service.OrgUnitTSVParser;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

public class OrgUnitTSVParserImpl implements OrgUnitTSVParser {

    @Autowired
    private ConfigurationService configurationService;

    @Override
    public OrgUnitTSV parseTSV(InputStream inputStream) {

        List<String> lines = readLines(inputStream);

        String[] headers = readHeaders(lines);
        List<OrgUnitRow> rows = readRows(lines, headers);

        OrgUnitTSV orgUnitTSV = new OrgUnitTSV(headers, rows);

        validateTSV(orgUnitTSV);

        return orgUnitTSV;
    }

    private List<String> readLines(InputStream inputStream) {
        try {
            return IOUtils.readLines(inputStream, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String[] readHeaders(List<String> lines) {

        if (CollectionUtils.isEmpty(lines)) {
            throw new IllegalArgumentException("Cannot import an empty TSV");
        }

        return getValuesFromLine(lines.get(0));

    }

    private List<OrgUnitRow> readRows(List<String> lines, String[] headers) {

        List<OrgUnitRow> orgUnitRows = new ArrayList<>();

        int lineCount = 0;
        Iterator<String> linesIterator = lines.iterator();

        while (linesIterator.hasNext()) {

            String line = linesIterator.next();

            if (lineCount == 0) {
                lineCount++;
                continue;
            }

            lineCount++;

            orgUnitRows.add(readRow(line, lineCount, headers));

        }

        return orgUnitRows;
    }

    private OrgUnitRow readRow(String line, int lineCount, String[] headers) {

        String[] lineValues = getValuesFromLine(line);

        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String value = lineValues.length > i ? lineValues[i] : "";
            values.put(headers[i], value);
        }

        return new OrgUnitRow(values, lineCount);
    }

    private String[] getValuesFromLine(String line) {
        return line.split("\t");
    }

    private void validateTSV(OrgUnitTSV orgUnitTSV) {

        String[] headers = orgUnitTSV.getHeaders();

        Object activeOrgUnitAcronymHeader = getActiveOrgUnitAcronymHeader();
        Object inactiveOrgUnitAcronymHeader = getInactiveOrgUnitAcronymHeader();

        if (!contains(headers, activeOrgUnitAcronymHeader) && !contains(headers, inactiveOrgUnitAcronymHeader)) {
            throw new IllegalArgumentException("The TSV must have at least one of those two headers: "
                + activeOrgUnitAcronymHeader + " or " + inactiveOrgUnitAcronymHeader);
        }

    }

    private String getActiveOrgUnitAcronymHeader() {
        return configurationService.getProperty("epfl.orgunit-import.active-orgunit.acronym-header");
    }

    private String getInactiveOrgUnitAcronymHeader() {
        return configurationService.getProperty("epfl.orgunit-import.inactive-orgunit.acronym-header");
    }

}
