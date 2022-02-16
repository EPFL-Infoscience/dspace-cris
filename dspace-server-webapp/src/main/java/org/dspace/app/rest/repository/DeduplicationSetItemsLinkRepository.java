/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.List;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import org.dspace.app.deduplication.utils.DedupUtils;
import org.dspace.app.deduplication.utils.DuplicateInfo;
import org.dspace.app.rest.model.DeduplicationSetRest;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.discovery.SearchServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * LinkRepository for "items" subresource of an individual set.
 * 
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */
@Component(DeduplicationSetRest.CATEGORY + "." + DeduplicationSetRest.NAME + "." + DeduplicationSetRest.ITEMS)
public class DeduplicationSetItemsLinkRepository extends AbstractDSpaceRestRepository implements LinkRestRepository {

    @Autowired
    private DedupUtils dedupUtils;

    @PreAuthorize("hasAuthority('ADMIN')")
    public Page<ItemRest> getItems(@Nullable HttpServletRequest request, String id,
        @Nullable Pageable optionalPageable, Projection projection) {
        try {
            Context context = obtainContext();
            DuplicateInfo duplicateInfo = dedupUtils.findGroup(context, id);
            if (duplicateInfo == null) {
                throw new ResourceNotFoundException("No such set: " + id);
            }
            List<Item> items = duplicateInfo.getItems();
            Pageable pageable = utils.getPageable(optionalPageable);
            return converter.toRestPage(items, pageable, utils.obtainProjection());
        } catch (SQLException | SearchServiceException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
