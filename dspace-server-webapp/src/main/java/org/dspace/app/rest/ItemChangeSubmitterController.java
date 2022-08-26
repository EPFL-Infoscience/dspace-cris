/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.sql.SQLException;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dspace.app.rest.model.WorkspaceItemRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.submit.service.ChangeSubmitterService;
import org.dspace.workflow.WorkflowItem;
import org.dspace.workflow.WorkflowItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * This RestController allows curators to change an item's submitter, given the
 * item ID and the submitter's identifier
 */
@RestController
@RequestMapping("/api/" + WorkspaceItemRest.CATEGORY + "/" + WorkspaceItemRest.NAME + "/changesubmitter")
public class ItemChangeSubmitterController {

    @Autowired
    ItemService itemService;

    @Autowired
    AuthorizeService authorizeService;

    @Autowired
    WorkspaceItemService workspaceItemService;

    @Autowired
    WorkflowItemService<?> workflowItemService;

    @Autowired
    ChangeSubmitterService changeSubmitterService;

    /**
     * Set the new submitter if allowed (must be a curator)
     * 
     * @param request
     * @param response
     * @param itemID
     * @param submitterIdentifier
     * @throws SQLException
     * @throws AuthorizeException
     */
    @PostMapping
    public void postChangeSubmitter(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("itemId") UUID itemID, @RequestParam("submitterIdentifier") String submitterIdentifier)
            throws SQLException, AuthorizeException {

        Context context = ContextUtil.obtainContext(request);
        if (context.getCurrentUser() == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Item item = itemService.find(context, itemID);
        if (item == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Collection collection = item.getOwningCollection();
        if (collection == null) {
            WorkspaceItem wsItem = workspaceItemService.findByItem(context, item);
            WorkflowItem wfItem = workflowItemService.findByItem(context, item);
            if (wsItem == null) {
                collection = wfItem.getCollection();
            } else {
                collection = wsItem.getCollection();
            }
        }

        // returns unauthorized if the collection is null or if the user is not the
        // collection admin
        if (!authorizeService.isAdmin(context, collection)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        changeSubmitterService.setUpSubmitter(context, item, submitterIdentifier);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
