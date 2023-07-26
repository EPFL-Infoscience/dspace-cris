/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;
import static org.dspace.core.I18nUtil.getEmailFilename;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.mail.MessagingException;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.authenticate.service.ProfileInitializer;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.ItemServiceImpl;
import org.dspace.content.MetadataValue;
import org.dspace.content.WorkspaceItem;
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
    private EpflApiClient epflApiClient;
    private CollectionService collectionService;
    private EPersonService ePersonService;
    private ProfileInitializer profileInitializer;
    private WorkspaceItemService workspaceItemService;
    private InstallItemService installItemService;

    private ConfigurationService configurationService;

    private StringBuilder logInfo = new StringBuilder();

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
        String message = orgUnits.size() + " units with acronym found in the repository";
        logInfo(message);

        for (Item orgUnit : orgUnits) {
            String name = getMetadataValue(orgUnit, "oairecerif", "acronym", null);
            if (!StringUtils.equalsAny(name, "IF-GEs")) {
                continue;
            }
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

        OrgUnitDTO epflOrgUnit = epflApiClient.getOrgUnit(getMetadataValue(orgUnit, "oairecerif",
                "acronym", null, "en"), EpflApiClient.Language.EN).orElseThrow();
        logInfo("Synchronization for orgUnit with acronym " + epflOrgUnit.getAcronym() + " is started");

        addOrUpdateMetadata(orgUnit, "dc", "title", null, "en", epflOrgUnit.getName());

        syncParentOrgUnits(epflOrgUnit, orgUnit);

        syncOrgUnitHead(epflOrgUnit, orgUnit);
    }

    private void syncParentOrgUnits(OrgUnitDTO epflOrgUnit, Item orgunit) {

        String parentAcronym = epflOrgUnit.getAcronym();
        logInfo("Synchronization for parent orgUnits of orgUnit with acronym " + parentAcronym);
        String[] path = epflOrgUnit.getUnitPath().split(" ");
        if (path.length < 2) {
            logInfo(parentAcronym + " does not have a parent orgunit");
            return;
        }
        String parentUnitAcronym = path[path.length - 2];
        Item parentUnit = dspaceLookup(parentUnitAcronym);
        if (parentUnit != null) {
            logInfo(parentUnitAcronym + " already in the repository");
        } else {
            logInfo(parentUnitAcronym + " not in the repository, creating it");
            parentUnit = createOrgUnit(epflOrgUnit, orgunit.getOwningCollection());
        }
        syncOrgUnit(parentUnit);
        replaceMetadataWithAuthority(orgunit, parentUnit, "organization", "parentOrganization", null);
    }

    private void replaceMetadataWithAuthority(Item item, Item metadataValues, String schema, String element,
                                              String qualifier) {
        try {
            if (itemService
                .getMetadataFirstValue(item, schema, element, qualifier, Item.ANY) != null) {
                itemService.replaceMetadata(context, item, schema, element,
                                            null, null, metadataValues.getName(),
                                            metadataValues.getID().toString(),
                                            600, 0);
            } else {
                itemService.addMetadata(context, item, schema, element,
                                        qualifier, null, metadataValues.getName(),
                                        metadataValues.getID().toString(),
                                        600, 0);
            }
            itemService.update(context, item);
        } catch (AuthorizeException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Item dspaceLookup(String parentUnitAcronym) {
        try {
            Iterator<Item> iterator = itemService
                .findUnfilteredByMetadataField(context, "oairecerif", "acronym", null,
                                                                                parentUnitAcronym);
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
        logInfo("Synchronization of head for orgUnit with acronym " + epflOrgUnit.getAcronym() + " is started");

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
                    ePersonFromEpfl = createUser(epflOrgUnit);
                }
                ResearcherProfile researcherProfile =
                    findRelatedResearcherProfile(ePersonFromEpfl);
                if (researcherProfile != null) {
                    logInfo("Updating director metadata for orgunit " + orgUnit.getName());
                    replaceMetadataWithAuthority(orgUnit, researcherProfile.getItem(),
                                                 "crisou", "director", null);
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
        try {
            Item director = itemService.find(context, UUIDUtils.fromString(authority));
            if (director == null) {
                return null;
            }
            return getMetadataValue(director, "epfl", "sciperId", null);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Item createOrgUnit(OrgUnitDTO epflOrgUnit, Collection collection) {

        try {
            logInfo("Creation of orgUnit with acronym " + epflOrgUnit.getAcronym() + " started");
            WorkspaceItem orgUnitWorkspaceItem = workspaceItemService.create(context, collection, false);
            Item newOrgUnit = orgUnitWorkspaceItem.getItem();

            newOrgUnit.setOwningCollection(collection);
            collectionService.addItem(context, collection, newOrgUnit);
            installItemService.installItem(context, orgUnitWorkspaceItem);
            addOrUpdateMetadata(newOrgUnit, "oairecerif", "acronym",
                    null, "en", epflOrgUnit.getAcronym());
            context.reloadEntity(newOrgUnit);
            return newOrgUnit;
        } catch (AuthorizeException | SQLException e) {
            handler.logError("Error during creation of orgUnit with acronym " + epflOrgUnit.getAcronym());
            throw new RuntimeException(e);
        }

    }

    private EPerson createUser(OrgUnitDTO epflOrgUnit) {
        EPerson newEPerson = null;
        try {
            logInfo("Creation of person with sciper " + epflOrgUnit.getHead().getSciper() + " started");
            PersonDTO epflPerson = epflApiClient.getPerson(epflOrgUnit.getHead().getSciper(),
                    EpflApiClient.Language.EN).orElse(null);

            newEPerson = ePersonService.create(context);

            newEPerson.setNetid(epflOrgUnit.getHead().getSciper() + "@epfl.ch");
            newEPerson.setFirstName(context, epflOrgUnit.getHead().getFirstname());
            newEPerson.setLastName(context, epflOrgUnit.getHead().getName());
            if (epflPerson != null) {
                newEPerson.setEmail(epflPerson.getEmail());
            } else {
                newEPerson.setEmail("placeholder@email.com");
            }

            profileInitializer.initialize(context, newEPerson);

            ePersonService.setMetadataSingleValue(context, newEPerson,
                    "epfl", "synchronization",
                    "date", null,
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

        Optional<PersonDTO> epflPersonOption = epflApiClient.getPerson(headSciper, EpflApiClient.Language.EN);
        if (epflPersonOption.isPresent()) {
            PersonDTO epflPerson = epflPersonOption.get();
            PersonDTO.Accred epflPersonAccred = Arrays.stream(epflPerson.getAccreds())
                    .filter(accred -> accred.getName().equals(getMetadataValue(orgUnit, "dc", "title",
                                                                               null, "en")))
                    .findFirst().orElse(null);
            if (epflPersonAccred != null) {
                addOrUpdateMetadata(orgUnit, "oairecerif", "acronym",
                        null, "en", epflPersonAccred.getAcronym());
                addOrUpdateMetadata(orgUnit, "oairecerif", "acronym",
                                    null, "fr", epflPersonAccred.getAcronym());
                return true;
            }
        }
        return false;
    }

    private void closeOrgUnit(Item orgUnit) {
        String acronym = getMetadataValue(orgUnit, "oairecerif", "acronym", null);
        logInfo("Closing orgUnit with acronym " + acronym);
        String yesterday = getYesterdayDate();

        addOrUpdateMetadata(orgUnit, "epfl", "orgUnit", "active", null, "false");

        addOrUpdateMetadata(orgUnit, "epfl", "orgUnit", "closure", null, yesterday);

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

            endDates.stream().filter(mv -> PLACEHOLDER_PARENT_METADATA_VALUE.equals(mv.getValue()))
                        .filter(mv -> places.contains(mv.getPlace()))
                            .forEach(mv -> {
                                try {
                                    itemService.replaceMetadata(context, person, mv.getSchema(), mv.getElement(),
                                                                mv.getQualifier(), null, yesterday,
                                                                null,
                                                                -1, mv.getPlace());
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            });

//            addOrUpdateMetadata(person,"oairecerif", "affiliation", "endDate", null, yesterday);
        }
        logInfo("Closing orgUnit with acronym " + acronym + " is done");
    }

    private List<Item> getAllOrgUnits() {
        List<Collection> orgUnitCollections;
        try {
            orgUnitCollections = collectionService.findCollectionsAdministeredByEntityType(null, "OrgUnit",
                    context, 0, 1024);
        } catch (SQLException | SearchServiceException e) {
            handler.logError("Error during search for all orgUnit collections in dspace occurs");
            throw new RuntimeException(e);
        }
        List<Item> orgUnits = new ArrayList<>();

        for (Collection collection : orgUnitCollections) {
            orgUnits = Stream.concat(orgUnits.stream(),
                                     getOrgUnitsFromCollection(collection).stream())
                .filter(item -> item.getMetadata()
                                    .stream()
                                    .anyMatch(mv -> "oairecerif.acronym".equals(mv.getMetadataField()
                                                                                  .toString('.'))))
                    .collect(Collectors.toList());
        }
        return orgUnits;
    }

    private List<Item> getOrgUnitsFromCollection(Collection collection) {
        try {
            Iterator<Item> orgUnitsIterator = itemService.findByCollection(context, collection);
            List<Item> orgUnitsList = new ArrayList<>();
            orgUnitsIterator.forEachRemaining(orgUnitsList::add);
            return orgUnitsList;
        } catch (SQLException e) {
            handler.logError("Error during search for all orgUnits in dspace collections occurs");
            throw new RuntimeException(e);
        }
    }

    private List<Item> getAllLinkedPersonsToOrgUnit(String uuid) {
        try {
            List<Item> linkedPersons = new ArrayList<>();
            itemService.findByMetadataFieldAuthority(context, "oairecerif.person.affiliation", uuid)
                    .forEachRemaining(linkedPersons::add);
            return linkedPersons;
        } catch (SQLException | AuthorizeException e) {
            handler.logError("Error during search for all linked persons of orgUnit occurs");
            throw new RuntimeException(e);
        }
    }

    private boolean isOrgUnitFoundInEpfl(Item orgUnit) {
        String acronym = getMetadataValue(orgUnit, "oairecerif", "acronym", null);
        if (acronym == null) {
            return false;
        }
        return epflApiClient.getOrgUnit(acronym, EpflApiClient.Language.EN).isPresent();
    }

    private String getMetadataValue(Item orgUnit, String schema, String element, String qualifier) {
        return getMetadataValue(orgUnit, schema, element, qualifier, Item.ANY);
    }

    private String getMetadataValue(Item orgUnit, String schema, String element, String qualifier, String language) {
        List<MetadataValue> values = itemService.getMetadata(orgUnit, schema, element,
                                                             qualifier, language, false);
        if (values.size() == 0) {
            return null;
        }
        return values.get(0).getValue();
    }

    private String getMetadataAuthority(Item orgUnit, String schema, String element, String qualifier) {
        List<MetadataValue> values = itemService.getMetadata(orgUnit, schema, element,
                                                             qualifier, Item.ANY, false);
        if (values.size() == 0) {
            return null;
        }
        return values.get(0).getAuthority();
    }

    private String getYesterdayDate() {
        LocalDate today = LocalDate.now();
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(today.minusDays(1L));
    }

    private void addOrUpdateMetadata(Item item, String schema, String element,
                                     String qualifier, String lang, String value) {
        String metadataValue = getMetadataValue(item, schema, element, qualifier);
        try {
            if (metadataValue == null) {
                itemService.addMetadata(context, item, schema, element, qualifier, lang, value);
            } else {
                itemService.setMetadataSingleValue(context, item, schema, element, qualifier, lang, value);

            }
            itemService.update(context, item);
        } catch (SQLException | AuthorizeException e) {
            handler.logError("Error during modifying metadata");
            throw new RuntimeException(e);
        }

    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }

    private void sendEmail() {
        String log = logInfo.toString();
        try {
            Email email = Email.getEmail(getEmailFilename(context.getCurrentLocale(), "epfl-user-synchronization_log"));
            email.addRecipient(configurationService.getProperty("mail.admin"));
            email.addArgument(log);
            email.send();
        } catch (IOException | MessagingException e) {
            handler.logInfo("An error occurs sending the email related to the user synchronization " + e);
            handler.logInfo("Mail Message content: " + log);
        }
    }

}
