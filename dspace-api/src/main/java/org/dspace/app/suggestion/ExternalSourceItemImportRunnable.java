/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.suggestion;

import static org.apache.commons.collections.CollectionUtils.isEmpty;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.external.model.ExternalDataObject;
import org.dspace.external.service.ExternalDataService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;
import org.dspace.workflow.WorkflowException;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalSourceItemImportRunnable.class);

    private SolrSuggestionStorageService solrSuggestionStorageService;
    private ExternalDataService externalDataService;
    private CollectionService collectionService;
    private WorkflowService workflowService;

    private Context context;
    private String source;
    private String score;
    private String collectionId;
    private String email;

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

        source = commandLine.getOptionValue("p");
        score = commandLine.getOptionValue("s");
        collectionId = commandLine.getOptionValue("u");
        email = commandLine.getOptionValue("e");

    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        context.setCurrentUser(findEPerson());

        if (source == null || score == null || collectionId == null) {
            throw new NullPointerException("provider -p option and score -s option " +
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
        String email = commandLine.getOptionValue('e');
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
        int idx = 0;
        suggestions = findAllUnprocessedSuggestionsBySource(context, source, idx);
        while (!isEmpty(suggestions) && idx <= 1000) {
            suggestions = filterSuggestionsByScore(suggestions, Double.parseDouble(score));
            for (Suggestion suggestion : suggestions) {
                try {
                    WorkspaceItem workspaceItem = createWorkspaceItem(context, collectionId,
                        suggestion.getExternalSourceUri());
                    workflowService.start(context, workspaceItem);
                    solrSuggestionStorageService.flagSuggestionAsProcessed(suggestion);
                } catch (AuthorizeException | IOException | WorkflowException | SolrServerException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
            idx += 10;
            suggestions = findAllUnprocessedSuggestionsBySource(context, source, idx);
        }
    }

    public List<Suggestion> findAllUnprocessedSuggestionsBySource(Context context, String source, long offset) {
        try {
            return solrSuggestionStorageService.findAllUnprocessedSuggestionsBySource(context, source,
                    10, offset, true);
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Suggestion> filterSuggestionsByScore(List<Suggestion> suggestions, double score) {
        return suggestions.stream()
                .filter(s -> s.getScore() >= score)
                .collect(Collectors.toList());
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
        ExternalDataObject dataObject = getExternalDataObjectFromUriList(uri);

        try {
            Collection collection = collectionService.find(context, UUID.fromString(collectionId));
            return externalDataService.createWorkspaceItemFromExternalDataObject(context, dataObject, collection);
        } catch (AuthorizeException | SQLException e) {
            LOGGER.error("An error occured when trying to create item in collection with uuid: " + collectionId,
                    e);
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
        return externalDataObject.orElseThrow(() -> new ResourceNotFoundException(
                "Couldn't find an ExternalSource for source: " + externalSourceIdentifer + " and ID: " + id));
    }

}