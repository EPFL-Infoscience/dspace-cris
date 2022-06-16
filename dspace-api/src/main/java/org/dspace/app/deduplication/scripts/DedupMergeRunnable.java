/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.deduplication.scripts;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.dspace.app.deduplication.model.DeduplicationMerge;
import org.dspace.app.util.DCInput;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.deduplication.factory.DeduplicationServiceFactory;
import org.dspace.deduplication.service.DeduplicationSetMergeService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;
import org.dspace.workflow.WorkflowItem;
import org.dspace.workflow.WorkflowItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link DSpaceRunnable} to perform merging of deduplication set items.
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 *
 */
public class DedupMergeRunnable extends DSpaceRunnable<DedupMergeScriptConfiguration<DedupMergeRunnable>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DedupMergeRunnable.class);

    protected ConfigurationService configurationService;

    protected DeduplicationSetMergeService deduplicationSetMergeService;

    protected WorkspaceItemService workspaceItemService;

    protected WorkflowItemService workflowItemService;

    protected DCInputsReader dcInputsReader;

    protected ItemService itemService;

    protected Context context;

    protected String targetItem;

    protected String[] mergedItems;

    protected String[] replacedNotEmptyMetadata;

    protected String[] replacedMetadata;

    protected String[] appendedMetadata;

    protected boolean delete;

    protected boolean exclude;
    protected boolean help;
    protected EPersonService ePersonService;

    @Override
    @SuppressWarnings({ "rawtypes" })
    public DedupMergeScriptConfiguration getScriptConfiguration() {
        DedupMergeScriptConfiguration configuration = new DSpace()
            .getServiceManager()
            .getServiceByName("deduplication-merge-items", DedupMergeScriptConfiguration.class);
        return configuration;
    }

    @Override
    public void setup() throws ParseException {
        DSpace dspace = new DSpace();
        configurationService = dspace.getConfigurationService();
        deduplicationSetMergeService = DeduplicationServiceFactory.getInstance().getDeduplicationSetMergeService();
        itemService = ContentServiceFactory.getInstance().getItemService();
        workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();
        workflowItemService = ContentServiceFactory.getInstance().getWorkflowItemService();
        ePersonService = EPersonServiceFactory.getInstance().getEPersonService();

        try {
            dcInputsReader = new DCInputsReader();
        } catch (DCInputsReaderException e) {
            LOGGER.error(e.getMessage(), e);
        }

        help = commandLine.hasOption("h");
        targetItem = commandLine.getOptionValue("t");
        mergedItems = commandLine.getOptionValues("m");
        replacedNotEmptyMetadata = commandLine.getOptionValues("p");
        replacedMetadata = commandLine.getOptionValues("r");
        appendedMetadata = commandLine.getOptionValues("a");
        delete = commandLine.hasOption("d");
        exclude = commandLine.hasOption("x");
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        try {
            context.turnOffAuthorisationSystem();
            validate();
            performMerging();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
            context.complete();
        }

    }

    protected void assignCurrentUserInContext() throws ParseException, SQLException {
        if (commandLine.hasOption('e')) {
            String ePersonEmail = commandLine.getOptionValue('e');
            context = new Context(Context.Mode.BATCH_EDIT);
            try {
                EPerson ePerson = ePersonService.findByEmail(this.context, ePersonEmail);
                if (ePerson == null) {
                    LOGGER.error("EPerson not found: " + ePersonEmail);
                    throw new IllegalArgumentException("Unable to find a user with email: " + ePersonEmail);
                }
                context.setCurrentUser(ePerson);
            } catch (SQLException e) {
                throw new IllegalArgumentException("SQLException trying to find user with email: " + ePersonEmail);
            }
        } else {
            UUID uuid = getEpersonIdentifier();
            if (uuid != null) {
                try {
                    EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
                    context.setCurrentUser(ePerson);
                } catch (SQLException e) {
                    LOGGER.error("Something went wrong while trying to fetch the eperson for uuid: " + uuid, e);
                }
            }
        }
    }

    private void validate() throws SQLException, DCInputsReaderException {
        validateInputs();
        if (!exclude) {
            validateMetadata();
        }
    }

    private void validateInputs() throws SQLException {
        if (!help) {
            if (targetItem == null) {
                throw new NullPointerException("-t --target option of target item must be included");
            } else {
                validateItem(targetItem);
            }

            if (mergedItems == null) {
                throw new NullPointerException("-m --merge option of merged items must be included");
            } else {
                for (String item : mergedItems) {
                    validateItem(item);
                }
            }

            if (replacedNotEmptyMetadata == null && appendedMetadata == null && replacedMetadata == null && !exclude) {
                throw new NullPointerException(
                    "-r --replace or -p --replace_notempty or -a --append options can't be null " +
                        "in case of -x --exclude option is null");
            }
        }
    }

    private void validateItem(String item) throws SQLException {
        Item item1 = itemService.find(context, UUID.fromString(item));
        if (item1 == null) {
            throw new IllegalArgumentException("Item not found for id = " + item);
        }
    }

    private void validateMetadata() throws SQLException, DCInputsReaderException {
        if (isDuplicatedMetadata()) {
            throw new IllegalArgumentException("duplicated metadata");
        }

        List<String> metadataFields;
        if (isMultipleMergedItems()) {
            metadataFields = getMetadataFields();
        } else {
            metadataFields = getAppendedMetadataFields();
        }
        checkAnyRepeatableMetadata(getCollection(), metadataFields);
    }

    private boolean isDuplicatedMetadata() {
        List<String> allMetadataList = new ArrayList<>();
        Set<String> allMetadataSet = new HashSet<>();
        if (replacedNotEmptyMetadata != null) {
            allMetadataList.addAll(Arrays.asList(replacedNotEmptyMetadata));
        }
        if (replacedMetadata != null) {
            allMetadataList.addAll(Arrays.asList(replacedMetadata));
        }
        if (appendedMetadata != null) {
            allMetadataList.addAll(Arrays.asList(appendedMetadata));
        }

        allMetadataSet.addAll(allMetadataList);

        return allMetadataList.size() != allMetadataSet.size();
    }

    private boolean isMultipleMergedItems() {
        return mergedItems.length > 1 ;
    }

    private Collection getCollection() throws SQLException {
        Item item = itemService.find(context, UUID.fromString(targetItem));
        Collection collection = item.getOwningCollection();
        if (collection == null) {
            WorkspaceItem workspaceItem = workspaceItemService.findByItem(context, item);
            WorkflowItem workflowItem = workflowItemService.findByItem(context, item);
            if (workspaceItem != null) {
                collection = workspaceItem.getCollection();
            } else if (workflowItem != null) {
                collection = workflowItem.getCollection();
            }

        }
        return collection;
    }

    private List<String> getMetadataFields() {
        List<String> allMetadataList = new ArrayList<>();
        if (replacedNotEmptyMetadata != null) {
            allMetadataList.addAll(Arrays.asList(replacedNotEmptyMetadata));
        }
        if (replacedMetadata != null) {
            allMetadataList.addAll(Arrays.asList(replacedMetadata));
        }
        if (appendedMetadata != null) {
            allMetadataList.addAll(Arrays.asList(appendedMetadata));
        }
        return allMetadataList;
    }

    private List<String> getAppendedMetadataFields() {
        return appendedMetadata != null ? Arrays.asList(appendedMetadata) : new ArrayList<>();
    }

    private void checkAnyRepeatableMetadata(Collection collection, List<String> metadataFields)
        throws DCInputsReaderException {
        List<DCInputSet> dcInputSets = dcInputsReader.getInputsByCollection(collection);
        for (DCInputSet dcInputSet : dcInputSets) {
            for (String field : metadataFields) {
                Optional<DCInput> dcInput = dcInputSet.getField(field);
                if (dcInput.isPresent()) {
                    if (!dcInput.get().isRepeatable()) {
                        throw new IllegalArgumentException("the metadata " + field + " isn't repeatable");
                    }
                }
            }
        }
    }

    private void performMerging() throws SearchServiceException, SQLException, AuthorizeException, IOException {
        DeduplicationMerge deduplicationMerge = buildDeduplicationMerge();
        handler.logInfo("start merging items");
        deduplicationSetMergeService.merge(context, deduplicationMerge);
        handler.logInfo("finished merging items");
    }

    private DeduplicationMerge buildDeduplicationMerge() {
        return new DeduplicationMerge(targetItem, Arrays.asList(mergedItems),
            Arrays.asList(Optional.ofNullable(replacedNotEmptyMetadata).orElse(new String[0])),
            Arrays.asList(Optional.ofNullable(replacedMetadata).orElse(new String[0])),
            Arrays.asList(Optional.ofNullable(appendedMetadata).orElse(new String[0])), delete, exclude);
    }

}
