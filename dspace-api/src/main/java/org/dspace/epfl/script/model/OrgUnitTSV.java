/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

public class OrgUnitTSV {

    private final String[] headers;

    private final List<OrgUnitRow> rows;

    public OrgUnitTSV(String[] headers, List<OrgUnitRow> rows) {
        this.headers = headers;
        this.rows = rows;
    }

    public String[] getHeaders() {
        return headers;
    }

    public List<OrgUnitRow> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public static class OrgUnitRow {

        private final Map<String, String> values;

        private final int index;

        public OrgUnitRow(Map<String, String> values, int index) {
            this.values = values;
            this.index = index;
        }

        public Optional<String> getValue(String header) {
            return Optional.ofNullable(values.get(header))
                .filter(StringUtils::isNotBlank);
        }

        public int getIndex() {
            return index;
        }

    }

}
