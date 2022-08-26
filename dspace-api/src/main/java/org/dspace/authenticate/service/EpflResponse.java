/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate.service;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EpflResponse {

    private List<EpflResult> result;

    public List<EpflResult> getResult() {
        return result == null ? List.of() : result;
    }

    public void setResult(List<EpflResult> result) {
        this.result = result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EpflResult {

        private Long author;

        private Long classid;

        private String comment;

        private String creator;

        private String datecreat;

        private String datedeb;

        private String datefin;

        private String datereval;

        private String debval;

        private String duree;

        private String finval;

        private Long ordre;

        private String origine;

        private Long persid;

        private Long posid;

        private String revalman;

        private Long statusid;

        private Long unitid;

        public Long getAuthor() {
            return author;
        }

        public void setAuthor(Long author) {
            this.author = author;
        }

        public Long getClassid() {
            return classid;
        }

        public void setClassid(Long classid) {
            this.classid = classid;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public String getCreator() {
            return creator;
        }

        public void setCreator(String creator) {
            this.creator = creator;
        }

        public String getDatecreat() {
            return datecreat;
        }

        public void setDatecreat(String datecreat) {
            this.datecreat = datecreat;
        }

        public String getDatedeb() {
            return datedeb;
        }

        public void setDatedeb(String datedeb) {
            this.datedeb = datedeb;
        }

        public String getDatefin() {
            return datefin;
        }

        public void setDatefin(String datefin) {
            this.datefin = datefin;
        }

        public String getDatereval() {
            return datereval;
        }

        public void setDatereval(String datereval) {
            this.datereval = datereval;
        }

        public String getDebval() {
            return debval;
        }

        public void setDebval(String debval) {
            this.debval = debval;
        }

        public String getDuree() {
            return duree;
        }

        public void setDuree(String duree) {
            this.duree = duree;
        }

        public String getFinval() {
            return finval;
        }

        public void setFinval(String finval) {
            this.finval = finval;
        }

        public Long getOrdre() {
            return ordre;
        }

        public void setOrdre(Long ordre) {
            this.ordre = ordre;
        }

        public String getOrigine() {
            return origine;
        }

        public void setOrigine(String origine) {
            this.origine = origine;
        }

        public Long getPersid() {
            return persid;
        }

        public void setPersid(Long persid) {
            this.persid = persid;
        }

        public Long getPosid() {
            return posid;
        }

        public void setPosid(Long posid) {
            this.posid = posid;
        }

        public String getRevalman() {
            return revalman;
        }

        public void setRevalman(String revalman) {
            this.revalman = revalman;
        }

        public Long getStatusid() {
            return statusid;
        }

        public void setStatusid(Long statusid) {
            this.statusid = statusid;
        }

        public Long getUnitid() {
            return unitid;
        }

        public void setUnitid(Long unitid) {
            this.unitid = unitid;
        }

    }
}
