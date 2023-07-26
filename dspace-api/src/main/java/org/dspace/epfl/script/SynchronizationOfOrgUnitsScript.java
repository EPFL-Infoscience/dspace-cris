/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

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

import org.apache.commons.cli.ParseException;
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
import org.dspace.discovery.SearchServiceException;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.epfl.client.EpflApiClient;
import org.dspace.epfl.client.EpflApiClientImpl;
import org.dspace.epfl.client.model.OrgUnitDTO;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.scripts.DSpaceRunnable;
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
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        syncOrgUnits();

        context.commit();
        context.restoreAuthSystemState();
    }

    private void syncOrgUnits() {
        List<Item> orgUnits = getAllOgrUnits();

        for (Item orgUnit : orgUnits) {

            if (isOrgUnitFoundInEpfl(orgUnit)) {
                syncOrgUnit(orgUnit);
            } else {
                if (tryToGetAcronymFromHead(orgUnit)) {
                    syncOrgUnit(orgUnit);
                } else {
                    closeOrgUnit(orgUnit);
                }
            }
        }
    }

    private void syncOrgUnit(Item orgUnit) {

        OrgUnitDTO epflOrgUnit = epflApiClient.getOrgUnit(getMetadataValue(orgUnit, "oairecerif",
                "acronym", null), EpflApiClient.Language.EN).orElseThrow();
        handler.logInfo("Synchronization for orgUnit with acronym " + epflOrgUnit.getAcronym() + " is started");

        addOrUpdateMetadata(orgUnit, "dc", "title", null, "en", epflOrgUnit.getName());

        syncPatentOrgUnits(epflOrgUnit, orgUnit.getOwningCollection());

        syncOrgUnitHead(epflOrgUnit, orgUnit);
    }

    private void syncPatentOrgUnits(OrgUnitDTO epflOrgUnit, Collection parentOrgUnitCollection) {

        String parentAcronym = epflOrgUnit.getAcronym();
        handler.logInfo("Synchronization for parent orgUnits of orgUnit with acronym " + parentAcronym);

        List<String> parentOrgUnitsAcronyms = Arrays.stream(epflOrgUnit.getPath())
                .map(OrgUnitDTO.OrgUnitPathDTO::getAcronym)
                .filter(acronym -> !acronym.equals(parentAcronym)).collect(Collectors.toList());

        handler.logInfo("Found " + parentOrgUnitsAcronyms.size() +
                " parent orgUnits of orgUnit with acronym " + parentAcronym);

        List<Item> allOrgUnits = getAllOgrUnits();


        for (String acronym : parentOrgUnitsAcronyms) {
            OrgUnitDTO parentEpflOrgUnit = epflApiClient.getOrgUnit(acronym, EpflApiClient.Language.EN).orElseThrow();
            String epflAcronym = parentEpflOrgUnit.getAcronym();
            Optional<Item> dspaceOrgUnit = allOrgUnits.stream()
                    .filter(orgUnit -> epflAcronym.equals(getMetadataValue(orgUnit, "oairecerif", "acronym", null)))
                    .findFirst();

            if (dspaceOrgUnit.isPresent()) {
                syncOrgUnit(dspaceOrgUnit.get());
            } else {
                Item newOrgUnt = createOrgUnit(parentEpflOrgUnit, parentOrgUnitCollection);
                syncOrgUnit(newOrgUnt);
            }
        }
    }

    private void syncOrgUnitHead(OrgUnitDTO epflOrgUnit, Item orgUnit) {
        handler.logInfo("Synchronization of head for orgUnit with acronym " + epflOrgUnit.getAcronym() + " is started");

        Optional<OrgUnitDTO.OrgUnitHeadDTO> orgUnitHeadSciperFromEpflOptional =
                Optional.ofNullable(epflOrgUnit.getHead());
        String orgUnitHeadSciperFromEpfl;

        if (orgUnitHeadSciperFromEpflOptional.isPresent()) {
            if (orgUnitHeadSciperFromEpflOptional.get().getSciper() != null) {
                orgUnitHeadSciperFromEpfl = orgUnitHeadSciperFromEpflOptional.get().getSciper();
            } else {
                handler.logInfo("OrgUnit with acronym " + epflOrgUnit.getAcronym() + " has no sciper info on epfl");
                return;
            }
        } else {
            handler.logInfo("OrgUnit with acronym " + epflOrgUnit.getAcronym() + " has no head info on epfl");
            return;
        }


        String orgUnitHeadSciperFromDspace = getMetadataValue(orgUnit, "crisou", "director", null);

        if (!orgUnitHeadSciperFromEpfl.equals(orgUnitHeadSciperFromDspace)) {
            addOrUpdateMetadata(orgUnit, "crisou",
                    "director", null, "en", orgUnitHeadSciperFromEpfl);
            createUser(epflOrgUnit);
        } else {
            try {
                EPerson ePersonFromEpfl = ePersonService.findByNetid(context, orgUnitHeadSciperFromEpfl + "@epfl.ch");
                if (ePersonFromEpfl == null) {
                    createUser(epflOrgUnit);
                }
            } catch (SQLException e) {
                handler.logError("Error during finding person by sciper");
                throw new RuntimeException(e);
            }
        }
    }

    private Item createOrgUnit(OrgUnitDTO epflOrgUnit, Collection collection) {

        try {
            handler.logInfo("Creation of orgUnit with acronym " + epflOrgUnit.getAcronym() + " started");
            WorkspaceItem orgUnitWorkspaceItem = workspaceItemService.create(context, collection, false);
            Item newOrgUnit = orgUnitWorkspaceItem.getItem();

            newOrgUnit.setOwningCollection(collection);
            collectionService.addItem(context, collection, newOrgUnit);
            installItemService.installItem(context, orgUnitWorkspaceItem);
            addOrUpdateMetadata(newOrgUnit, "oairecerif", "acronym",
                    null, "en", epflOrgUnit.getAcronym());
            return newOrgUnit;
        } catch (AuthorizeException | SQLException e) {
            handler.logError("Error during creation of orgUnit with acronym " + epflOrgUnit.getAcronym());
            throw new RuntimeException(e);
        }

    }

    private void createUser(OrgUnitDTO epflOrgUnit) {
        EPerson newEPerson = null;
        try {
            handler.logInfo("Creation of person with sciper " + epflOrgUnit.getHead().getSciper() + " started");
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
        } catch (SQLException | AuthorizeException e) {
            handler.logError("Error during creation of person with sciper " + epflOrgUnit.getHead().getSciper());
            throw new RuntimeException(e);
        }
    }

    private boolean tryToGetAcronymFromHead(Item orgUnit) {
        String headSciper = getMetadataValue(orgUnit, "crisou", "director", null);

        Optional<PersonDTO> epflPersonOption = epflApiClient.getPerson(headSciper, EpflApiClient.Language.EN);
        if (epflPersonOption.isPresent()) {
            PersonDTO epflPerson = epflPersonOption.get();
            PersonDTO.Accred epflPersonAccred = Arrays.stream(epflPerson.getAccreds())
                    .filter(accred -> accred.getName().equals(getMetadataValue(orgUnit, "dc", "title", null)))
                    .findFirst().orElse(null);
            if (epflPersonAccred != null) {
                addOrUpdateMetadata(orgUnit, "oairecerif", "acronym",
                        null, "en", epflPersonAccred.getAcronym());
                return true;
            }
        }
        return false;
    }

    private void closeOrgUnit(Item orgUnit) {
        handler.logInfo("Closing orgUnit with uuid " + orgUnit.getID());
        String yesterday = getYesterdayDate();

        addOrUpdateMetadata(orgUnit, "epfl", "orgunit", "active", null, "false");

        addOrUpdateMetadata(orgUnit, "epfl", "orgunit", "closure", null, yesterday);

        List<Item> linkedPersons = getAllLinkedPersonsToOpgUnit(orgUnit.getID().toString());
        handler.logInfo("Removing affiliations from all linked persons of orgUnit with uuid " + orgUnit.getID());
        for (Item person : linkedPersons) {
            addOrUpdateMetadata(person,"oairecerif", "affiliation", "endDate", null, yesterday);
        }
        handler.logInfo("Closing orgUnit with uuid " + orgUnit.getID() + " is done");
    }

    private List<Item> getAllOgrUnits() {
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
                    .collect(Collectors.toList());
        }
        handler.logInfo("OrgUnits from dspace side are successfully found");
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

    private List<Item> getAllLinkedPersonsToOpgUnit(String uuid) {
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
        List<MetadataValue> values = itemService.getMetadata(orgUnit, schema, element,
                qualifier, "en", false);
        if (values.size() == 0) {
            return null;
        }
        return values.get(0).getValue();
    }

    private String getYesterdayDate() {
        LocalDate today = LocalDate.now();
        return (today.minusDays(1)).format(DateTimeFormatter.ISO_DATE);
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

}
