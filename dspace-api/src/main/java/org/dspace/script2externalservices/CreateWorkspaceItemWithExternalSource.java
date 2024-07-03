/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.script2externalservices;

import static org.apache.commons.collections4.IteratorUtils.chainedIterator;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.CollectionServiceImpl;
import org.dspace.content.DCDate;
import org.dspace.content.InstallItemServiceImpl;
import org.dspace.content.Item;
import org.dspace.content.ItemServiceImpl;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.packager.PackageUtils;
import org.dspace.content.service.InstallItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverQuery.SORT_ORDER;
import org.dspace.discovery.DiscoverResultItemIterator;
import org.dspace.discovery.DiscoverResultIterator;
import org.dspace.discovery.IndexingService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.SearchUtils;
import org.dspace.discovery.indexobject.IndexableCollection;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.discovery.indexobject.IndexableWorkflowItem;
import org.dspace.discovery.indexobject.IndexableWorkspaceItem;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.external.model.ExternalDataObject;
import org.dspace.external.provider.impl.LiveImportDataProvider;
import org.dspace.external.service.ExternalDataService;
import org.dspace.external.service.impl.ExternalDataServiceImpl;
import org.dspace.kernel.ServiceManager;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.dspace.workflow.WorkflowException;
import org.dspace.workflow.WorkflowService;
import org.dspace.workflow.factory.WorkflowServiceFactory;
import org.hibernate.LazyInitializationException;

/**
 * Implementation of {@link DSpaceRunnable}
 * to import Publications from external services as Scopus | Web Of Science | CrossRef.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 */
public class CreateWorkspaceItemWithExternalSource extends DSpaceRunnable<
       CreateWorkspaceItemWithExternalSourceScriptConfiguration<CreateWorkspaceItemWithExternalSource>> {

    private static final Logger log = LogManager.getLogger(CreateWorkspaceItemWithExternalSource.class);

    public static final String WOS = "wos";
    public static final String SCOPUS = "scopus";
    public static final String CROSSREF = "crossref";
    private static final String ARXIV = "arxiv";
    private static final String EPO = "epo";
    private static final String WORKFLOW_STATE = "workflow";
    private static final String WORKSPACE_STATE = "workspace";
    private static final String ARCHIVED_ITEM_STATE = "item";
    private static final String PUBLICATION = "Publication";
    private static final String PATENT = "Patent";
    private static final int LIMIT = 10;

    int importedItemsCounter = 0;

    private String service;

    private String extraQuery;

    private String collectionUuid;

    private String finalState;

    private List<String> workspaceItemImportedDoi;

    private Integer totalSearchLimit;

    private Integer perResearcherSearchLimit;

    private Context context;

    private ItemServiceImpl itemService;

    private CollectionServiceImpl collectionService;

    private ExternalDataService externalDataService;

    private Collection collection;

    private ConfigurationService configurationService;

    private Map<String, LiveImportDataProvider> nameToProvider = new HashMap<>();

    @SuppressWarnings("rawtypes")
    private WorkflowService workflowService;

    private EPersonService ePersonService;

    private AuthorizeService authorizeService;

    private InstallItemService installItemService;

    private IndexingService indexingService;

    @Override
    public void setup() throws ParseException {
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        ServiceManager serviceManager = new DSpace().getServiceManager();
        itemService = serviceManager.getServiceByName(ItemServiceImpl.class.getName(), ItemServiceImpl.class);
        installItemService = serviceManager.getServiceByName(InstallItemServiceImpl.class.getName(),
                                                             InstallItemServiceImpl.class);
        collectionService = serviceManager.getServiceByName(CollectionServiceImpl.class.getName(),
                                                            CollectionServiceImpl.class);
        externalDataService = serviceManager.getServiceByName(ExternalDataServiceImpl.class.getName(),
                                                              ExternalDataServiceImpl.class);
        indexingService = serviceManager.getServiceByName(IndexingService.class.getName(), IndexingService.class);
        putServiceIfExists(SCOPUS,"scopusLiveImportDataProviderProcess");
        putServiceIfExists(WOS, "wosLiveImportDataProviderProcess");
        putServiceIfExists(CROSSREF, "crossRefLiveImportDataProviderProcess");
        putServiceIfExists(ARXIV, "arxivLiveImportDataProviderProcess");
        putServiceIfExists(EPO, "epoLiveImportDataProviderProcess");

        workflowService = WorkflowServiceFactory.getInstance().getWorkflowService();
        ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();

        this.service = commandLine.getOptionValue('s');
        this.finalState = commandLine.getOptionValue('f');
        this.collectionUuid = commandLine.getOptionValue('c');
        this.extraQuery = commandLine.getOptionValue('q');
        this.totalSearchLimit = commandLine.hasOption('l')
            ? Integer.valueOf(commandLine.getOptionValue('l'))
            : getDefaultTotalSearchLimit();
        this.perResearcherSearchLimit = getDefaultPerResearcherSearchLimit();
        workspaceItemImportedDoi = new ArrayList<>();
        importedItemsCounter = 0;
    }

    private void putServiceIfExists(String key, String serviceName) {
        ServiceManager serviceManager = new DSpace().getServiceManager();
        if (serviceManager.isServiceExists(serviceName)) {
            nameToProvider.put(key, serviceManager.getServiceByName(serviceName, LiveImportDataProvider.class));
        }
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        context.setCurrentUser(findEPerson());
        if (Objects.isNull(service)) {
            throw new IllegalArgumentException("The name of service must be provided");
        }

        if (StringUtils.isBlank(finalState) || isNotSupportedState()) {
            throw new IllegalArgumentException("The provided final state: (" + finalState + ") is not supported,"
                                             + " it must be one of this: workspace, workflow or item");
        }

        if (totalSearchLimit < 0) {
            throw new IllegalArgumentException("The search limit value must be a positive integer");
        }

        LiveImportDataProvider dataProvider = nameToProvider.get(service);
        if (Objects.isNull(dataProvider)) {
            throw new IllegalArgumentException("The " + service + " provider does not exist");
        }
        dataProvider.setHandler(handler);

        UUID collectionUUID = getCollectionUUID();
        collection = Objects.nonNull(collectionUUID)
            ? collectionService.find(context, collectionUUID)
            : getCollectionByType(EPO.equals(service) ? PATENT : PUBLICATION);

        if (Objects.isNull(collection)) {
            throw new RuntimeException("Collection with uuid" + collectionUUID + "does not exist!");
        }

        validateCollectionEntityType();

        if (!authorizeService.isAdmin(context, collection)) {
            throw new RuntimeException("User " + context.getCurrentUser().getEmail() + " cannot submit to collection "
            + collection.getID());
        }

        try {
            context.turnOffAuthorisationSystem();
            performCreatingOfWorkspaceItems(dataProvider);
            context.complete();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            handler.handleException(e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private boolean isNotSupportedState() {
        return !Arrays.asList(WORKSPACE_STATE, WORKFLOW_STATE, ARCHIVED_ITEM_STATE).contains(this.finalState);
    }

    private UUID getCollectionUUID() {
        return StringUtils.isNoneBlank(collectionUuid)
            ? UUID.fromString(collectionUuid)
            : getUuid(service + ".importworkspaceitem.collection-id");
    }

    private void validateCollectionEntityType() {
        String entityType = collection.getEntityType();
        switch (service) {
            case SCOPUS:
            case WOS:
            case CROSSREF:
            case ARXIV:
                if (!PUBLICATION.equals(entityType)) {
                    throw new RuntimeException("Collection type must be Publication, but actually is " + entityType);
                }
                break;
            case EPO:
                if (!PATENT.equals(entityType)) {
                    throw new RuntimeException("Collection type must be Patent, but actually is " + entityType);
                }
                break;
            default:
        }
    }

    private UUID getUuid(final String property) {
        final String propertyValue = configurationService.getProperty(property);
        return StringUtils.isBlank(propertyValue) ? null : UUID.fromString(propertyValue);
    }

    private EPerson findEPerson() throws SQLException {
        String email = commandLine.getOptionValue('e');
        if (StringUtils.isNotBlank(email)) {
            EPerson byEmail = ePersonService.findByEmail(context, email);
            if (Objects.nonNull(byEmail)) {
                return byEmail;
            }
        }
        UUID uuid = getEpersonIdentifier();
        return uuid != null ? ePersonService.find(context, uuid) : null;
    }

    private void performCreatingOfWorkspaceItems(LiveImportDataProvider dataProvider) throws SQLException {

        int searchCount = 0;
        int totalRecordWorked = 0;
        int totalItemsProcessed = 0;
        int recordsFound = 0;
        int arxivCallCount = 4;


        try {
            Iterator<Item> itemIterator = findItems();
            handler.logInfo("Update start");
            final String sourceIdentifier = dataProvider.getSourceIdentifier();
            while (itemIterator.hasNext() && searchCount < totalSearchLimit) {
                Item item = itemIterator.next();
                String id = buildID(item);
                if (StringUtils.isNotBlank(id)) {
                    int currentRecord = 0;
                    if (sourceIdentifier.equals(ARXIV)) {
                        if (arxivCallCount == 0) {
                            Thread.sleep(1000);
                            arxivCallCount = 4;
                        }
                        arxivCallCount--;
                    }
                    recordsFound = dataProvider.getNumberOfResults(id);
                    // retrieving the number of result usually required 1 search call
                    searchCount++;
                    handler.logInfo("Found " + recordsFound + " records for researcher " + id +
                                        " that could be imported");
                    if (recordsFound > perResearcherSearchLimit) {
                        handler.logInfo(
                            recordsFound + " exceeds import limit per researcher, importing only first "
                                + perResearcherSearchLimit + " records");
                        recordsFound = perResearcherSearchLimit;
                    }
                    int[] userPublicationsProcessed = new int[] {0, 0};
                    int iterations = recordsFound <= 0 ? 0
                            : (recordsFound / LIMIT) + (recordsFound % LIMIT == 0 ? 0 : 1);
                    for (int i = 1; i <= iterations && searchCount < totalSearchLimit; i++) {
                        int[] resultFill = fillWorkspaceItems(context, currentRecord, dataProvider, id, getOwner(item));
                        userPublicationsProcessed[0] += resultFill[0];
                        userPublicationsProcessed[1] += resultFill[1];
                        searchCount++;
                        currentRecord += LIMIT;
                    }
                    setLastImportMetadataValue(item);
                    context.uncacheEntity(item);
                    totalRecordWorked += userPublicationsProcessed[0];
                    totalItemsProcessed += userPublicationsProcessed[1];
                    if (userPublicationsProcessed[0] >= 1) {
                        context.commit();
                        // to ensure that collection's template item is fully initialized
                        reloadCollectionIfNeeded();
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (context.isValid()) {
                context.commit();
            }
            printImportedItemsSummary();
        }
        handler.logInfo("Processed " + totalRecordWorked + " records, " + totalItemsProcessed + " imported");
        if (searchCount == totalSearchLimit) {
            handler.logInfo("Process terminated as we reach the global limit of search call for execution: "
                    + totalSearchLimit + ", this can be set using the -l parameter or "
                    + "updating the default value in the configuration");
        }
        handler.logInfo("Update end");
    }

    private void printImportedItemsSummary() {
        handler.logInfo("SUMMARY: with process " + importedItemsCounter + " items were imported");
    }

    private MetadataValue getOwner(Item item) {
        return itemService.getMetadataByMetadataString(item, "dspace.object.owner").stream()
                          .findFirst().orElse(null);
    }

    /**
     * utility method to check that we have collection's item fully loaded with template item fully initialized,
     * in order to avoid lazy initialization exceptions.
     *
     * @throws SQLException e
     */
    private void reloadCollectionIfNeeded() throws SQLException {
        boolean needsReload;
        try {
            needsReload = Objects.isNull(collection.getTemplateItem()) ||
                CollectionUtils.isEmpty(collection.getTemplateItem().getMetadata());
        } catch (LazyInitializationException e) {
            log.warn(e.getMessage());
            needsReload = true;
        }
        if (needsReload) {
            log.debug("Reloading collection");
            collection = context.reloadEntity(collection);
        }
    }

    private String buildID(Item item) {
        StringBuilder id = new StringBuilder();
        switch (service) {
            case CROSSREF:
                String orcid = itemService.getMetadataFirstValue(item, "person", "identifier", "orcid", Item.ANY);
                if (StringUtils.isNotBlank(orcid)) {
                    id.append(orcid);
                }
                break;
            case SCOPUS:
                String scopusId = itemService.getMetadataFirstValue(
                                  item, "person", "identifier", "scopus-author-id", Item.ANY);
                if (StringUtils.isNotBlank(scopusId)) {
                    id.append("AU-ID(").append(scopusId).append(")");
                }
                break;
            case WOS:
                String orcidId = itemService.getMetadataFirstValue(item, "person", "identifier", "orcid", Item.ANY);
                String rid = itemService.getMetadataFirstValue(item, "person", "identifier", "rid", Item.ANY);
                if (StringUtils.isNotBlank(orcidId) && StringUtils.isNotBlank(rid)) {
                    id.append("AI=(").append(orcidId).append(" OR ").append(rid).append(")");
                } else if (StringUtils.isNotBlank(orcidId)) {
                    id.append("AI=(").append(orcidId).append(")");
                } else if (StringUtils.isNotBlank(rid)) {
                    id.append("AI=(").append(rid).append(")");
                }
                break;
            case ARXIV:
                id.append("au:");
                String dcTitle = itemService.getMetadataFirstValue(item, "dc", "title", null, Item.ANY);
                if (StringUtils.isNotBlank(dcTitle)) {
                    id.append("\"").append(dcTitle).append("\"");
                }
                break;
            case EPO:
                id.append("in=");
                String epoTitle = itemService.getMetadataFirstValue(item, "dc", "title", null, Item.ANY);
                if (StringUtils.isNotBlank(epoTitle)) {
                    id.append("\"").append(epoTitle).append("\"");
                }
                break;
            default:
        }
        if (StringUtils.isNotBlank(id.toString()) && StringUtils.isNotBlank(extraQuery)) {
            if (ARXIV.equals(service)) {
                id.append(" AND ");
                if (extraQuery.startsWith("ti:")) {
                    id.append("ti:\"").append(extraQuery.substring(3)).append("\"");
                } else {
                    id.append(extraQuery);
                }
            } else if (EPO.equals(service)) {
                id.append(" AND ").append(extraQuery);
            } else {
                id.append(" ").append(extraQuery);
            }
        }
        return id.toString();
    }

    private int[] fillWorkspaceItems(Context context, int record, LiveImportDataProvider dataProvider,
                                     String id, MetadataValue owner) throws SQLException {
        int countDataObjects = 0;
        int imported = 0;
        try {
            for (ExternalDataObject dataObject : dataProvider.searchExternalDataObjects(id, record, LIMIT)) {
                if (!exist(dataObject.getMetadata()) && !isExternalIdentifierAlreadyImported(dataObject.getId())) {
                    WorkspaceItem wsItem = externalDataService
                        .createWorkspaceItemFromExternalDataObject(context, dataObject, collection);
                    Item itemFromWs = wsItem.getItem();
                    PackageUtils.addDepositLicense(context, null, itemFromWs, wsItem.getCollection());
                    itemService.addMetadata(context, wsItem.getItem(), "cris", "source", "name", null, this.service);
                    if (owner != null) {
                        updateSubmitter(wsItem.getItem(), owner);
                    }
                    if (!StringUtils.equals(finalState, WORKSPACE_STATE)) {
                        makeFinalState(wsItem);
                    }
                    context.uncacheEntity(itemFromWs);
                    handler.logInfo("Created item with id " + wsItem.getItem().getID() +
                            " from the identifier " + dataObject.getId() +
                            " with the status: " + finalState);
                    importedItemsCounter++;
                    imported++;
                    String externalId = dataObject.getId();
                    if (StringUtils.isNotBlank(externalId)) {
                        workspaceItemImportedDoi.add(externalId);
                    }
                }
                countDataObjects++;
            }
        } catch (AuthorizeException | IOException | WorkflowException e) {
            log.error(e.getMessage(), e);
        }
        return new int[] {countDataObjects, imported};
    }

    private boolean isExternalIdentifierAlreadyImported(String identifier) {
        if (workspaceItemImportedDoi.contains(identifier)) {
            handler.logInfo("Ignoring record with identifier " + identifier + " already imported in this thread ");
            return true;
        }
        return false;
    }

    private void makeFinalState(WorkspaceItem wsItem)
            throws SQLException, AuthorizeException, IOException, WorkflowException {
        if (StringUtils.equals(finalState, WORKFLOW_STATE)) {
            workflowService.start(context, wsItem);
        }
        if (StringUtils.equals(finalState, ARCHIVED_ITEM_STATE)) {
            installItemService.installItem(context, wsItem);
        }
    }

    private void updateSubmitter(Item item, MetadataValue submitter) {
        if (StringUtils.isBlank(submitter.getAuthority())) {
            return;
        }
        try {
            EPerson ePerson = ePersonService.findByIdOrLegacyId(context, submitter.getAuthority());
            if (Objects.isNull(ePerson) || ePerson.equals(item.getSubmitter())) {
                return;
            }
            EPerson previousSubmitter = item.getSubmitter();
            item.setSubmitter(ePerson);
            int[] actionIds = { Constants.READ, Constants.WRITE, Constants.ADD, Constants.REMOVE, Constants.DELETE };
            for (int actionId : actionIds) {
                authorizeService.removeEPersonPolicies(context, item, previousSubmitter);
                authorizeService.addPolicy(context, item, actionId, item.getSubmitter(),
                                           ResourcePolicy.TYPE_SUBMISSION);
            }
        } catch (Exception e) {
            handler.logWarning("Unable to update submitter for item " + item.getID() + " : " + e.getMessage());
        }
    }

    private void addMetadata(Item item, List<MetadataValueDTO> metadataList) throws SQLException {
        for (MetadataValueDTO metadataValue : metadataList) {
            itemService.addMetadata(context, item, metadataValue.getSchema(), metadataValue.getElement(),
                metadataValue.getQualifier(), null, metadataValue.getValue());
        }
    }

    private boolean exist(List<MetadataValueDTO> metadataList) {
        if (metadataList.isEmpty()) {
            return false;
        }
        MetadataValueDTO metadata = getMetadataToCheck();
        for (MetadataValueDTO mv : metadataList) {
            String schema = mv.getSchema();
            String element = mv.getElement();
            String qualifier = mv.getQualifier();
            if (StringUtils.equals(schema, metadata.getSchema()) && StringUtils.equals(element, metadata.getElement())
                    && StringUtils.equals(qualifier, metadata.getQualifier())) {

                String value = mv.getValue();
                StringBuilder filter = new StringBuilder();
                filter.append(metadata.getSchema()).append(".").append(metadata.getElement());
                if (StringUtils.isNotBlank(metadata.getQualifier())) {
                    filter.append(".").append(metadata.getQualifier()).append(":\"").append(value).append("\"");
                } else {
                    filter.append(":\"").append(value).append("\"");
                }
                try {
                    Iterator<Item> itemIterator = findItemsInDSpace(context, filter.toString());
                    if (itemIterator.hasNext()) {
                        handler.logInfo("Ignoring record with identifier " + value + " already in the repository "
                                + itemIterator.next().getID().toString());
                        return true;
                    }
                } catch (SearchServiceException e) {
                    log.error(e.getMessage(), e);
                }
                return false;
            }
        }
        return false;
    }

    private MetadataValueDTO getMetadataToCheck() {
        MetadataValueDTO metadata = new MetadataValueDTO();
        metadata.setSchema("dc");
        metadata.setElement("identifier");

        switch (service) {
            case SCOPUS:
                metadata.setQualifier(SCOPUS);
                break;
            case WOS:
                metadata.setQualifier("isi");
                break;
            case CROSSREF:
                metadata.setQualifier("doi");
                break;
            case ARXIV:
                metadata.setQualifier(ARXIV);
                break;
            case EPO:
                metadata.setQualifier("applicationnumber");
                break;
            default:
        }
        return metadata;
    }

    private Iterator<Item> findItemsInDSpace(Context context, String filter) throws SearchServiceException {
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.addDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.addDSpaceObjectFilter(IndexableWorkspaceItem.TYPE);
        discoverQuery.addDSpaceObjectFilter(IndexableWorkflowItem.TYPE);
        discoverQuery.setMaxResults(20);
        discoverQuery.addFilterQueries(filter);
        return new DiscoverResultItemIterator(context, discoverQuery);
    }

    private Iterator<Item> findItems() {
        return chainedIterator(findItems(false), findItems(true));
    }

    private Iterator<Item> findItems(boolean withLastImport) {

        DiscoverQuery discoverQuery = new DiscoverQuery();

        discoverQuery.setDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.setMaxResults(20);
        setFilterQueries(discoverQuery);

        String lastImportMetadataField = getLastImportMetadataField();
        if (withLastImport) {
            // set an upper limit to prevent items updated in the same run from being pulled out again.
            discoverQuery.setQuery(lastImportMetadataField + ": [* TO NOW-1SECONDS]");
            discoverQuery.setSortField(lastImportMetadataField, SORT_ORDER.asc);
        } else {
            discoverQuery.setQuery("-" + lastImportMetadataField + ": [* TO *]");
            discoverQuery.setSortField(SearchUtils.LAST_INDEXED_FIELD, SORT_ORDER.asc);
        }

        return new DiscoverResultIterator<Item, UUID>(context, discoverQuery);
    }

    private String getLastImportMetadataField() {
        return "cris.lastimport." + service + "-publication_dt";
    }

    private void setFilterQueries(DiscoverQuery discoverQuery) {
        discoverQuery.addFilterQueries("search.entitytype:Person");

        if (SCOPUS.equals(service)) {
            discoverQuery.addFilterQueries("person.identifier.scopus-author-id:*");
        }
        if (WOS.equals(service)) {
            discoverQuery.addFilterQueries("person.identifier.orcid:* OR person.identifier.rid:*");
        }
        if (CROSSREF.equals(service)) {
            discoverQuery.addFilterQueries("person.identifier.orcid:*");
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    public CreateWorkspaceItemWithExternalSourceScriptConfiguration<CreateWorkspaceItemWithExternalSource>
        getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("import-publications",
                                                CreateWorkspaceItemWithExternalSourceScriptConfiguration.class);
    }

    private void setLastImportMetadataValue(Item item) {
        try {
            item = context.reloadEntity(item);
            String metadataField = "cris.lastimport." + service + "-publication";
            String currentDate = DCDate.getCurrent().toString();
            itemService.setMetadataSingleValue(context, item, new MetadataFieldName(metadataField), null, currentDate);
            itemService.update(context, item, false);
            indexingService.updateLastPublicationImport(context, item, service, currentDate);
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException(e);
        }
    }

    private Collection getCollectionByType(String entityType) {
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setDSpaceObjectFilter(IndexableCollection.TYPE);
        discoverQuery.setMaxResults(1);
        discoverQuery.addFilterQueries("search.entitytype:" + entityType);
        Iterator<Collection> collections = new DiscoverResultIterator<Collection, UUID>(context, discoverQuery);
        return collections.hasNext() ? collections.next() : null;
    }

    private Integer getDefaultTotalSearchLimit() {
        return configurationService.getIntProperty("importworkspaceitem.limit-total", Integer.MAX_VALUE);
    }

    private Integer getDefaultPerResearcherSearchLimit() {
        return configurationService.getIntProperty("importworkspaceitem.limit-per-researcher", 100);
    }

    public Map<String, LiveImportDataProvider> getNameToProvider() {
        return nameToProvider;
    }

    public void setNameToProvider(Map<String, LiveImportDataProvider> nameToProvider) {
        this.nameToProvider = nameToProvider;
    }

}
