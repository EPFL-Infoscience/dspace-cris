/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static org.dspace.authority.service.AuthorityValueService.GENERATE;
import static org.dspace.authority.service.AuthorityValueService.REFERENCE;
import static org.dspace.authority.service.AuthorityValueService.SPLIT;
import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;
import static org.dspace.core.I18nUtil.getEmailFilename;
import static org.dspace.util.FunctionalUtils.throwingConsumerWrapper;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.mail.MessagingException;

import com.google.api.client.util.Lists;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.authenticate.service.ProfileInitializer;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.ItemServiceImpl;
import org.dspace.content.MetadataValue;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.discovery.SearchServiceException;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.client.EpflApiClientImpl;
import org.dspace.epfl.client.model.OrgUnitDTO;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.epfl.service.OrgUnitApiService;
import org.dspace.epfl.service.impl.OrgUnitApiServiceImpl;
import org.dspace.profile.ResearcherProfile;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;


public class SynchronizationOfOrgUnitsScript
        extends DSpaceRunnable<SynchronizationOfOrgUnitsConfiguration<SynchronizationOfOrgUnitsScript>> {

    private Context context;
    private ItemService itemService;
    protected EpflApiClient epflApiClient;

    protected OrgUnitApiService orgUnitApiService;
    private CollectionService collectionService;
    private EPersonService ePersonService;
    private ProfileInitializer profileInitializer;
    private WorkspaceItemService workspaceItemService;
    private InstallItemService installItemService;

    private ConfigurationService configurationService;

    private StringBuilder logInfo = new StringBuilder();

    private List<String> acronyms = List.of();

    private String email;

    private Map<String, Item> createdAcronyms = new HashMap<>();

    private Set<String> synchronizedUnits = new HashSet<>();

    @Override
    @SuppressWarnings("unchecked")
    public SynchronizationOfOrgUnitsConfiguration<SynchronizationOfOrgUnitsScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("synchronization-of-orgunits",
                SynchronizationOfOrgUnitsConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        itemService = new DSpace().getSingletonService(ItemServiceImpl.class);
        profileInitializer = new DSpace().getSingletonService(ProfileInitializer.class);
        epflApiClient = new DSpace().getServiceManager()
                .getServiceByName("org.dspace.epfl.client.EpflApiClientImpl",
                        EpflApiClientImpl.class);
        collectionService = ContentServiceFactory.getInstance().getCollectionService();
        ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();
        installItemService = ContentServiceFactory.getInstance().getInstallItemService();
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        orgUnitApiService = new DSpace().getServiceManager()
                                        .getServiceByName("org.dspace.epfl.service.impl.OrgUnitApiServiceImpl",
                                                          OrgUnitApiServiceImpl.class);
        String acronyms = commandLine.getOptionValue("a");
        if (StringUtils.isNotBlank(acronyms)) {
            this.acronyms = List.of(StringUtils.split(acronyms, ","));
        }
        email = commandLine.getOptionValue("e");
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        syncOrgUnits();

        context.complete();
        context.restoreAuthSystemState();
        sendEmail();
    }

    private void syncOrgUnits() {
        List<Item> orgUnits = getAllOrgUnits();
        logInfo(orgUnits.size() + " units with acronym found in the repository");

        for (Item orgUnit : orgUnits) {
            String name = getAcronym(orgUnit);
            logInfo("Synchronizing orgunit " + name);
            if (isOrgUnitFoundInEpfl(orgUnit)) {
                syncOrgUnit(orgUnit);
            } else {
                if (tryToGetAcronymFromHead(orgUnit)) {
                    syncOrgUnit(orgUnit);
                } else {
                    closeOrgUnit(orgUnit);
                    logInfo(name + " not available anymore, has been closed");
                }
            }
        }
    }

    private void logInfo(String message) {
        handler.logInfo(message);
        logInfo.append(message).append("\n");
    }

    private void syncOrgUnit(Item orgUnit) {

        String acronym = getAcronym(orgUnit);
        if (synchronizedUnits.contains(acronym)) {
            logInfo(acronym + " already synchronized");
            return;
        }
        OrgUnitDTO epflOrgUnit;
        List<MetadataValueDTO> metadataValues;
        try {
            epflOrgUnit = epflApiClient.getOrgUnit(acronym, EpflApiClient.Language.EN).orElseThrow();
            metadataValues = orgUnitApiService.getMetadataValues(acronym);
        } catch (RuntimeException e) {
            logInfo("An exception occurred while getting data for " + acronym + ": " + e.getMessage() +
                ", unit not synchronized");
            return;
        }
        if (metadataValues.isEmpty()) {
            logInfo("Unable to update metadata for acronym " + acronym);
            return;
        }
        logInfo("Synchronization for orgUnit with acronym " + acronym + " is started");

        updateMetadata(orgUnit, metadataToUpdate(orgUnit.getMetadata(), metadataValues), acronym);
        addOrUpdateMetadata(orgUnit, "epfl", "synchronization", "date", null,
                            DCDate.getCurrent().toString(), null, -1);
        synchronizedUnits.add(acronym);
        syncParentOrgUnits(epflOrgUnit, orgUnit);

        try {
            syncOrgUnitHead(epflOrgUnit, orgUnit);
        }  catch (IllegalStateException e) {
            logInfo("Unable to sync orgunit head " + epflOrgUnit.getAcronym() + ": " + e.getMessage());
        }
    }

    protected List<MetadataValueDTO> metadataToUpdate(List<MetadataValue> itemMetadata,
                                                      List<MetadataValueDTO> metadataValues) {
        Map<String, String> currentValues = itemMetadata.stream().collect(
            Collectors.toMap(mv -> mv.getMetadataField().toString('.') + mv.getLanguage(),
                             MetadataValue::getValue, (mv1, mv2) -> mv1));
        Predicate<MetadataValueDTO> changed = mv -> {
            String key = mv.getMetadataField() + mv.getLanguage();
            String currentValue = currentValues.get(key);
            return (currentValue == null || !currentValue.equals(mv.getValue()));
        };
        return metadataValues.stream().filter(changed).collect(Collectors.toList());
    }

    private void updateMetadata(Item orgUnit, List<MetadataValueDTO> metadataValues, String acronym)  {
        if (metadataValues.isEmpty()) {
            logInfo("no need to synchronize metadata of unit " + acronym);
            return;
        }

        metadataValues.forEach(throwingConsumerWrapper(metadataValue -> tryToUpdateMetadata(orgUnit, metadataValue)));
    }

    private void tryToUpdateMetadata(Item orgUnit, MetadataValueDTO metadataValue) throws SQLException {
        itemService.clearMetadata(context, orgUnit, metadataValue.getSchema(),
                                  metadataValue.getElement(), metadataValue.getQualifier(),
                                  metadataValue.getLanguage());
        itemService.addMetadata(context, orgUnit, metadataValue.getSchema(), metadataValue.getElement(),
                                metadataValue.getQualifier(), metadataValue.getLanguage(),
                                metadataValue.getValue(), metadataValue.getAuthority(),
                                metadataValue.getConfidence());
    }

    private String getAcronym(Item orgUnit) {
        return getMetadataValue(orgUnit, "oairecerif", "acronym", null, Item.ANY).getValue();
    }

    protected void syncParentOrgUnits(OrgUnitDTO epflOrgUnit, Item orgunit) {

        String acronym = epflOrgUnit.getAcronym();
        logInfo("Synchronization for parent orgUnits of orgUnit with acronym " + acronym);

        Optional<String> parentAcronym = parentAcronym(epflOrgUnit);
        if (parentAcronym.isEmpty()) {
            logInfo(acronym + " does not have a parent orgunit");
            return;
        }
        String parentAcronymValue = parentAcronym.get();
        Item parentUnit = createdAcronyms.getOrDefault(parentAcronymValue, dspaceLookup(parentAcronymValue));
        if (parentUnit != null) {
            logInfo(parentAcronymValue + " already in the repository");
        } else {
            logInfo(parentAcronymValue + " not in the repository, creating it");
            Optional<OrgUnitDTO> orgUnit;
            try {
                orgUnit = epflApiClient.getOrgUnit(parentAcronymValue, EpflApiClient.Language.EN);
            } catch (RuntimeException e) {
                logInfo("Unable to create parent orgunit " + parentAcronymValue + ": " + e.getMessage());
                return;
            }
            parentUnit = createOrgUnit(orgUnit.orElseThrow(), orgunit.getOwningCollection());
            createdAcronyms.put(parentAcronymValue, parentUnit);
        }
        syncOrgUnit(parentUnit);
        replaceMetadataWithAuthorityAndValue(orgunit, "organization", "parentOrganization", null,
                                             parentAcronymValue, parentUnit.getID().toString());
    }

    private Optional<String> parentAcronym(OrgUnitDTO epflOrgUnit) {
        String[] path = epflOrgUnit.getUnitPath().split(" ");
        return path.length < 2 ? Optional.empty() : Optional.of(path[path.length - 2]);
    }

    private void replaceMetadataWithAuthorityAndValue(Item item, String schema, String element, String qualifier,
                                                      String value, String authority) {
        try {
            if (itemService.getMetadataFirstValue(item, schema, element, qualifier, Item.ANY) != null) {
                itemService.replaceMetadata(context, item, schema, element, null, null, value, authority, 600, 0);
            } else {
                itemService.addMetadata(context, item, schema, element, qualifier, null, value, authority, 600, 0);
            }
            itemService.update(context, item);
        } catch (AuthorizeException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Item dspaceLookup(String parentUnitAcronym) {
        try {
            Iterator<Item> iterator = itemService.findUnfilteredByMetadataField(
                context, "oairecerif", "acronym", null, parentUnitAcronym);
            while (iterator.hasNext()) {
                Item item = iterator.next();
                if ("OrgUnit".equals(itemService.getEntityType(item))) {
                    return item;
                }
            }
            return null;
        } catch (AuthorizeException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void syncOrgUnitHead(OrgUnitDTO epflOrgUnit, Item orgUnit) {
        logInfo("Performing synchronization of head for orgUnit with acronym " + epflOrgUnit.getAcronym());

        Optional<OrgUnitDTO.OrgUnitHeadDTO> orgUnitHeadSciperFromEpflOptional =
                Optional.ofNullable(epflOrgUnit.getHead());
        String orgUnitHeadSciperFromEpfl;

        if (orgUnitHeadSciperFromEpflOptional.isPresent()) {
            if (orgUnitHeadSciperFromEpflOptional.get().getSciper() != null) {
                orgUnitHeadSciperFromEpfl = orgUnitHeadSciperFromEpflOptional.get().getSciper();
            } else {
                logInfo("OrgUnit with acronym " + epflOrgUnit.getAcronym() + " has no sciper info on epfl");
                return;
            }
        } else {
            logInfo("OrgUnit with acronym " + epflOrgUnit.getAcronym() + " has no head info on epfl");
            return;
        }


        String orgUnitHeadSciperFromDspace = directorSciper(orgUnit);

        if (!orgUnitHeadSciperFromEpfl.equals(orgUnitHeadSciperFromDspace)) {
            try {
                EPerson ePersonFromEpfl = ePersonService.findByNetid(context, orgUnitHeadSciperFromEpfl + "@epfl.ch");
                if (ePersonFromEpfl == null) {
                    ePersonFromEpfl = ePersonService.findByEmail(context, orgUnitHeadSciperFromEpflOptional.get()
                                                                                                           .getEmail());
                }
                if (ePersonFromEpfl == null) {
                    ePersonFromEpfl = createUser(epflOrgUnit);
                }
                if (ePersonFromEpfl == null) {
                    return;
                }
                ResearcherProfile researcherProfile = findRelatedResearcherProfile(ePersonFromEpfl);
                if (researcherProfile != null) {
                    logInfo("Updating director metadata for orgunit " + orgUnit.getName());
                    Item researcherProfileItem = researcherProfile.getItem();
                    replaceMetadataWithAuthorityAndValue(
                        orgUnit, "crisou", "director", null,
                        researcherProfileItem.getName(), researcherProfileItem.getID().toString()
                    );
                }
            } catch (AuthorizeException | SQLException e) {
                handler.handleException("Error during finding person by sciper");
                throw new RuntimeException(e);
            }
        } else {
            logInfo("Director for " + epflOrgUnit.getAcronym() + " does not need to be synchronized");
        }
    }

    private ResearcherProfile findRelatedResearcherProfile(EPerson ePerson) throws SQLException, AuthorizeException {
        return profileInitializer.findProfile(context, ePerson)
                                 .orElseGet(() -> {
                                     profileInitializer.initialize(context, ePerson);
                                     return profileInitializer.findProfile(context, ePerson).orElse(null);
                                 });
    }

    private String directorSciper(Item orgUnit) {
        String authority = getMetadataAuthority(orgUnit, "crisou", "director", null);
        if (StringUtils.isBlank(authority)) {
            return null;
        }
        String sciper = Stream.of(GENERATE, REFERENCE).filter(authority::startsWith)
            .map(ignored -> StringUtils.substringAfterLast(authority, SPLIT)).findFirst().orElse("");
        if (StringUtils.isNotBlank(sciper)) {
            return sciper;
        }
        try {
            Item director = itemService.find(context, UUIDUtils.fromString(authority));
            if (director == null) {
                return null;
            }
            MetadataValue sciperMetadata = getMetadataValue(director, "epfl", "sciperId", null);
            return Optional.ofNullable(sciperMetadata).map(MetadataValue::getValue).orElse(null);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Item createOrgUnit(OrgUnitDTO epflOrgUnit, Collection collection) {

        try {
            logInfo("Creating orgUnit with acronym " + epflOrgUnit.getAcronym());
            WorkspaceItem orgUnitWorkspaceItem = workspaceItemService.create(context, collection, false);
            Item newOrgUnit = orgUnitWorkspaceItem.getItem();

            newOrgUnit.setOwningCollection(collection);
            collectionService.addItem(context, collection, newOrgUnit);
            installItemService.installItem(context, orgUnitWorkspaceItem);
            addOrUpdateMetadata(newOrgUnit, "oairecerif", "acronym",
                                null, "en", epflOrgUnit.getAcronym(), null, -1);
            return newOrgUnit;
        } catch (AuthorizeException | SQLException e) {
            handler.logError("Error during creation of orgUnit with acronym " + epflOrgUnit.getAcronym());
            throw new RuntimeException(e);
        }

    }

    private EPerson createUser(OrgUnitDTO epflOrgUnit) {
        EPerson newEPerson;
        try {
            logInfo("Creation of person with sciper " + epflOrgUnit.getHead().getSciper() + " started");
            PersonDTO epflPerson;
            try {
                epflPerson = epflApiClient.getPerson(epflOrgUnit.getHead().getSciper(), EpflApiClient.Language.EN)
                                          .orElse(null);
            } catch (RuntimeException e) {
                logInfo("unable to gather data for person with sciper: " + epflOrgUnit.getHead().getSciper() + ":"
                            + e.getMessage());
                return null;
            }

            newEPerson = ePersonService.create(context);

            newEPerson.setNetid(epflOrgUnit.getHead().getSciper() + "@epfl.ch");
            newEPerson.setFirstName(context, epflOrgUnit.getHead().getFirstname());
            newEPerson.setLastName(context, epflOrgUnit.getHead().getName());
            newEPerson.setEmail(Optional.ofNullable(epflPerson)
                                        .map(PersonDTO::getEmail)
                                        .orElse("placeholder@email.com"));

            profileInitializer.initialize(context, newEPerson);

            ePersonService.setMetadataSingleValue(context, newEPerson, "epfl", "synchronization", "date", null,
                                                  new Timestamp(new Date().getTime()).toString());
            ePersonService.update(context, newEPerson);
            return context.reloadEntity(newEPerson);
        } catch (SQLException | AuthorizeException e) {
            handler.logError("Error during creation of person with sciper " + epflOrgUnit.getHead().getSciper());
            throw new RuntimeException(e);
        }
    }

    private boolean tryToGetAcronymFromHead(Item orgUnit) {
        String headSciper = directorSciper(orgUnit);

        Optional<PersonDTO> epflPersonOption;
        try {
            epflPersonOption = epflApiClient.getPerson(headSciper, EpflApiClient.Language.EN);
        } catch (RuntimeException e) {
            logInfo("Error while getting acronym from head of unit: " + e.getMessage());
            return false;
        }
        if (epflPersonOption.isPresent()) {
            PersonDTO epflPerson = epflPersonOption.get();
            PersonDTO.Accred epflPersonAccred = Arrays.stream(epflPerson.getAccreds())
                    .filter(accred -> accred.getName()
                                            .equals(getMetadataValue(orgUnit, "dc", "title", null, "en").getValue()))
                    .findFirst().orElse(null);
            if (epflPersonAccred != null) {
                addOrUpdateMetadata(orgUnit, "oairecerif", "acronym",
                                    null, "en", epflPersonAccred.getAcronym(), null, -1);
                addOrUpdateMetadata(orgUnit, "oairecerif", "acronym",
                                    null, "fr", epflPersonAccred.getAcronym(), null, -1);
                return true;
            }
        }
        return false;
    }

    private void closeOrgUnit(Item orgUnit) {
        MetadataValue metadataValue = getMetadataValue(orgUnit, "oairecerif", "acronym", null);
        String acronym = metadataValue.getValue();
        logInfo("Closing orgUnit with acronym " + acronym);
        String yesterday = getYesterdayDate();

        addOrUpdateMetadata(orgUnit, "epfl", "orgUnit", "active", null, "false", null, -1);
        addOrUpdateMetadata(orgUnit, "epfl", "orgUnit", "closure", null, yesterday, null, -1);

        List<Item> linkedPersons = getAllLinkedPersonsToOrgUnit(orgUnit.getID().toString());
        logInfo("Closing affiliations from all linked persons of orgUnit with acronym " + acronym);
        for (Item person : linkedPersons) {
            List<MetadataValue> affiliations =
                itemService.getMetadataByMetadataString(person, "oairecerif.person.affiliation");
            List<MetadataValue> endDates =
                itemService.getMetadataByMetadataString(person, "oairecerif.affiliation.endDate");
            List<Integer> places =
                affiliations.stream().filter(mv -> orgUnit.getID().toString().equals(mv.getAuthority()))
                            .map(MetadataValue::getPlace)
                            .collect(Collectors.toList());

            endDates.stream()
                    .filter(mv -> PLACEHOLDER_PARENT_METADATA_VALUE.equals(mv.getValue()))
                    .filter(mv -> places.contains(mv.getPlace()))
                    .forEach(throwingConsumerWrapper(
                        mv -> itemService.replaceMetadata(context, person, mv.getSchema(), mv.getElement(),
                                                          mv.getQualifier(), null, yesterday, null, -1, mv.getPlace())
                    ));
        }
        logInfo("Closing orgUnit with acronym " + acronym + " is done");
    }

    private List<Item> getAllOrgUnits() {
        List<Collection> orgUnitCollections;
        try {
            orgUnitCollections = collectionService.findCollectionsAdministeredByEntityType(
                null, "OrgUnit", context, 0, 1024);
        } catch (SQLException | SearchServiceException e) {
            handler.logError("Error during search for all orgUnit collections in dspace occurs");
            throw new RuntimeException(e);
        }
        List<Item> orgUnits = new ArrayList<>();

        for (Collection collection : orgUnitCollections) {
            orgUnits = Stream.concat(orgUnits.stream(), getOrgUnitsFromCollection(collection).stream())
                             .filter(item -> item.getMetadata().stream().anyMatch(this::acronymField))
                             .filter(item -> acronyms.isEmpty() || matchingAcronyms(item.getMetadata()))
                             .filter(this::isActive)
                             .collect(Collectors.toList());
        }
        return orgUnits;
    }

    private boolean isActive(Item orgUnit) {
        String value = itemService.getMetadataFirstValue(orgUnit, "epfl", "orgUnit", "active", Item.ANY);
        return value == null || Boolean.parseBoolean(value);
    }

    private boolean matchingAcronyms(List<MetadataValue> metadata) {
        return metadata.stream().anyMatch(mv -> acronymField(mv) && acronyms.contains(mv.getValue()));
    }

    private boolean acronymField(MetadataValue mv) {
        return "oairecerif.acronym".equals(mv.getMetadataField().toString('.'));
    }

    private List<Item> getOrgUnitsFromCollection(Collection collection) {
        try {
            return Lists.newArrayList(itemService.findByCollection(context, collection));
        } catch (SQLException e) {
            handler.logError("Error during search for all orgUnits in dspace collections occurs");
            throw new RuntimeException(e);
        }
    }

    private List<Item> getAllLinkedPersonsToOrgUnit(String uuid) {
        try {
            return Lists.newArrayList(
                itemService.findByMetadataFieldAuthority(context, "oairecerif.person.affiliation", uuid));
        } catch (SQLException | AuthorizeException e) {
            handler.logError("Error during search for all linked persons of orgUnit occurs");
            throw new RuntimeException(e);
        }
    }

    private boolean isOrgUnitFoundInEpfl(Item orgUnit) {
        MetadataValue metadataValue = getMetadataValue(orgUnit, "oairecerif", "acronym", null);
        if (metadataValue == null) {
            return false;
        }
        try {
            return epflApiClient.getOrgUnit(metadataValue.getValue(), EpflApiClient.Language.EN).isPresent();
        } catch (RuntimeException e) {
            logInfo("Unable to find orgunit from epfl " + metadataValue.getValue() + ": " + e.getMessage());
            return false;
        }
    }

    private MetadataValue getMetadataValue(Item orgUnit, String schema, String element, String qualifier) {
        return getMetadataValue(orgUnit, schema, element, qualifier, Item.ANY);
    }

    private MetadataValue getMetadataValue(Item orgUnit, String schema, String element, String qualifier,
                                           String language) {
        return itemService.getMetadata(orgUnit, schema, element, qualifier, language, false).stream()
                          .filter(mv -> StringUtils.equalsAny(language, Item.ANY, mv.getLanguage()))
                          .findFirst().orElse(null);
    }

    private String getMetadataAuthority(Item orgUnit, String schema, String element, String qualifier) {
        return itemService.getMetadata(orgUnit, schema, element, qualifier, Item.ANY, false).stream()
                          .map(MetadataValue::getAuthority)
                          .findFirst().orElse(null);
    }

    private String getYesterdayDate() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now().minusDays(1L));
    }

    private void addOrUpdateMetadata(Item item, String schema, String element,
                                     String qualifier, String lang, String value,
                                     String authority, Integer confidence) {
        MetadataValue metadataValue = getMetadataValue(item, schema, element, qualifier, lang);
        try {
            if (metadataValue == null) {
                itemService.addMetadata(context, item, schema, element, qualifier, lang, value, authority, confidence);
            } else {
                itemService.replaceMetadata(context, item, schema, element, qualifier, lang, value, authority,
                                            confidence, metadataValue.getPlace());
            }
            itemService.update(context, item);
        } catch (SQLException | AuthorizeException e) {
            handler.logError("Error during modifying metadata");
            throw new RuntimeException(e);
        }

    }

    private void assignCurrentUserInContext() throws SQLException {
        if (StringUtils.isNotBlank(this.email)) {
            EPerson eperson = ePersonService.findByEmail(context, this.email);
            context.setCurrentUser(eperson);
            return;
        }
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = ePersonService.find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() {
        handler.getSpecialGroups().forEach(context::setSpecialGroup);
    }

    protected void sendEmail() {
        try {
            Email email = Email.getEmail(getEmailFilename(context.getCurrentLocale(), "epfl-user-synchronization_log"));
            email.addRecipient(configurationService.getProperty("mail.admin"));
            email.setSubject("INFOSCIENCE - Organizations synchronization process report");
            email.addArgument(logInfo.toString());
            email.send();
        } catch (IOException | MessagingException e) {
            handler.logInfo("An error occurs sending the email related to the user synchronization " + e);
            handler.logInfo("Mail Message content: " + logInfo.toString());
        }
    }

}
