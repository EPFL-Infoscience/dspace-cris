/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static com.google.common.collect.Streams.concat;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.dspace.authority.service.AuthorityValueService.GENERATE;
import static org.dspace.authority.service.AuthorityValueService.REFERENCE;
import static org.dspace.content.authority.Choices.CF_AMBIGUOUS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.dspace.app.bulkimport.exception.BulkImportException;
import org.dspace.app.bulkimport.service.BulkImportWorkbookBuilder;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.authority.Choices;
import org.dspace.content.dto.ItemDTO;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.epfl.script.model.OrgUnitTSV;
import org.dspace.epfl.script.model.OrgUnitTSV.OrgUnitRow;
import org.dspace.epfl.script.service.OrgUnitTSVParser;
import org.dspace.epfl.service.OrgUnitApiService;
import org.dspace.epfl.service.impl.OrgUnitApiServiceImpl;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrgUnitTSVImportScript
    extends DSpaceRunnable<OrgUnitTSVImportScriptConfiguration<OrgUnitTSVImportScript>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrgUnitTSVImportScript.class);

    private static final String INACTIVE_ORGUNITS_METADATA_PREFIX = "epfl.orgunit-import.inactive-orgunit.metadata";

    private static final String ACTIVE_ORGUNITS_METADATA_PREFIX = "epfl.orgunit-import.active-orgunit.metadata";

    private OrgUnitTSVParser orgUnitTSVParser;

    private CollectionService collectionService;

    private AuthorizeService authorizeService;

    private ConfigurationService configurationService;

    private BulkImportWorkbookBuilder workbookBuilder;

    private OrgUnitApiService orgUnitApiService;

    private String collectionId;

    private String filename;

    private Boolean isIntegratedMode;

    private Context context;


    private int importedOrgUnitsCount = 0;

    private int errorsCount = 0;

    private String activeOrgUnitAcronymHeader;

    private String inactiveOrgUnitAcronymHeader;


    @Override
    public void setup() throws ParseException {

        this.workbookBuilder = new DSpace().getServiceManager()
            .getServicesByType(BulkImportWorkbookBuilder.class).get(0);
        this.orgUnitApiService = new DSpace().getServiceManager()
            .getServicesByType(OrgUnitApiService.class).get(0);
        this.collectionService = ContentServiceFactory.getInstance().getCollectionService();
        this.authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        this.orgUnitTSVParser = new DSpace().getServiceManager().getServicesByType(OrgUnitTSVParser.class).get(0);

        collectionId = commandLine.getOptionValue('c');
        filename = commandLine.getOptionValue('f');
        isIntegratedMode = commandLine.hasOption('i');

        activeOrgUnitAcronymHeader = getActiveOrgUnitAcronymHeader();
        inactiveOrgUnitAcronymHeader = getInactiveOrgUnitAcronymHeader();

    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
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
            buildWorkbook(inputStream);
            context.complete();
            context.restoreAuthSystemState();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        }

    }

    private void buildWorkbook(InputStream inputStream) throws Exception {

        OrgUnitTSV orgUnitTSV = orgUnitTSVParser.parseTSV(inputStream);

        Workbook workbook = buildWorkbook(orgUnitTSV);

        writeWorkbook(workbook);

    }

    private Workbook buildWorkbook(OrgUnitTSV orgUnitTSV) {

        handler.logInfo("Found " + orgUnitTSV.getRows().size() + " OrgUnits to be imported");

        List<ItemDTO> items = readOrgUnits(orgUnitTSV);

        Workbook workbook = workbookBuilder.build(context, getCollection(), items.iterator());

        handler.logInfo("Import completed. OrgUnits written with success: " + importedOrgUnitsCount
            + ". Errors: " + errorsCount);

        ((OrgUnitApiServiceImpl) orgUnitApiService).clearActiveOrgUnitsCache();

        return workbook;

    }

    private void writeWorkbook(Workbook workbook) throws IOException, SQLException, AuthorizeException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        InputStream is = new ByteArrayInputStream(bos.toByteArray());
        handler.writeFilestream(context, "orgUnits.xls", is, "application/vnd.ms-excel", false);
    }

    private List<ItemDTO> readOrgUnits(OrgUnitTSV orgUnitTSV) {
        return orgUnitTSV.getRows().stream()
            .flatMap(orgUnitRow -> readOrgUnit(orgUnitRow).stream())
            .collect(Collectors.toList());
    }

    private Optional<ItemDTO> readOrgUnit(OrgUnitRow orgUnitRow) {

        try {

            String orgUnitAcronym = getOrgUnitAcronym(orgUnitRow);

            List<MetadataValueDTO> metadataValues = getMetadataValues(orgUnitRow, orgUnitAcronym);

            importedOrgUnitsCount++;

            return Optional.of(new ItemDTO("ACRONYM::" + orgUnitAcronym, metadataValues));

        } catch (Exception ex) {
            handleRowException(orgUnitRow, ex);
            return Optional.empty();
        }

    }

    private List<MetadataValueDTO> getMetadataValues(OrgUnitRow orgUnitRow, String orgUnitAcronym) {
        if (isIntegratedMode) {
            return getMetadataValuesFromOrgUnitRow(orgUnitRow);
        } else if (orgUnitApiService.isOrgUnitActive(orgUnitAcronym)) {
            return getMetadataValuesFromAPI(orgUnitRow);
        } else {
            return getMetadataValuesFromOrgUnitRow(orgUnitRow);
        }
    }

    private List<MetadataValueDTO> getMetadataValuesFromAPI(OrgUnitRow orgUnitRow) {

        handler.logInfo("Row " + orgUnitRow.getIndex() + " - Reading Active OrgUnit data from API");

        String orgUnitAcronym = getOrgUnitAcronym(orgUnitRow);

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        metadataValues.addAll(getCommonMetadataValues(orgUnitRow, true));

        metadataValues.addAll(orgUnitApiService.getMetadataValues(orgUnitAcronym));

        return metadataValues;
    }

    private List<MetadataValueDTO> getMetadataValuesFromOrgUnitRow(OrgUnitRow orgUnitRow) {

        handler.logInfo("Row " + orgUnitRow.getIndex() + " - Reading Inactive OrgUnit data from TSV");

        List<MetadataValueDTO> metadataValues = new ArrayList<>();

        metadataValues.addAll(getCommonMetadataValues(orgUnitRow, false));

        getHeadMetadataValue(orgUnitRow).ifPresent(metadataValues::add);
        getParentOrgUnitMetadataValue(orgUnitRow).ifPresent(metadataValues::add);

        return metadataValues;

    }

    private Optional<MetadataValueDTO> getHeadMetadataValue(OrgUnitRow orgUnitRow) {

        String metadataField = getHeadMetadataField();
        if (StringUtils.isBlank(metadataField)) {
            return Optional.empty();
        }

        String name = getHeadName(orgUnitRow);
        if (StringUtils.isBlank(name)) {
            return Optional.empty();
        }

        String authority = getHeadAuthority(orgUnitRow);
        int confidence = StringUtils.isNotBlank(authority) ? Choices.CF_AMBIGUOUS : Choices.CF_UNSET;

        return Optional.of(new MetadataValueDTO(metadataField, name, authority, confidence));

    }

    private String getHeadName(OrgUnitRow orgUnitRow) {
        String firstName = getHeadFirstNameHeader();
        String lastName = getHeadLastNameHeader();
        return concat(orgUnitRow.getValue(lastName).stream(), orgUnitRow.getValue(firstName).stream())
            .collect(Collectors.joining(", "));
    }

    private String getHeadAuthority(OrgUnitRow orgUnitRow) {
        String headAuthorityPrefix = getHeadAuthorityPrefix();
        String headSciperIdHeader = getHeadSciperIdHeader();
        return orgUnitRow.getValue(headSciperIdHeader)
            .map(sciperId -> isNotBlank(headAuthorityPrefix) ? headAuthorityPrefix + sciperId : sciperId)
            .orElse(null);
    }

    private Optional<MetadataValueDTO> getParentOrgUnitMetadataValue(OrgUnitRow orgUnitRow) {

        String metadataField = getParentMetadataField();

        if (StringUtils.isBlank(metadataField)) {
            return Optional.empty();
        }

        return orgUnitRow.getValue(getParentAcronym())
            .map(acronym -> new MetadataValueDTO(metadataField, acronym, getOrgUnitAuthority(acronym), CF_AMBIGUOUS));

    }

    private String getOrgUnitAuthority(String acronym) {
        String authorityPrefix = getParentAuthorityPrefix();
        if (StringUtils.isBlank(authorityPrefix)) {
            return acronym;
        }

        String willBePrefix;
        try {
            willBePrefix = orgUnitApiService.isOrgUnitActive(acronym) ? GENERATE : REFERENCE;
        } catch (Exception e) {
            handler.logError("Error retrieving org unit status for acronym " + acronym);
            willBePrefix = REFERENCE;
        }
        return willBePrefix + authorityPrefix + acronym;
    }

    private List<MetadataValueDTO> getCommonMetadataValues(OrgUnitRow orgUnitRow, boolean active) {

        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        getActiveOrgUnitMetadataField()
            .ifPresent(field -> metadataValues.add(new MetadataValueDTO(field, String.valueOf(active))));

        getConfiguredHeaders(active).stream()
            .flatMap(header -> getMetadataValue(header, orgUnitRow).stream())
            .forEach(metadataValues::add);

        return metadataValues;
    }

    private Optional<MetadataValueDTO> getMetadataValue(String configuredHeader, OrgUnitRow orgUnitRow) {

        for (String header : orgUnitRow.getHeaders()) {

            if (header.equals(configuredHeader) || getHeaderWithoutLanguage(header).equals(configuredHeader)) {

                String metadataField = getMetadataFieldForHeader(configuredHeader);
                if (StringUtils.isBlank(metadataField)) {
                    continue;
                }

                return orgUnitRow.getValue(header)
                    .map(value -> new MetadataValueDTO(metadataField, getLanguageFromHeader(header), value));

            }

        }

        return Optional.empty();
    }

    private String getMetadataFieldForHeader(String header) {

        String configurationPrefix = INACTIVE_ORGUNITS_METADATA_PREFIX + ".";

        String field = configurationService.getProperty(configurationPrefix + header);
        if (StringUtils.isBlank(field) && StringUtils.isNotBlank(getLanguageFromHeader(header))) {
            field = configurationService.getProperty(configurationPrefix + getHeaderWithoutLanguage(header));
        }

        return field;
    }

    private List<String> getConfiguredHeaders(boolean active) {
        String prefix = active ? ACTIVE_ORGUNITS_METADATA_PREFIX : INACTIVE_ORGUNITS_METADATA_PREFIX;
        return configurationService.getPropertyKeys(prefix).stream()
            .map(key -> StringUtils.removeStart(key, prefix + "."))
            .collect(Collectors.toList());
    }

    private String getHeaderWithoutLanguage(String header) {
        return header.split("-")[0];
    }

    private String getLanguageFromHeader(String header) {
        return header.contains("-") ? header.split("-")[1] : null;
    }

    private String getOrgUnitAcronym(OrgUnitRow orgUnitRow) {
        return orgUnitRow.getValue(activeOrgUnitAcronymHeader)
            .or(() -> orgUnitRow.getValue(inactiveOrgUnitAcronymHeader))
            .orElseThrow(() -> new IllegalArgumentException("No acronym found for the given row. Both "
                + activeOrgUnitAcronymHeader + " and " + inactiveOrgUnitAcronymHeader + " fields are unset"));
    }

    private String getActiveOrgUnitAcronymHeader() {
        return configurationService.getProperty("epfl.orgunit-import.active-orgunit.acronym-header");
    }

    private String getInactiveOrgUnitAcronymHeader() {
        return configurationService.getProperty("epfl.orgunit-import.inactive-orgunit.acronym-header");
    }

    private String getHeadMetadataField() {
        return configurationService.getProperty("epfl.orgunit-import.inactive-orgunit.head.metadata");
    }

    private String getHeadAuthorityPrefix() {
        return configurationService.getProperty("epfl.orgunit-import.inactive-orgunit.head.authority");
    }

    private String getHeadFirstNameHeader() {
        return configurationService.getProperty("epfl.orgunit-import.inactive-orgunit.head.first-name");
    }

    private String getHeadLastNameHeader() {
        return configurationService.getProperty("epfl.orgunit-import.inactive-orgunit.head.last-name");
    }

    private String getHeadSciperIdHeader() {
        return configurationService.getProperty("epfl.orgunit-import.inactive-orgunit.head.sciper-id");
    }

    private String getParentMetadataField() {
        return configurationService.getProperty("epfl.orgunit-import.inactive-orgunit.parent-orgunit.metadata");
    }

    private String getParentAcronym() {
        return configurationService.getProperty("epfl.orgunit-import.inactive-orgunit.parent-orgunit.acronym");
    }

    private String getParentAuthorityPrefix() {
        return configurationService.getProperty("epfl.orgunit-import.inactive-orgunit.parent-orgunit.authority");
    }

    private Optional<String> getActiveOrgUnitMetadataField() {
        return ofNullable(configurationService.getProperty("epfl.orgunit-import.active-metadata-field"));
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
