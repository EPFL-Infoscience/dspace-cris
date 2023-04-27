/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.service.impl;

import static java.util.Optional.ofNullable;
import static org.dspace.authority.service.AuthorityValueService.GENERATE;
import static org.dspace.authority.service.AuthorityValueService.REFERENCE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.authority.Choices;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.epfl.client.OrgUnitApiClient;
import org.dspace.epfl.client.OrgUnitApiClient.Language;
import org.dspace.epfl.client.model.OrgUnitDTO;
import org.dspace.epfl.client.model.OrgUnitDTO.OrgUnitHeadDTO;
import org.dspace.epfl.client.model.OrgUnitDTO.OrgUnitPathDTO;
import org.dspace.epfl.service.OrgUnitApiService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

public class OrgUnitApiServiceImpl implements OrgUnitApiService {

    @Autowired
    private OrgUnitApiClient orgUnitApiClient;

    @Autowired
    private ConfigurationService configurationService;

    @Override
    public boolean isOrgUnitActive(String acronym) {
        return orgUnitApiClient.isOrgUnitActive(acronym);
    }

    @Override
    public List<MetadataValueDTO> getMetadataValues(String orgUnitAcronym) {

        Optional<OrgUnitDTO> orgUnitEnglish = orgUnitApiClient.getOrgUnit(orgUnitAcronym, Language.EN);
        Optional<OrgUnitDTO> orgUnitFrench = orgUnitApiClient.getOrgUnit(orgUnitAcronym, Language.FR);

        if (orgUnitEnglish.isEmpty() && orgUnitFrench.isEmpty()) {
            return List.of();
        }

        String englishLanguage = getEnglishMetadataFieldLanguage();
        String frenchLanguage = getFrenchMetadataFieldLanguage();

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        orgUnitEnglish.map(orgUnit -> getMetadataValuesWithLanguage(orgUnit, englishLanguage))
            .ifPresent(metadataValues::addAll);

        orgUnitFrench.map(orgUnit -> getMetadataValuesWithLanguage(orgUnit, frenchLanguage))
            .ifPresent(metadataValues::addAll);

        orgUnitEnglish.or(() -> orgUnitFrench)
            .map(orgUnit -> getMetadataValuesWithoutLanguage(orgUnit))
            .ifPresent(metadataValues::addAll);

        return metadataValues;
    }

    private List<MetadataValueDTO> getMetadataValuesWithLanguage(OrgUnitDTO orgUnit, String language) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        getOrgUnitNameMetadataField()
            .flatMap(field -> getMetadataValue(orgUnit.getName(), field, language))
            .ifPresent(metadataValues::add);

        getOrgUnitAcronymMetadataField()
            .flatMap(field -> getMetadataValue(orgUnit.getAcronym(), field, language))
            .ifPresent(metadataValues::add);

        return metadataValues;

    }

    private List<MetadataValueDTO> getMetadataValuesWithoutLanguage(OrgUnitDTO orgUnit) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        getOrgUnitCodeMetadataField()
            .flatMap(field -> getMetadataValue(orgUnit.getCode(), field, null))
            .ifPresent(metadataValues::add);

        getOrgUnitHeadMetadataField()
            .flatMap(field -> getHeadMetadataValue(orgUnit, field))
            .ifPresent(metadataValues::add);

        getOrgUnitParentMetadataField()
            .flatMap(field -> getParentMetadataValue(orgUnit, field))
            .ifPresent(metadataValues::add);

        return metadataValues;

    }

    private Optional<MetadataValueDTO> getHeadMetadataValue(OrgUnitDTO orgUnit, String field) {

        OrgUnitHeadDTO head = orgUnit.getHead();
        if (head == null) {
            return Optional.empty();
        }

        String fullName = head.getFullName();
        if (StringUtils.isBlank(fullName)) {
            return Optional.empty();
        }
        
        String authority = null;
        int confidence = Choices.CF_UNSET;
        
        String sciperId = head.getSciper();
        
        if(StringUtils.isNotBlank(sciperId)) {
            authority = getOrgUnitHeadAuthorityPrefix()
                .map(prefix -> prefix + sciperId)
                .orElse(sciperId);
            confidence = Choices.CF_AMBIGUOUS;
        }
        
        return Optional.of(new MetadataValueDTO(field, fullName, authority, confidence));

    }

    private Optional<MetadataValueDTO> getParentMetadataValue(OrgUnitDTO orgUnit, String field) {

        String parentName = null;
        String parentAcronym = null;

        OrgUnitPathDTO[] path = orgUnit.getPath();
        if (ArrayUtils.isNotEmpty(path) && path.length > 1) {
            OrgUnitPathDTO parentOrgUnit = path[path.length - 2];
            parentName = parentOrgUnit.getName();
            parentAcronym = parentOrgUnit.getAcronym();
        } else if (StringUtils.isNotBlank(orgUnit.getUnitPath())) {
            String[] unitPath = orgUnit.getUnitPath().split(" ");
            if (unitPath.length > 1) {
                parentName = unitPath[unitPath.length - 2];
                parentAcronym = unitPath[unitPath.length - 2];
            }
        }

        if (StringUtils.isAnyBlank(parentAcronym, parentName)) {
            return Optional.empty();
        }

        String authority = getParentOrgUnitAuthority(parentAcronym);
        return Optional.of(new MetadataValueDTO(field, parentName, authority, Choices.CF_AMBIGUOUS));

    }

    private String getParentOrgUnitAuthority(String acronym) {
        return getOrgUnitParentAuthorityPrefix()
            .map(prefix -> isOrgUnitActive(acronym) ? GENERATE + prefix : REFERENCE + prefix)
            .map(prefix -> prefix + acronym)
            .orElse(acronym);
    }

    private Optional<MetadataValueDTO> getMetadataValue(Object value, String field, String language) {
        if (value != null) {
            return Optional.of(new MetadataValueDTO(field, language, String.valueOf(value)));
        } else {
            return Optional.empty();
        }
    }

    private Optional<MetadataValueDTO> getMetadataValue(String value, String field, String language) {
        if (StringUtils.isNotBlank(value)) {
            return Optional.of(new MetadataValueDTO(field, language, value));
        } else {
            return Optional.empty();
        }
    }

    private Optional<String> getOrgUnitNameMetadataField() {
        return ofNullable(configurationService.getProperty("epfl.orgunit-import.api.metadata-field.name"));
    }

    private Optional<String> getOrgUnitCodeMetadataField() {
        return ofNullable(configurationService.getProperty("epfl.orgunit-import.api.metadata-field.code"));
    }

    private Optional<String> getOrgUnitHeadMetadataField() {
        return ofNullable(configurationService.getProperty("epfl.orgunit-import.api.metadata-field.head"));
    }

    private Optional<String> getOrgUnitHeadAuthorityPrefix() {
        return ofNullable(configurationService.getProperty("epfl.orgunit-import.api.metadata-field.head.authority"));
    }

    private Optional<String> getOrgUnitParentMetadataField() {
        return ofNullable(configurationService.getProperty("epfl.orgunit-import.api.metadata-field.head"));
    }

    private Optional<String> getOrgUnitParentAuthorityPrefix() {
        return ofNullable(configurationService.getProperty("epfl.orgunit-import.api.metadata-field.head.authority"));
    }

    private Optional<String> getOrgUnitAcronymMetadataField() {
        return ofNullable(configurationService.getProperty("epfl.orgunit-import.api.metadata-field.acronym"));
    }

    private String getEnglishMetadataFieldLanguage() {
        return configurationService.getProperty("epfl.orgunit-import.metadata-field-language.english", "en");
    }

    private String getFrenchMetadataFieldLanguage() {
        return configurationService.getProperty("epfl.orgunit-import.metadata-field-language.english", "fr");
    }

}
