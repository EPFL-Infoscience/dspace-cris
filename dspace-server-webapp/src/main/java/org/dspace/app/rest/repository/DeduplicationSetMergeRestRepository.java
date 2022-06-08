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
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.app.deduplication.model.DeduplicationSetMerge;
import org.dspace.app.deduplication.utils.DedupUtils;
import org.dspace.app.deduplication.utils.DuplicateInfo;
import org.dspace.app.rest.converter.DeduplicationSetMergeConverter;
import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.DeduplicationSetMergeRest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.deduplication.dto.DeduplicationSetMergeDTO;
import org.dspace.deduplication.service.DeduplicationSetMergeService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.util.UUIDUtils;
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
    private ItemService itemService;

    @Autowired
    BitstreamService bitstreamService;

    @Autowired
    private DedupUtils dedupUtils;

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
        } catch (SearchServiceException e) {
            throw new RuntimeException(e);
        }
    }

    private void validate(Context context, UUID targetUUID, DeduplicationSetMergeDTO deduplicationSetMergeDTO)
        throws SQLException, SearchServiceException {

        DuplicateInfo duplicateInfo = dedupUtils.findGroup(context, deduplicationSetMergeDTO.getSetId());
        if (duplicateInfo == null) {
            throw new UnprocessableEntityException(
                "Could not find set with id: " + deduplicationSetMergeDTO.getSetId());
        }

        if (itemService.find(context, targetUUID) == null) {
            throw new ResourceNotFoundException("Target Item with uuid " + targetUUID + " not found");
        }

        for (String itemUri : deduplicationSetMergeDTO.getMergedItems()) {
            if (itemService.find(context, getUUIDFromUri(itemUri)) == null) {
                throw new UnprocessableEntityException("item for uuid " + getUUIDFromUri(itemUri) + "doesn't exist");
            }
        }

        for (String bitstreamUri : deduplicationSetMergeDTO.getBitstreams()) {
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

}
