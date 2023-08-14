/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.client.model;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

public class OrgUnitDTO {

    private Integer code;

    private String name;

    private String acronym;

    private String unitPath;

    private OrgUnitHeadDTO head;

    private OrgUnitPathDTO[] path;

    public boolean isNotEmpty() {
        return StringUtils.isNotBlank(acronym);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAcronym() {
        return acronym;
    }

    public void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    public OrgUnitHeadDTO getHead() {
        return head;
    }

    public void setHead(OrgUnitHeadDTO head) {
        this.head = head;
    }

    public OrgUnitPathDTO[] getPath() {
        return path;
    }

    public void setPath(OrgUnitPathDTO[] path) {
        this.path = path;
    }

    public String getUnitPath() {
        return unitPath;
    }

    public void setUnitPath(String unitPath) {
        this.unitPath = unitPath;
    }

    public static class OrgUnitPathDTO {

        private String acronym;

        private String name;

        public String getAcronym() {
            return acronym;
        }

        public void setAcronym(String acronym) {
            this.acronym = acronym;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }

    public static class OrgUnitHeadDTO {

        private String sciper;

        private String name;

        private String firstname;

        private String email;

        public String getFullName() {
            return Stream.of(name, firstname)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
        }

        public String getSciper() {
            return sciper;
        }

        public void setSciper(String sciper) {
            this.sciper = sciper;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFirstname() {
            return firstname;
        }

        public void setFirstname(String firstname) {
            this.firstname = firstname;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getEmail() {
            return email;
        }
    }
}
