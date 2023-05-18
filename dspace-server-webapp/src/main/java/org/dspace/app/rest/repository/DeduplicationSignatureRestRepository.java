/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import org.dspace.app.deduplication.utils.DedupUtils;
import org.dspace.app.deduplication.utils.DeduplicationSignature;
import org.dspace.app.rest.model.DeduplicationSignatureRest;
import org.dspace.core.Context;
import org.dspace.discovery.SearchServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * This is the repository responsible to manage DeduplicationSignature Rest object
 *
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */

@Component(DeduplicationSignatureRest.CATEGORY + "." + DeduplicationSignatureRest.NAME)
public class DeduplicationSignatureRestRepository extends DSpaceRestRepository<DeduplicationSignatureRest, String> {

    @Autowired
    private DedupUtils dedupUtils;

    @Override
    public Class<DeduplicationSignatureRest> getDomainClass() {
        return DeduplicationSignatureRest.class;
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Override
    public Page<DeduplicationSignatureRest> findAll(Context context, Pageable pageable) {
        try {
            return converter.toRestPage(dedupUtils.findAllSignatures(),
                pageable, utils.obtainProjection());
        } catch (SearchServiceException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Override
    public DeduplicationSignatureRest findOne(Context context, String id) {
        try {
            DeduplicationSignature deduplicationSignature = dedupUtils.findSignature(id);
            if (deduplicationSignature == null) {
                return null;
            }
            return converter.toRest(deduplicationSignature, utils.obtainProjection());
        } catch (SearchServiceException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
