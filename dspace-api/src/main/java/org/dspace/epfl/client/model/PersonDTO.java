/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.client.model;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

public class PersonDTO {

    private String name;

    private String firstname;

    private String email;

    private String sciper;

    private Integer rank;

    private String profile;

    private Accred[] accreds;

    public String getFullName() {
        return Stream.of(name, firstname)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.joining(", "));
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSciper() {
        return sciper;
    }

    public void setSciper(String sciper) {
        this.sciper = sciper;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public Accred[] getAccreds() {
        return accreds;
    }

    public void setAccreds(Accred[] accreds) {
        this.accreds = accreds;
    }


    public static class Accred {

        private String acronym;

        private String path;

        private String name;

        private String position;

        private List<String> phoneList;

        private List<String> officeList;

        private Integer rank;

        private Integer code;

        public String getAcronym() {
            return acronym;
        }

        public void setAcronym(String acronym) {
            this.acronym = acronym;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public List<String> getPhoneList() {
            return phoneList;
        }

        public void setPhoneList(List<String> phoneList) {
            this.phoneList = phoneList;
        }

        public List<String> getOfficeList() {
            return officeList;
        }

        public void setOfficeList(List<String> officeList) {
            this.officeList = officeList;
        }

        public Integer getRank() {
            return rank;
        }

        public void setRank(Integer rank) {
            this.rank = rank;
        }

        public Integer getCode() {
            return code;
        }

        public void setCode(Integer code) {
            this.code = code;
        }

    }
}
