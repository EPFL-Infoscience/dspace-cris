/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.PostConstruct;

import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.model.SubmissionRepeatableFieldsRest;
import org.dspace.app.rest.model.wrapper.SubmissionRepeatableFields;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.workflow.WorkflowItem;
import org.dspace.workflow.WorkflowItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * This is the repository responsible to manage Submission Repeatable Fields Rest object
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
@Component(SubmissionRepeatableFieldsRest.CATEGORY + "." + SubmissionRepeatableFieldsRest.NAME)
public class SubmissionRepeatableFieldsRestRepository
    extends DSpaceRestRepository<SubmissionRepeatableFieldsRest, UUID> {

    @Autowired
    private WorkspaceItemService workspaceItemService;

    @Autowired
    private WorkflowItemService workflowItemService;

    @Autowired
    private ItemService itemService;

    @Autowired
    protected ConverterService converter;

    private DCInputsReader dcInputsReader;

    @PostConstruct
    private void setup() throws DCInputsReaderException {
        this.dcInputsReader = new DCInputsReader();
    }

    /**
     * The findOne method is not supported in this repository
     */
    @PreAuthorize("permitAll()")
    @Override
    public SubmissionRepeatableFieldsRest findOne(Context context, UUID uuid) {
        throw new RepositoryMethodNotImplementedException(SubmissionRepeatableFieldsRest.NAME, "findOne");
    }

    /**
     * The findAll method is not supported in this repository
     */
    @Override
    public Page<SubmissionRepeatableFieldsRest> findAll(Context context, Pageable pageable) {
        throw new RepositoryMethodNotImplementedException(SubmissionRepeatableFieldsRest.NAME, "findAll");
    }

    @Override
    public Class<SubmissionRepeatableFieldsRest> getDomainClass() {
        return SubmissionRepeatableFieldsRest.class;
    }

    @SearchRestMethod(name = "findByItem")
    public SubmissionRepeatableFieldsRest findByItem(@Parameter(value = "uuid", required = true) UUID uuid)
        throws SQLException, DCInputsReaderException {

        Context context = obtainContext();

        Item item = itemService.find(context, uuid);

        if (item == null) {
            throw new ResourceNotFoundException(
                "The given uuid did not resolve to an item on the server: " + uuid);
        }

        Collection collation = findCollectionByItem(context, item);

        SubmissionRepeatableFields submissionRepeatableFields =
            new SubmissionRepeatableFields(uuid.toString(), getRepeatableFields(item, collation));

        return converter.toRest(submissionRepeatableFields, utils.obtainProjection());
    }

    private Collection findCollectionByItem(Context context, Item item) throws SQLException {

        Collection collection = null;

        WorkspaceItem workspaceItem = workspaceItemService.findByItem(context, item);
        WorkflowItem workflowItem = workflowItemService.findByItem(context, item);

        if (workspaceItem != null) {
            collection = workspaceItem.getCollection();
        } else if (workflowItem != null) {
            collection = workflowItem.getCollection();
        } else if (item.isArchived()) {
            collection = item.getOwningCollection();
        }

        return collection;

    }

    private List<String> getRepeatableFields(Item item, Collection collation) throws DCInputsReaderException {
        List<String> repeatableFields = new ArrayList<>();
        List<DCInputSet> dcInputSets = dcInputsReader.getInputsByCollection(collation);

        for (String metadataField : getDistinctMetadataFields(item)) {
            for (DCInputSet dcInputSet : dcInputSets) {
                if (dcInputSet.isFieldPresent(metadataField)) {

                    if (dcInputSet.hasParent(metadataField)) {
                        repeatableFields.add(metadataField);
                    } else if (dcInputSet.getField(metadataField).get().isRepeatable()) {
                        repeatableFields.add(metadataField);
                    }

                }
            }
        }

        return repeatableFields;

    }

    private Set<String> getDistinctMetadataFields(Item item) {

        final Set<String> fields = new HashSet<>();

        item.getMetadata().stream()
            .forEach(m -> fields.add(m.getMetadataField().toString('.')));

        return fields;
    }

}
