/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import org.dspace.app.dataquality.utils.service.AbstractDedupUtilsAddon;
import org.dspace.app.deduplication.utils.DuplicateInfo;
import org.dspace.app.rest.model.DeduplicationSetRest;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.core.Context;
import org.dspace.discovery.SearchServiceException;
import org.dspace.services.ConfigurationService;
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

    protected static Predicate<MetadataField> isNotVirtualField =
        (mf) -> !mf.toString('.').contains("virtual");
    protected static Predicate<MetadataField> fieldNotInSet(Set<String> excludedMetadata) {
        return mf -> excludedMetadata != null && !excludedMetadata.contains(mf.toString('.'));
    }

    @Autowired
    private AbstractDedupUtilsAddon dedupUtilsAddon;

    @Autowired
    private ConfigurationService configurationService;

    @PreAuthorize("hasAuthority('ADMIN')")
    public Page<ItemRest> getItems(@Nullable HttpServletRequest request, String id,
                                   @Nullable Pageable optionalPageable, Projection projection) {
        try {
            Context context = obtainContext();
            DuplicateInfo duplicateInfo = dedupUtilsAddon.findGroup(context, id);
            if (duplicateInfo == null) {
                throw new ResourceNotFoundException("No such set: " + id);
            }
            return converter.toRestPage(
                filterItemsMetadata(
                    duplicateInfo.getItems(),
                    new HashSet<>(
                        Arrays.asList(this.configurationService.getArrayProperty("merge.excluded-metadata"))
                    )
                ),
                utils.getPageable(optionalPageable),
                projection
            );
        } catch (SQLException | SearchServiceException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    protected List<Item> filterItemsMetadata(List<Item> items, Set<String> excludedMetadata) {
        return items
            .stream()
            .map(item -> filterItemMetadata(item, excludedMetadata))
            .collect(Collectors.toList());
    }

    protected Item filterItemMetadata(Item item, Set<String> excludedMetadata) {
        item.setMetadata(
            item.getMetadata()
                .stream()
                .filter(metadataValue ->
                            isNotVirtualField
                                .and(fieldNotInSet(excludedMetadata))
                                .test(metadataValue.getMetadataField())
                )
                .collect(Collectors.toList())
        );
        return item;
    }
}