/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.app.deduplication.model.DeduplicationMergeTarget;
import org.dspace.app.deduplication.model.DeduplicationSetMerge;
import org.dspace.app.deduplication.utils.DedupUtils;
import org.dspace.app.deduplication.utils.DuplicateInfo;
import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.converter.DeduplicationMergeTargetConverter;
import org.dspace.app.rest.converter.DeduplicationSetMergeConverter;
import org.dspace.app.rest.converter.ItemConverter;
import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.DeduplicationMergeTargetRest;
import org.dspace.app.rest.model.DeduplicationSetMergeRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.app.util.DCInput;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.deduplication.dto.DeduplicationMetadataDTO;
import org.dspace.deduplication.dto.DeduplicationMetadataSourcesDTO;
import org.dspace.deduplication.dto.DeduplicationSetMergeDTO;
import org.dspace.deduplication.service.DeduplicationSetMergeService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.util.UUIDUtils;
import org.dspace.workflow.WorkflowItem;
import org.dspace.workflow.WorkflowItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * This is the repository responsible for merging Items.
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 *
 */
@Component(DeduplicationSetMergeRest.CATEGORY + "." + DeduplicationSetMergeRest.NAME)
public class DeduplicationSetMergeRestRepository
    extends DSpaceRestRepository<DeduplicationSetMergeRest, UUID> {

    @Autowired
    DeduplicationSetMergeService deduplicationSetMergeService;

    @Autowired
    DeduplicationSetMergeConverter converter;

    @Autowired
    DeduplicationMergeTargetConverter targetConverter;

    @Autowired
    ItemConverter itemConverter;

    @Autowired
    private ItemService itemService;

    @Autowired
    BitstreamService bitstreamService;

    @Autowired
    private DedupUtils dedupUtils;

    @Autowired
    private WorkflowItemService workflowItemService;

    @Autowired
    private WorkspaceItemService workspaceItemService;

    protected DCInputsReader dcInputsReader;

    @PostConstruct
    private void init() throws DCInputsReaderException {
        dcInputsReader = new DCInputsReader();
    }

    @Override
    public DeduplicationSetMergeRest findOne(Context context, UUID uuid) {
        throw new RepositoryMethodNotImplementedException(DeduplicationSetMergeRest.NAME, "findOne");
    }

    @Override
    public Page<DeduplicationSetMergeRest> findAll(Context context, Pageable pageable) {
        throw new RepositoryMethodNotImplementedException(DeduplicationSetMergeRest.NAME, "findAll");
    }

    @Override
    public Class<DeduplicationSetMergeRest> getDomainClass() {
        return DeduplicationSetMergeRest.class;
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public DeduplicationSetMergeRest put(Context context, HttpServletRequest request,
                                            String apiCategory, String model, UUID uuid, JsonNode jsonNode)
        throws AuthorizeException, SQLException {
        ObjectMapper mapper = new ObjectMapper();
        DeduplicationSetMergeDTO deduplicationSetMergeDTO;
        DeduplicationSetMerge dedupSetMerge = null;
        try {
            deduplicationSetMergeDTO = mapper.readValue(jsonNode.toString(), DeduplicationSetMergeDTO.class);
            validate(context, uuid, deduplicationSetMergeDTO);
            dedupSetMerge = deduplicationSetMergeService.merge(context, uuid, deduplicationSetMergeDTO);
            return converter.convert(dedupSetMerge, utils.obtainProjection());
        } catch (IOException e1) {
            throw new UnprocessableEntityException("Error parsing request body", e1);
        } catch (SearchServiceException | DCInputsReaderException e) {
            throw new RuntimeException(e);
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @SearchRestMethod(name = "findTargets")
    public DeduplicationMergeTargetRest findTargets(@Parameter(value = "uuid", required = true) UUID[] uuids) {

        Context context = obtainContext();

        List<String> allowedTargets = Arrays.stream(uuids)
                                            .map(uuid -> findItem(context, uuid))
                                            .filter(item -> item != null && isAllowedTarget(context, item))
                                            .map(item -> convertItemToUri(itemConverter, item))
                                            .collect(Collectors.toList());

        if (allowedTargets.isEmpty()) {
            throw new ResourceNotFoundException("none of provided items is eligible as target");
        }

        return targetConverter.convert(new DeduplicationMergeTarget(allowedTargets), utils.obtainProjection());
    }

    private void validate(Context context, UUID targetUUID, DeduplicationSetMergeDTO deduplicationSetMergeDTO)
        throws SQLException, SearchServiceException, DCInputsReaderException {

        validateDeduplicationSet(context, deduplicationSetMergeDTO.getSetId());
        validateTargetItem(context, targetUUID);
        validateMergedItems(context, deduplicationSetMergeDTO.getMergedItems());
        validateMetadata(context, targetUUID, deduplicationSetMergeDTO.getMetadata());
        validateBitstreams(context, deduplicationSetMergeDTO.getBitstreams());

    }

    private void validateDeduplicationSet(Context context, String setId) throws SearchServiceException, SQLException {
        DuplicateInfo duplicateInfo = dedupUtils.findGroup(context, setId);

        if (duplicateInfo == null) {
            throw new UnprocessableEntityException(
                "Could not find set with id: " + setId);
        }
    }

    private void validateMergedItems(Context context, List<String> mergedItems) throws SQLException {
        for (String itemUri : mergedItems) {
            if (itemService.find(context, getUUIDFromUri(itemUri)) == null) {
                throw new UnprocessableEntityException("item for uuid " + getUUIDFromUri(itemUri) + "doesn't exist");
            }
        }
    }

    private void validateTargetItem(Context context, UUID targetUUID) throws SQLException {
        Item item = itemService.find(context, targetUUID);

        if (item == null) {
            throw new ResourceNotFoundException("Target Item with uuid " + targetUUID + " not found");
        }
    }
    private void validateMetadata(Context context, UUID targetUUID, List<DeduplicationMetadataDTO> metadataList)
        throws SQLException, DCInputsReaderException {

        Item item = itemService.find(context, targetUUID);
        Collection collection =  getCollectionByItem(context, item);
        List<String> metadataFields = getRepeatedMetadata(metadataList);

        if (metadataFields.size() > 0) {
            checkAnyRepeatableMetadata(collection , metadataFields);
        }
    }

    private Collection getCollectionByItem(Context context, Item item) throws SQLException {
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

    private List<String> getRepeatedMetadata(List<DeduplicationMetadataDTO> metadataList) {
        List<String> metadataFields = new ArrayList<>();

        for (DeduplicationMetadataDTO metadataDTO : metadataList) {
            List<DeduplicationMetadataSourcesDTO> sources = metadataDTO.getSources();
            if (sources != null && sources.size() > 1) {
                metadataFields.add(metadataDTO.getMetadataField());
            }
        }
        return metadataFields;
    }

    private void checkAnyRepeatableMetadata(Collection collection, List<String> metadataFields)
        throws DCInputsReaderException {
        List<DCInputSet> dcInputSets = dcInputsReader.getInputsByCollection(collection);
        for (DCInputSet dcInputSet : dcInputSets) {
            for (String field : metadataFields) {
                Optional<DCInput> dcInput = dcInputSet.getField(field);
                if (dcInput.isPresent()) {
                    if (!dcInput.get().isRepeatable()) {
                        throw new UnprocessableEntityException("the metadata " + field + " isn't repeatable");
                    }
                }
            }
        }
    }

    private void validateBitstreams(Context context, List<String> bitstreams) throws SQLException {
        for (String bitstreamUri : bitstreams) {
            if (bitstreamService.find(context, getUUIDFromUri(bitstreamUri)) == null) {
                throw new UnprocessableEntityException(
                    "Bitstream for uuid " + getUUIDFromUri(bitstreamUri) + "doesn't exist");
            }
        }
    }

    private UUID getUUIDFromUri(String uri) {
        String path = URI.create(uri).getPath();
        return UUIDUtils.fromString(path.substring(path.lastIndexOf("/") + 1));
    }

    private Item findItem(Context context, UUID uuid) {
        try {
            return itemService.find(context, uuid);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private boolean isAllowedTarget(Context context, Item item) {
        return item.isArchived() && !isItemInWorkspace(context, item) && !isItemInWorkflow(context, item);
    }

    private boolean isItemInWorkspace(Context context, Item item) {
        try {
            return workspaceItemService.findByItem(context, item) != null;
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private boolean isItemInWorkflow(Context context, Item item) {
        try {
            return workflowItemService.findByItem(context, item) != null;
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String convertItemToUri(ItemConverter converter, Item item) {
        return utils.linkToSingleResource(converter.convert(item, Projection.DEFAULT), "self").getHref();
    }
}
