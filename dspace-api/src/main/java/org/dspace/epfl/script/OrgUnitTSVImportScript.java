/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static org.dspace.content.Item.ANY;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.dspace.app.bulkimport.exception.BulkImportException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.SearchUtils;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.epfl.script.model.OrgUnitTSV;
import org.dspace.epfl.script.model.OrgUnitTSV.OrgUnitRow;
import org.dspace.epfl.script.service.OrgUnitTSVParser;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrgUnitTSVImportScript extends DSpaceRunnable<OrgUnitTSVImportScriptConfiguration<OrgUnitTSVImportScript>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrgUnitTSVImportScript.class);

    private OrgUnitTSVParser orgUnitTSVParser;

    private CollectionService collectionService;

    private AuthorizeService authorizeService;

    private ConfigurationService configurationService;

    private SearchService searchService;

    private ItemService itemService;

    private WorkspaceItemService workspaceItemService;

    private InstallItemService installItemService;


    private String collectionId;

    private String filename;

    private Context context;


    private int importedOrgUnitsCount = 0;

    private int errorsCount = 0;

    private String activeOrgUnitAcronymHeader;

    private String inactiveOrgUnitAcronymHeader;


    @Override
    public void setup() throws ParseException {

        this.collectionService = ContentServiceFactory.getInstance().getCollectionService();
        this.authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        this.itemService = ContentServiceFactory.getInstance().getItemService();
        this.searchService = SearchUtils.getSearchService();
        this.installItemService = ContentServiceFactory.getInstance().getInstallItemService();
        this.workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();
        this.orgUnitTSVParser = new DSpace().getServiceManager().getServicesByType(OrgUnitTSVParser.class).get(0);

        collectionId = commandLine.getOptionValue('c');
        filename = commandLine.getOptionValue('f');

        activeOrgUnitAcronymHeader = getActiveOrgUnitAcronymHeader();
        inactiveOrgUnitAcronymHeader = getInactiveOrgUnitAcronymHeader();

    }

    @Override
    public void internalRun() throws Exception {
        context = new Context(Context.Mode.BATCH_EDIT);
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        InputStream inputStream = handler.getFileStream(context, filename)
            .orElseThrow(() -> new IllegalArgumentException("Error reading file, the file couldn't be "
                + "found for filename: " + filename));

        Collection collection = getCollection();
        if (collection == null) {
            throw new IllegalArgumentException("No collection found with id " + collectionId);
        }

        if (!this.authorizeService.isAdmin(context, collection)) {
            throw new IllegalArgumentException("The user is not an admin of the given collection");
        }

        try {
            performImport(inputStream);
            context.complete();
            context.restoreAuthSystemState();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        }

    }

    private void performImport(InputStream inputStream) throws IOException {
        OrgUnitTSV orgUnitTSV = orgUnitTSVParser.parseTSV(inputStream);
        importTSV(orgUnitTSV);
    }

    private void importTSV(OrgUnitTSV orgUnitTSV) {

        handler.logInfo("Found " + orgUnitTSV.getRows().size() + " OrgUnits to be imported");

        orgUnitTSV.getRows().forEach(this::importOrgUnitRow);

        handler.logInfo("Import completed. OrgUnit imported with success: " + importedOrgUnitsCount
            + ". Errors: " + errorsCount);

    }

    private void importOrgUnitRow(OrgUnitRow orgUnitRow) {

        try {

            String orgUnitAcronym = getOrgUnitAcronym(orgUnitRow);

            Item orgUnit = findOrgUnitByAcronym(orgUnitAcronym);

            if (orgUnit == null) {
                orgUnit = createOrgUnit(orgUnitRow);
            } else {
                orgUnit = updateOrgUnit(orgUnit, orgUnitRow);
            }

            importedOrgUnitsCount++;

            context.commit();

        } catch (Exception ex) {
            handleRowException(orgUnitRow, ex);
        }

    }

    @SuppressWarnings("rawtypes")
    private Item findOrgUnitByAcronym(String orgUnitAcronym) throws SearchServiceException {

        String acronymMetadataField = getOrgUnitAcronymMetadataField();
        if (StringUtils.isBlank(acronymMetadataField)) {
            return null;
        }

        String query = acronymMetadataField + ":" + orgUnitAcronym;
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.addDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.addFilterQueries(query);

        DiscoverResult discoverResult = searchService.search(context, discoverQuery);

        List<IndexableObject> indexableObjects = discoverResult.getIndexableObjects();

        if (CollectionUtils.isEmpty(indexableObjects)) {
            return null;
        }

        return indexableObjects.stream()
            .map(indexableObject -> (Item) indexableObject.getIndexedObject())
            .filter(item -> hasAcronym(item, orgUnitAcronym))
            .findFirst()
            .orElse(null);
    }

    private Item createOrgUnit(OrgUnitRow orgUnitRow) throws AuthorizeException, SQLException {

        WorkspaceItem workspaceItem = workspaceItemService.create(context, getCollection(), true);
        Item item = workspaceItem.getItem();

        addMetadataValues(orgUnitRow, item);

        item = installItemService.installItem(context, workspaceItem);

        handler.logInfo("Row " + orgUnitRow.getIndex() + " - Imported OrgUnit with acronym "
            + getOrgUnitAcronym(orgUnitRow) + ". Created item with UUID: " + item.getID());

        return item;
    }

    private void addMetadataValues(OrgUnitRow orgUnitRow, Item item) throws SQLException {
        List<MetadataValueDTO> metadataValues = getMetadataValues(orgUnitRow);
        addMetadataValues(item, metadataValues);
    }

    private List<MetadataValueDTO> getMetadataValues(OrgUnitRow orgUnitRow) {
        if (isOrgUnitActive(orgUnitRow)) {
            return getMetadataValuesFromAPI(orgUnitRow);
        } else {
            return getMetadataValuesFromOrgUnitRow(orgUnitRow);
        }
    }

    private void addMetadataValues(Item item, List<MetadataValueDTO> metadataValues) throws SQLException {
        for (MetadataValueDTO metadataValue : metadataValues) {
            itemService.addMetadata(context, item, metadataValue.getSchema(), metadataValue.getElement(),
                metadataValue.getQualifier(), metadataValue.getLanguage(), metadataValue.getValue(),
                metadataValue.getAuthority(), metadataValue.getConfidence());
        }
    }

    private boolean isOrgUnitActive(OrgUnitRow orgUnitRow) {
        return orgUnitRow.getValue(activeOrgUnitAcronymHeader).isPresent();
    }

    private List<MetadataValueDTO> getMetadataValuesFromAPI(OrgUnitRow orgUnitRow) {

        handler.logInfo("Row " + orgUnitRow.getIndex() + " - Reading Active OrgUnit data from API");

        return null;
    }

    private List<MetadataValueDTO> getMetadataValuesFromOrgUnitRow(OrgUnitRow orgUnitRow) {

        handler.logInfo("Row " + orgUnitRow.getIndex() + " - Reading Inactive OrgUnit data from TSV");

        return null;
    }

    private Item updateOrgUnit(Item item, OrgUnitRow orgUnitRow) throws SQLException, AuthorizeException {

        removeItemMetadataValues(item);

        addMetadataValues(orgUnitRow, item);

        itemService.update(context, item);

        handler.logInfo("Row " + orgUnitRow.getIndex() + " - Imported OrgUnit with acronym "
            + getOrgUnitAcronym(orgUnitRow) + ". Updated item with UUID: " + item.getID());

        return item;
    }

    private void removeItemMetadataValues(Item item) throws SQLException {

        Set<String> metadataFieldsToKeep = getMetadataFieldsToKeep();

        List<MetadataValue> metadataToRemove = item.getMetadata().stream()
            .filter(value -> !metadataFieldsToKeep.contains(value.getMetadataField().toString('.')))
            .collect(Collectors.toList());

        itemService.removeMetadataValues(context, item, metadataToRemove);

    }

    private Set<String> getMetadataFieldsToKeep() {
        return Set.of(configurationService.getArrayProperty("epfl.orgunit-import.update.metadata-to-keep"));
    }

    private boolean hasAcronym(Item item, String acronym) {
        MetadataFieldName metadataFieldName = new MetadataFieldName(getOrgUnitAcronymMetadataField());
        return acronym.equals(itemService.getMetadataFirstValue(item, metadataFieldName, ANY));
    }

    private String getOrgUnitAcronym(OrgUnitRow orgUnitRow) {
        return orgUnitRow.getValue(activeOrgUnitAcronymHeader)
            .or(() -> orgUnitRow.getValue(inactiveOrgUnitAcronymHeader))
            .orElseThrow(() -> new IllegalArgumentException("No acronym found for the given row. Both "
                + activeOrgUnitAcronymHeader + " and " + inactiveOrgUnitAcronymHeader + " fields are unset"));
    }

    private String getOrgUnitAcronymMetadataField() {
        return configurationService.getProperty("epfl.orgunit-import.metadata-fields." + activeOrgUnitAcronymHeader);
    }

    private String getActiveOrgUnitAcronymHeader() {
        return configurationService.getProperty("epfl.orgunit-import.active-orgunit.acronym-header");
    }

    private String getInactiveOrgUnitAcronymHeader() {
        return configurationService.getProperty("epfl.orgunit-import.inactive-orgunit.acronym-header");
    }

    private void handleRowException(OrgUnitRow orgUnitRow, Exception ex) {

        errorsCount++;

        String errorMessage = "Row " + orgUnitRow.getIndex() + " - An error occurs importing the OrgUnit";
        LOGGER.error(errorMessage, ex);
        handler.logError(errorMessage + ". Error details: " + ExceptionUtils.getRootCauseMessage(ex));

        rollback();

    }

    private Collection getCollection() {
        try {
            return collectionService.find(context, UUID.fromString(collectionId));
        } catch (SQLException e) {
            throw new BulkImportException(e);
        }
    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    private void rollback() {
        try {
            context.rollback();
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }
    }

    private void assignSpecialGroupsInContext() throws SQLException {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public OrgUnitTSVImportScriptConfiguration<OrgUnitTSVImportScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("orgunit-tsv-import",
            OrgUnitTSVImportScriptConfiguration.class);
    }

}
