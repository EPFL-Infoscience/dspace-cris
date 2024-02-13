/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.model;

public class OrgUnitXMLToTSV {

    private String infoscienceAuthNum;
    private String infoscienceUnitCode;
    private String unitCode;
    private String unitNameFr;
    private String unitNameEn;
    private String unitsAcro;
    private String unitAcroEn;
    private String unitParentCode;
    private String unitParentAcronym;
    private String unitUrl;
    private String headSciperid;
    private String headFirstname;
    private String headLastname;
    private String unitStatus;
    private String unitsType;

    public String getInfoscienceAuthNum() {
        return infoscienceAuthNum;
    }

    public void setInfoscienceAuthNum(String infoscienceAuthNum) {
        this.infoscienceAuthNum = infoscienceAuthNum;
    }

    public String getInfoscienceUnitCode() {
        return infoscienceUnitCode;
    }

    public void setInfoscienceUnitCode(String infoscienceUnitCode) {
        this.infoscienceUnitCode = infoscienceUnitCode;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    }

    public String getUnitNameFr() {
        return unitNameFr;
    }

    public void setUnitNameFr(String unitNameFr) {
        this.unitNameFr = unitNameFr;
    }

    public String getUnitNameEn() {
        return unitNameEn;
    }

    public void setUnitNameEn(String unitNameEn) {
        this.unitNameEn = unitNameEn;
    }

    public String getUnitsAcro() {
        return unitsAcro;
    }

    public void setUnitsAcro(String unitsAcro) {
        this.unitsAcro = unitsAcro;
    }

    public String getUnitAcroEn() {
        return unitAcroEn;
    }

    public void setUnitAcroEn(String unitAcroEn) {
        this.unitAcroEn = unitAcroEn;
    }

    public String getUnitParentCode() {
        return unitParentCode;
    }

    public void setUnitParentCode(String unitParentCode) {
        this.unitParentCode = unitParentCode;
    }

    public String getUnitParentAcronym() {
        return unitParentAcronym;
    }

    public void setUnitParentAcronym(String unitParentAcronym) {
        this.unitParentAcronym = unitParentAcronym;
    }

    public String getUnitUrl() {
        return unitUrl;
    }

    public void setUnitUrl(String unitUrl) {
        this.unitUrl = unitUrl;
    }

    public String getHeadSciperid() {
        return headSciperid;
    }

    public void setHeadSciperid(String headSciperid) {
        this.headSciperid = headSciperid;
    }

    public String getHeadFirstname() {
        return headFirstname;
    }

    public void setHeadFirstname(String headFirstname) {
        this.headFirstname = headFirstname;
    }

    public String getHeadLastname() {
        return headLastname;
    }

    public void setHeadLastname(String headLastname) {
        this.headLastname = headLastname;
    }

    public String getUnitStatus() {
        return unitStatus;
    }

    public void setUnitStatus(String unitStatus) {
        this.unitStatus = unitStatus;
    }

    public String getUnitsType() {
        return unitsType;
    }

    public void setUnitsType(String unitsType) {
        this.unitsType = unitsType;
    }

    @Override
    public String toString() {
        return "OrgUnitXMLToTSV [infoscienceAuthNum=" + infoscienceAuthNum + ", infoscienceUnitCode="
                + infoscienceUnitCode + ", unitCode=" + unitCode + ", unitNameFr=" + unitNameFr + ", unitNameEn="
                + unitNameEn + ", unitsAcro=" + unitsAcro + ", unitAcroEn=" + unitAcroEn + ", unitParentCode="
                + unitParentCode + ", unitParentAcronym=" + unitParentAcronym + ", unitUrl=" + unitUrl
                + ", headSciperid=" + headSciperid + ", headFirstname=" + headFirstname + ", headLastname="
                + headLastname + ", unitStatus=" + unitStatus + ", unitsType=" + unitsType + "]";
    }

}
