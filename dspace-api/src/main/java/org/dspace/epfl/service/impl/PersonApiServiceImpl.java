/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.service.impl;

import static java.util.Optional.ofNullable;
import static org.apache.commons.collections.IteratorUtils.toList;
import static org.dspace.authority.service.AuthorityValueService.REFERENCE;
import static org.dspace.content.authority.Choices.CF_ACCEPTED;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;

import java.io.InputStream;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.authority.Choices;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.client.EpflApiClient.Language;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.epfl.client.model.PersonDTO.Accred;
import org.dspace.epfl.service.OrgUnitApiService;
import org.dspace.epfl.service.PersonApiService;
import org.dspace.profile.ResearcherProfile;
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

    @Autowired
    private EPersonService epersonService;

    @Autowired
    private ItemService itemService;

    @Override
    public List<PersonDTO> getPersons(String query) {
        return apiClient.getPersons(query, Language.EN);
    }

    @Override
    public Optional<PersonDTO> getPerson(String sciper) {
        return apiClient.getPerson(sciper, Language.EN);
    }

    @Override
    public List<MetadataValueDTO> getMetadataValues(Context context, String sciper) {
        return getPerson(sciper)
            .map(p -> getMetadataValues(context, p))
            .orElse(getInactiveMetadataField());
    }

    @Override
    public List<String> getMetadataFields() {
        return configurationService.getPropertyKeys(PERSON_MAPPING_PREFIX).stream()
            .map(key -> configurationService.getProperty(key))
            .filter(property -> isMetadataField(property))
            .collect(Collectors.toList());
    }

    @Override
    public Optional<InputStream> getPersonalPicture(String sciper) {
        return getPerson(sciper)
            .map(PersonDTO::getEmail)
            .map(email -> email.contains("@") ? email.substring(0, email.indexOf('@')) : email)
            .filter(StringUtils::isNotBlank)
            .filter(localPart -> localPart.contains("."))
            .flatMap(apiClient::getPersonalPicture);
    }

    @Override
    public List<MetadataValueDTO> getMetadataValues(Context context, PersonDTO person) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        getPersonMetadataField("name")
            .flatMap(field -> getMetadataValue(person.getFullName(), field))
            .ifPresent(metadataValues::add);

        getPersonMetadataField("first-name")
            .flatMap(field -> getMetadataValue(person.getFirstname(), field))
            .ifPresent(metadataValues::add);

        getPersonMetadataField("last-name")
            .flatMap(field -> getMetadataValue(person.getName(), field))
            .ifPresent(metadataValues::add);

        getPersonMetadataField("email")
            .flatMap(field -> getMetadataValue(person.getEmail(), field))
            .ifPresent(metadataValues::add);

        getPersonMetadataField("active")
            .flatMap(field -> getMetadataValue("true", field))
            .ifPresent(metadataValues::add);

        getPersonMetadataField("sciper")
            .flatMap(field -> getMetadataValue(person.getSciper(), field))
            .ifPresent(metadataValues::add);

        getPersonMetadataField("url")
            .flatMap(field -> getUrlMetadataValue(person.getProfile(), field))
            .ifPresent(metadataValues::add);

        metadataValues.addAll(getAffiliationMetadataValues(context, person));

        return metadataValues;

    }

    private List<MetadataValueDTO> getInactiveMetadataField() {
        return getPersonMetadataField("active")
            .flatMap(field -> getMetadataValue("false", field))
            .map(List::of)
            .orElse(List.of());
    }


    private Optional<MetadataValueDTO> getMetadataValue(String value, String field) {
        return Optional.ofNullable(value)
                       .filter(StringUtils::isNotBlank)
                       .map(metadataValue -> new MetadataValueDTO(field, metadataValue));
    }
    private Optional<MetadataValueDTO> getMetadataValue(String value, String field, int place) {
        return Optional.ofNullable(value)
            .filter(StringUtils::isNotBlank)
            .map(metadataValue -> new MetadataValueDTO(field, metadataValue, place));
    }

    private List<MetadataValueDTO> getAffiliationMetadataValues(Context context, PersonDTO person) {

        Optional<String> positionField = getPersonMetadataField("affiliation.position");
        Optional<String> affiliationField = getPersonMetadataField("affiliation.orgunit");

        if (positionField.isEmpty() || affiliationField.isEmpty()) {
            return List.of();
        }
        AtomicInteger place = new AtomicInteger(0);
        List<MetadataValueDTO> affiliationMetadataValues =
            Arrays.stream(person.getAccreds())
                  .flatMap(accred -> getAffiliationValues(context, accred, positionField.get(),
                                                          affiliationField.get(), place).stream())
                  .collect(Collectors.toList());

        person.getMainAffiliation()
            .flatMap(mainAffiliation -> getMainAffiliationMetadataValue(context, mainAffiliation))
              .ifPresent(affiliationMetadataValues::add);

        return affiliationMetadataValues;
    }

    private List<MetadataValueDTO> getAffiliationValues(Context context, Accred accred, String positionField,
                                                        String affiliationField, AtomicInteger atomicPlace) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        String position = accred.getPosition();
        String acronym = accred.getAcronym();

        if (StringUtils.isAllBlank(acronym, position) ||
            !inDspace(context, acronym)) {
            return List.of();
        }

        int place = atomicPlace.get();

        if (StringUtils.isNotBlank(position)) {
            metadataValues.add(new MetadataValueDTO(positionField, position, place));
        }

        if (StringUtils.isNotBlank(acronym)) {
            String authority = getOrgUnitAuthority(acronym);
            int confidence = StringUtils.isBlank(authority) ? Choices.CF_UNSET : Choices.CF_AMBIGUOUS;
            metadataValues.add(new MetadataValueDTO(affiliationField, acronym, authority, confidence, place));
        }

        String yesterday = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now().minusDays(1L));
        getPersonMetadataField("affiliation.start")
            .flatMap(field -> getMetadataValue(yesterday, field, place))
            .ifPresent(metadataValues::add);

        getPersonMetadataField("affiliation.end")
            .flatMap(field -> getMetadataValue(PLACEHOLDER_PARENT_METADATA_VALUE, field, place))
            .ifPresent(metadataValues::add);

        if (!metadataValues.isEmpty())  {
            atomicPlace.set(atomicPlace.get() + 1);
        }

        return metadataValues;
    }

    // FIXME; centralize this logic, is currently redundant
    private boolean inDspace(Context context, String acro) {
        try {
            Iterator<Item> iterator =
                itemService.findArchivedByMetadataField(context, "oairecerif.acronym", acro);
            while (iterator.hasNext()) {
                String entityType = itemService.getEntityType(iterator.next());
                if ("OrgUnit".equals(entityType)) {
                    return true;
                }
            }
        } catch (AuthorizeException | SQLException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private Optional<MetadataValueDTO> getMainAffiliationMetadataValue(Context context, Accred mainAffiliation) {

        String acronym = mainAffiliation.getAcronym();
        if (StringUtils.isBlank(mainAffiliation.getName())
            || StringUtils.isBlank(mainAffiliation.getAcronym())
            || !inDspace(context, mainAffiliation.getAcronym())
        ) {
            return Optional.empty();
        }

        String authority = getOrgUnitAuthority(mainAffiliation.getAcronym());
        int confidence = StringUtils.isBlank(authority) ? Choices.CF_UNSET : Choices.CF_AMBIGUOUS;

        return getPersonMetadataField("affiliation.main")
            .map(field -> new MetadataValueDTO(field, acronym, authority, confidence));
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

        return getPersonMetadataField("affiliation.authority")
            .map(prefix -> REFERENCE + prefix)
            .map(prefix -> prefix + acronym)
            .orElse(acronym);
    }

    private boolean isOrgUnitActive(String acronym) {
        return orgUnitApiService.isOrgUnitActive(acronym);
    }

    private boolean isMetadataField(String property) {
        return property != null && property.contains(".");
    }

    private Optional<String> getPersonMetadataField(String fieldName) {
        return ofNullable(configurationService.getProperty(PERSON_MAPPING_PREFIX + fieldName));
    }

    public EpflApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(EpflApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public String getSciperMetadataField() {
        return getPersonMetadataField("sciper")
            .orElseThrow(() -> new IllegalStateException("No Sciper metadata field configured"));
    }

    public Optional<ResearcherProfile> findProfileBySciperAndFixOwnerIfNeeded(Context context, EPerson eperson,
            String sciper) {

        String sciperMetadataField = getSciperMetadataField();

        List<Item> items = findArchivedByMetadataField(context, sciperMetadataField, sciper);
        if (items.isEmpty()) {
            return Optional.empty();
        }

        if (items.size() > 1) {
            throw new IllegalStateException("Found many items with sciper " + sciper);
        }

        Item item = items.get(0);
        fixOwnerIfNeeded(context, eperson, item);
        return Optional.of(new ResearcherProfile(item));
    }

    private void fixOwnerIfNeeded(Context context, EPerson eperson, Item item) {
        EPerson owner = getOwner(context, item);
        if (owner != null && !owner.equals(eperson)) {
            throw new IllegalStateException("The item " + item.getID().toString() + " is already linked "
                    + "to another eperson: " + owner.getID() + " cannot be linked to " + eperson.getID().toString());
        }
        if (owner == null) {
            setOwner(context, item, eperson);
        }
    }

    private void setOwner(Context context, Item item, EPerson ePerson) {
        try {
            itemService.clearMetadata(context, item, "dspace", "object", "owner", Item.ANY);
            itemService.addMetadata(context, item, "dspace", "object", "owner", null, ePerson.getName(),
                                    ePerson.getID().toString(), CF_ACCEPTED);
            itemService.update(context, item);
        } catch (AuthorizeException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private EPerson getOwner(Context context, Item item) {
        try {
            return epersonService.findByProfileItem(context, item);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Item> findArchivedByMetadataField(Context context, String sciperMetadataField, String sciper) {
        try {
            return toList(itemService.findArchivedByMetadataField(context, sciperMetadataField, sciper));
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException(e);
        }
    }
}
