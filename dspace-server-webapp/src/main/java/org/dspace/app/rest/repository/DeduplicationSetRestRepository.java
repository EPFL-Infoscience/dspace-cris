/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;

import org.dspace.app.deduplication.utils.DedupUtils;
import org.dspace.app.deduplication.utils.DuplicateInfo;
import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.model.DeduplicationSetRest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.discovery.SearchServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * This is the repository responsible to manage DeduplicationSet Rest object
 *
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */

@Component(DeduplicationSetRest.CATEGORY + "." + DeduplicationSetRest.NAME)
public class DeduplicationSetRestRepository extends DSpaceRestRepository<DeduplicationSetRest, String> {

    @Autowired
    private DedupUtils dedupUtils;

    @Override
    public Class<DeduplicationSetRest> getDomainClass() {
        return DeduplicationSetRest.class;
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Override
    public Page<DeduplicationSetRest> findAll(Context context, Pageable pageable) {
        try {
            return converter.toRestPage(utils.getPage(dedupUtils.findAllGroups(context),
                pageable), utils.obtainProjection());
        } catch (SearchServiceException | SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Override
    public DeduplicationSetRest findOne(Context context, String id) {
        try {
            DuplicateInfo duplicateInfo = dedupUtils.findGroup(context, id);
            if (duplicateInfo == null) {
                return null;
            }
            return converter.toRest(duplicateInfo, utils.obtainProjection());
        } catch (SearchServiceException | SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @SearchRestMethod(name = "findBySignature")
    public Page<DeduplicationSetRest> findBySignature(
            @Parameter(value = "signature-id", required = true) String signatureId, Pageable pageable) {
        try {
            Context context = obtainContext();
            return converter.toRestPage(utils.getPage(dedupUtils.findAllGroups(context, signatureId),
                    pageable), utils.obtainProjection());
        } catch (SQLException | SearchServiceException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @SearchRestMethod(name = "findBySignatureAndRule")
    public Page<DeduplicationSetRest> findBySignatureAndRule(
            @Parameter(value = "signature-id", required = true) String signatureId,
            @Parameter(value = "rule", required = true) String rule, Pageable pageable) {
        try {
            Context context = obtainContext();
            return converter.toRestPage(utils.getPage(dedupUtils.findAllGroups(context, signatureId, rule),
                    pageable), utils.obtainProjection());
        } catch (SQLException | SearchServiceException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Override
    protected void delete(Context context, String id) throws AuthorizeException {
        DuplicateInfo duplicateInfo = null;
        try {
            duplicateInfo = dedupUtils.findGroup(context, id);
            if (duplicateInfo == null) {
                throw new ResourceNotFoundException("Could not find set with id: " + id);
            }
        } catch (SearchServiceException | SQLException e) {
            throw new RuntimeException("Could not find set with id: " + id, e);
        }
        try {
            dedupUtils.rejectAdminDups(context, duplicateInfo, Constants.ITEM);
        } catch (SQLException | SearchServiceException e) {
            throw new RuntimeException("Something went wrong trying to delete set with id: " + id, e);
        }
    }
}
