/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.dspace.app.rest.utils.ContextUtil.obtainContext;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;

import java.sql.SQLException;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dspace.app.deduplication.utils.DedupUtils;
import org.dspace.app.deduplication.utils.DuplicateInfo;
import org.dspace.app.rest.model.DeduplicationSetRest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.discovery.SearchServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RestController to delete items from a specific set
 * This is handled by calling "/api/deduplications/sets/{id}/items/{uuid}" with the correct RequestMethod
 * 
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */
@RestController
@RequestMapping("/api/" + DeduplicationSetRest.CATEGORY + "/" + DeduplicationSetRest.PLURAL_NAME)
public class DeduplicationSetRestController {

    @Autowired
    private DedupUtils dedupUtils;

    @Autowired
    ItemService itemService;

    /**
     * Method to delete an item from a set.
     *
     * @param id the id of the set
     * @param uuid the uuid of the item
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = DELETE, path = "/{id}/items/{uuid}")
    public void deleteItem(@PathVariable String id, @PathVariable UUID uuid,
        HttpServletResponse response, HttpServletRequest request)
        throws SearchServiceException, SQLException, AuthorizeException {
        Context context = obtainContext(request);
        DuplicateInfo duplicateInfo = dedupUtils.findGroup(context, id);
        if (duplicateInfo == null) {
            throw new ResourceNotFoundException("Could not find set with id: " + id);
        }
        Item item = itemService.find(context, uuid);
        if (item == null) {
            throw new ResourceNotFoundException("Could not find item with id " + uuid);
        }

        dedupUtils.rejectAdminDups(context, duplicateInfo, uuid, Constants.ITEM);

        context.complete();
        response.setStatus(SC_NO_CONTENT);
    }
}
