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
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dspace.content.authority.Choices;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.client.EpflApiClient.Language;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.epfl.client.model.PersonDTO.Accred;
import org.dspace.epfl.service.OrgUnitApiService;
import org.dspace.epfl.service.PersonApiService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

public class PersonApiServiceImpl implements PersonApiService {

    private static final String PERSON_MAPPING_PREFIX = "epfl.person-import.api.metadata-field.";

    @Autowired
    private EpflApiClient apiClient;

    @Autowired
    private OrgUnitApiService orgUnitApiService;

    @Autowired
    private ConfigurationService configurationService;

    @Override
    public List<MetadataValueDTO> getMetadataValues(String sciper) {
        return apiClient.getPerson(sciper, Language.EN)
            .map(person -> getMetadataValues(person))
            .orElse(getInactiveMetadataField());
    }

    @Override
    public Optional<InputStream> getPersonalPicture(String sciper) {
        return apiClient.getPersonalPicture(sciper);
    }

    private List<MetadataValueDTO> getMetadataValues(PersonDTO person) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        getPersonNameMetadataField()
            .flatMap(field -> getMetadataValue(person.getFullName(), field))
            .ifPresent(metadataValues::add);

        getPersonFirstNameMetadataField()
            .flatMap(field -> getMetadataValue(person.getFirstname(), field))
            .ifPresent(metadataValues::add);

        getPersonLastNameMetadataField()
            .flatMap(field -> getMetadataValue(person.getName(), field))
            .ifPresent(metadataValues::add);

        getPersonEmailMetadataField()
            .flatMap(field -> getMetadataValue(person.getEmail(), field))
            .ifPresent(metadataValues::add);

        getPersonActiveMetadataField()
            .flatMap(field -> getMetadataValue("true", field))
            .ifPresent(metadataValues::add);

        getPersonSciperMetadataField()
            .flatMap(field -> getMetadataValue(person.getSciper(), field))
            .ifPresent(metadataValues::add);

        getPersonUrlMetadataField()
            .flatMap(field -> getUrlMetadataValue(person.getProfile(), field))
            .ifPresent(metadataValues::add);

        metadataValues.addAll(getAffiliationMetadataValues(person.getAccreds()));

        return metadataValues;

    }

    private List<MetadataValueDTO> getInactiveMetadataField() {
        return getPersonActiveMetadataField()
            .flatMap(field -> getMetadataValue("false", field))
            .map(List::of)
            .orElse(List.of());
    }

    private Optional<MetadataValueDTO> getMetadataValue(String value, String field) {
        return Optional.ofNullable(value)
            .filter(StringUtils::isNotBlank)
            .map(metadataValue -> new MetadataValueDTO(field, metadataValue));
    }

    private List<MetadataValueDTO> getAffiliationMetadataValues(Accred[] accreds) {

        Optional<String> positionField = getPersonAffiliationPositionMetadataField();
        Optional<String> affiliationField = getPersonAffiliationOrgUnitMetadataField();

        if (positionField.isEmpty() || affiliationField.isEmpty()) {
            return List.of();
        }

        return Arrays.stream(accreds)
            .flatMap(accred -> getAffiliationValues(accred, positionField.get(), affiliationField.get()).stream())
            .collect(Collectors.toList());
    }

    private List<MetadataValueDTO> getAffiliationValues(Accred accred, String positionField, String affiliationField) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        String name = accred.getName();
        String position = accred.getPosition();

        if (StringUtils.isAllBlank(name, position)) {
            return List.of();
        }

        if (StringUtils.isNotBlank(position)) {
            metadataValues.add(new MetadataValueDTO(positionField, position));
        }

        if (StringUtils.isNotBlank(name)) {
            String authority = getOrgUnitAuthority(accred.getAcronym());
            int confidence = StringUtils.isBlank(authority) ? Choices.CF_UNSET : Choices.CF_AMBIGUOUS;
            metadataValues.add(new MetadataValueDTO(affiliationField, name, authority, confidence));
        }

        getPersonAffiliationStartMetadataField()
            .flatMap(field -> getMetadataValue(PLACEHOLDER_PARENT_METADATA_VALUE, field))
            .ifPresent(metadataValues::add);

        getPersonAffiliationEndMetadataField()
            .flatMap(field -> getMetadataValue(PLACEHOLDER_PARENT_METADATA_VALUE, field))
            .ifPresent(metadataValues::add);

        return metadataValues;
    }

    private Optional<MetadataValueDTO> getUrlMetadataValue(String profile, String field) {
        return Optional.ofNullable(profile)
            .filter(StringUtils::isNotBlank)
            .map(value -> "https://people.epfl.ch/" + value)
            .map(metadataValue -> new MetadataValueDTO(field, metadataValue));
    }

    private String getOrgUnitAuthority(String acronym) {

        if (StringUtils.isBlank(acronym)) {
            return null;
        }

        return getPersonAffiliationAuthorityPrefix()
            .map(prefix -> isOrgUnitActive(acronym) ? GENERATE + prefix : REFERENCE + prefix)
            .map(prefix -> prefix + acronym)
            .orElse(acronym);
    }

    private boolean isOrgUnitActive(String acronym) {
        return orgUnitApiService.isOrgUnitActive(acronym);
    }

    private Optional<String> getPersonNameMetadataField() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "name"));
    }

    private Optional<String> getPersonFirstNameMetadataField() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "first-name"));
    }

    private Optional<String> getPersonLastNameMetadataField() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "last-name"));
    }

    private Optional<String> getPersonEmailMetadataField() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "email"));
    }

    private Optional<String> getPersonAffiliationPositionMetadataField() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "affiliation.position"));
    }

    private Optional<String> getPersonAffiliationOrgUnitMetadataField() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "affiliation.orgunit"));
    }

    private Optional<String> getPersonAffiliationStartMetadataField() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "affiliation.startDate"));
    }

    private Optional<String> getPersonAffiliationEndMetadataField() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "affiliation.endDate"));
    }

    private Optional<String> getPersonUrlMetadataField() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "url"));
    }

    private Optional<String> getPersonActiveMetadataField() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "active"));
    }

    private Optional<String> getPersonSciperMetadataField() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "sciper"));
    }

    private Optional<String> getPersonAffiliationAuthorityPrefix() {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + "affiliation.authority"));
    }

    @Override
    public String getSciperMetadataField() {
        return getPersonSciperMetadataField()
            .orElseThrow(() -> new IllegalStateException("No Sciper metadata field configured"));
    }

}
