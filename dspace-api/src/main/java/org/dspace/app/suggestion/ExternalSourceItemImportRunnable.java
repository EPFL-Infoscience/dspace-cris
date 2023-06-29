/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.suggestion;

import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.dspace.content.authority.Choices.CF_ACCEPTED;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResultItemIterator;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.discovery.indexobject.IndexableWorkflowItem;
import org.dspace.discovery.indexobject.IndexableWorkspaceItem;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.external.model.ExternalDataObject;
import org.dspace.external.service.ExternalDataService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;
import org.dspace.workflow.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link DSpaceRunnable} to import items from external source solr
 * By suggestion provider name, score and a target collection uuid
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 *
 */
public class ExternalSourceItemImportRunnable
    extends DSpaceRunnable<ExternalSourceItemImportScriptConfiguration<ExternalSourceItemImportRunnable>> {

    private static final org.apache.logging.log4j.Logger
        log = LogManager.getLogger(ExternalSourceItemImportRunnable.class);


    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalSourceItemImportRunnable.class);

    private SolrSuggestionStorageService solrSuggestionStorageService;
    private ExternalDataService externalDataService;
    private CollectionService collectionService;
    private WorkflowService workflowService;

    private Context context;
    private String source;
    private String score;
    private String collectionId;
    private String type;
    private String email;
    private String limit;
    private EPersonService ePersonService;
    private AuthorizeService authorizeService;
    private ItemService itemService;

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public ExternalSourceItemImportScriptConfiguration<ExternalSourceItemImportRunnable> getScriptConfiguration() {
        ExternalSourceItemImportScriptConfiguration configuration = new DSpace().getServiceManager()
                .getServiceByName("import-external-source-item", ExternalSourceItemImportScriptConfiguration.class);
        return configuration;
    }

    @Override
    public void setup() throws ParseException {
        this.solrSuggestionStorageService = ContentServiceFactory.getInstance().getSolrSuggestionStorageService();
        this.collectionService = ContentServiceFactory.getInstance().getCollectionService();
        this.externalDataService = ContentServiceFactory.getInstance().getExternalDataService();
        this.workflowService = ContentServiceFactory.getInstance().getWorkflowService();
        this.ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        this.authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
        this.itemService = ContentServiceFactory.getInstance().getItemService();

        source = commandLine.getOptionValue("p");
        score = commandLine.getOptionValue("s");
        collectionId = commandLine.getOptionValue("t");
        type = commandLine.getOptionValue("ty");
        email = commandLine.getOptionValue("e");
        limit = commandLine.getOptionValue("l");

    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        context.setCurrentUser(findEPerson());

        if (source == null || score == null || collectionId == null) {
            throw new NullPointerException("provider -p option and minimum score -s option " +
                "and collection uuid -u option can't be null");
        }

        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        try {
            context.turnOffAuthorisationSystem();
            performImportItemsExternalSource(context);
            context.complete();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private EPerson findEPerson() throws SQLException {
        EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        String email = this.email;
        if (StringUtils.isNotBlank(email)) {
            EPerson byEmail = ePersonService.findByEmail(context, email);
            if (Objects.nonNull(byEmail)) {
                return byEmail;
            }
        }
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            return ePersonService.find(context, uuid);
        }
        return null;
    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() throws SQLException {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }

    private void performImportItemsExternalSource(Context context) throws SQLException {
        List<Suggestion> suggestions = new ArrayList<>();
        int countRecordWorked = 0;
        int totalRecordWorked = 0;
        int totalItemsNotProcessed = 0;
        int limit = getLimit(this.limit);
        int pageSize = limit % 10 == 0 ? 10 : limit;
        int idx = pageSize;

        suggestions = StringUtils.isEmpty(type)
            ? findAllUnprocessedSuggestionsBySourceAndScore(context, source, score, 0, pageSize)
            : findAllUnprocessedSuggestionsBySourceAndScoreAndType(context, source, score, type, 0, pageSize);

        while (!isEmpty(suggestions) && idx <= limit) {
            countRecordWorked = fillWorkspaceItems(context, suggestions);
            totalRecordWorked += countRecordWorked;
            totalItemsNotProcessed += suggestions.size() - countRecordWorked;
            context.commit();
            idx += 10;
            suggestions = StringUtils.isEmpty(type)
                ? findAllUnprocessedSuggestionsBySourceAndScore(
                    context, source, score, totalItemsNotProcessed, totalItemsNotProcessed + 10)
                : findAllUnprocessedSuggestionsBySourceAndScoreAndType(
                    context, source, score, type, totalItemsNotProcessed, totalItemsNotProcessed + 10);
        }

        handler.logInfo("Processed " + totalRecordWorked + " records");
        handler.logInfo("Not Processed " + totalItemsNotProcessed + " records");
        handler.logInfo("Update end");
    }

    private int fillWorkspaceItems(Context context, List<Suggestion> suggestions) throws SQLException {
        int countDataObjects = 0;
        for (Suggestion suggestion : suggestions) {
            try {
                ExternalDataObject externalDataObject =
                    getExternalDataObjectFromUriList(suggestion.getExternalSourceUri());
                if (alreadyInRepository(externalDataObject)) {
                    solrSuggestionStorageService.flagSuggestionAsProcessed(suggestion);
                    continue;
                }
                WorkspaceItem workspaceItem = createWorkspaceItem(context, collectionId,
                    suggestion.getExternalSourceUri());
                Item target = suggestion.getTarget();
                if (Objects.nonNull(target)
                    && StringUtils.isNotBlank(target.getName())) {
                    getOwner(target).ifPresent(owner -> updateSubmitter(workspaceItem.getItem(), owner));
                    workspaceItem.getItem().getMetadata().stream()
                        .filter(mv -> target.getName().equals(mv.getValue()))
                        .findFirst()
                        .ifPresent(mv -> {
                            mv.setAuthority(UUIDUtils.toString(target.getID()));
                            mv.setConfidence(CF_ACCEPTED);
                        });
                }
                workflowService.start(context, workspaceItem);
                solrSuggestionStorageService.flagSuggestionAsProcessed(suggestion);
                countDataObjects++;
            } catch (Exception e) {
                handler.logError(e.getMessage());
            }
        }
        return countDataObjects;
    }

    private boolean alreadyInRepository(ExternalDataObject externalDataObject) {

        MetadataValueDTO metadata = getMetadataToCheck();
        return externalDataObjectInRepository(externalDataObject, metadata);
    }

    // FIXME: Refactor and use a common service since the logic is the same as
    //  org.dspace.script2externalservices.CreateWorkspaceItemWithExternalSource.exist
    private boolean externalDataObjectInRepository(ExternalDataObject externalDataObject, MetadataValueDTO metadata) {
        List<MetadataValueDTO> metadataList = externalDataObject.getMetadata();
        if (metadataList.isEmpty()) {
            return false;
        }
        for (MetadataValueDTO mv : metadataList) {
            String schema = mv.getSchema();
            String element = mv.getElement();
            String qualifier = mv.getQualifier();
            if (StringUtils.equals(schema, metadata.getSchema()) && StringUtils.equals(element, metadata.getElement())
                && StringUtils.equals(qualifier, metadata.getQualifier())) {

                String value = (mv.getValue()).replaceAll(":", "");
                StringBuilder filter = new StringBuilder();
                filter.append(metadata.getSchema()).append(".").append(metadata.getElement());
                if (StringUtils.isNotBlank(metadata.getQualifier())) {
                    filter.append(".").append(metadata.getQualifier()).append(":").append(value);
                } else {
                    filter.append(":").append(value);
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
        if (this.source.equals("pubmed")) {
            metadata.setSchema("dc");
            metadata.setElement("identifier");
            metadata.setQualifier("pmid");
        }
        return metadata;
    }

    private Iterator<Item> findItemsInDSpace(Context context, String filter)
        throws SearchServiceException {
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.addDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.addDSpaceObjectFilter(IndexableWorkspaceItem.TYPE);
        discoverQuery.addDSpaceObjectFilter(IndexableWorkflowItem.TYPE);
        discoverQuery.setMaxResults(20);
        discoverQuery.addFilterQueries(filter);
        return new DiscoverResultItemIterator(context, discoverQuery);
    }
    private Optional<MetadataValue> getOwner(Item item) {
        List<MetadataValue> metadataByMetadataString = itemService
            .getMetadataByMetadataString(item, "dspace.object.owner");
        return metadataByMetadataString.size() > 0 ? Optional.of(metadataByMetadataString.get(0)) : Optional.empty();
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

    private int getLimit(String limit) {
        return limit != null && !limit.isEmpty() ? Integer.parseInt(limit) : 1000;
    }

    private List<Suggestion> findAllUnprocessedSuggestionsBySourceAndScore(Context context, String source,
                                                                           String score, long offset, int pageSize) {
        try {
            return solrSuggestionStorageService.findAllUnprocessedSuggestionsBySourceAndScore(context, source, score,
                pageSize, offset, true);
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Suggestion> findAllUnprocessedSuggestionsBySourceAndScoreAndType(Context context, String source,
                                                                                  String score, String type,
                                                                                  long offset, int pageSize) {
        try {
            return solrSuggestionStorageService.findAllUnprocessedSuggestionsBySourceAndScoreAndType(
                context, source, score, type, pageSize, offset, true
            );
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method will create a WorkspaceItem made from the ExternalDataObject that will be created from the given
     * uri. The Collection for the WorkspaceItem will be retrieved through the request.
     *
     * @param context   The relevant DSpace context
     * @param uri       The uri that contains the data for the ExternalDataObject
     * @return          A WorkspaceItem created from the given information
     * @throws SQLException         If something goes wrong
     * @throws AuthorizeException   If something goes wrong
     */
    private WorkspaceItem createWorkspaceItem(Context context, String collectionId, String uri)
            throws SQLException, AuthorizeException {

        try {
            ExternalDataObject dataObject = getExternalDataObjectFromUriList(uri);
            dataObject.addMetadata(new MetadataValueDTO("cris", "source", "name",
                                                        source));
            Collection collection = collectionService.find(context, UUID.fromString(collectionId));
            return externalDataService.createWorkspaceItemFromExternalDataObject(context, dataObject, collection);
        } catch (AuthorizeException | SQLException e) {
            LOGGER.error("An error occured when trying to create item in collection with uuid: " + collectionId,
                    e);
            throw e;
        } catch (ResourceNotFoundException e) {
            LOGGER.error(e.getMessage());
            throw e;
        }
    }

    /**
     * This method will take uri string and it'll perform regex logic
     * to get the AuthorityName and ID parameters from it to then retrieve
     * an ExternalDataObject from the service
     * @param uri   The Uri string to be parsed
     * @return The appropriate ExternalDataObject
     */
    private ExternalDataObject getExternalDataObjectFromUriList(String uri) {
        Pattern pattern = Pattern.compile("api\\/integration\\/externalsources\\/(.*)\\/entryValues\\/(.*)");
        Matcher matcher = pattern.matcher(uri);

        matcher.find();
        String externalSourceIdentifer = matcher.group(1);
        String id = matcher.group(2);

        Optional<ExternalDataObject> externalDataObject = externalDataService
                .getExternalDataObject(externalSourceIdentifer, id);

        sleepForExternalData();

        return externalDataObject.orElseThrow(() -> new ResourceNotFoundException(
                "Couldn't find an ExternalSource for source: " + externalSourceIdentifer + " and ID: " + id));
    }
    private void sleepForExternalData() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(0, 501));
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}