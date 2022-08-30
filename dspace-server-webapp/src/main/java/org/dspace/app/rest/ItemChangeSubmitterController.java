/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dspace.app.rest.model.WorkspaceItemRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.submit.service.ChangeSubmitterService;
import org.dspace.workflow.WorkflowItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.TemplateVariable;
import org.springframework.hateoas.TemplateVariable.VariableType;
import org.springframework.hateoas.TemplateVariables;
import org.springframework.hateoas.UriTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * This RestController allows curators to change an item's submitter, given the
 * item ID and the submitter's identifier
 */
@RestController
@RequestMapping("/api/" + WorkspaceItemRest.CATEGORY + "/" + WorkspaceItemRest.NAME)
public class ItemChangeSubmitterController {

    public static final String ACTION = "changesubmitter";
    public static final String ITEM_PARAM = "itemId";
    public static final String SUB_PARAM = "submitterIdentifier";

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

    @Autowired
    DiscoverableEndpointsService discoverableEndpointsService;

    @PostConstruct
    public void afterPropertiesSet() {
        discoverableEndpointsService
                .register(this,
                        Arrays.asList(Link.of(
                                UriTemplate.of("/api/" + WorkspaceItemRest.CATEGORY + "/" + WorkspaceItemRest.NAME,
                                        new TemplateVariables(
                                                new TemplateVariable(ITEM_PARAM, VariableType.REQUEST_PARAM),
                                                new TemplateVariable(SUB_PARAM, VariableType.REQUEST_PARAM))),
                                ACTION)));
    }

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
    @RequestMapping(method = RequestMethod.POST, value = ACTION)
    public void postChangeSubmitter(HttpServletRequest request, HttpServletResponse response,
            @RequestParam(ITEM_PARAM) UUID itemID, @RequestParam(SUB_PARAM) String submitterIdentifier)
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

        Collection collection = (Collection) itemService.getParentObject(context, item);

        // returns unauthorized if the collection is null or if the user is not the
        // collection admin
        if (!authorizeService.isAdmin(context, collection)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        changeSubmitterService.setUpSubmitter(context, item, submitterIdentifier);
        context.complete();
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
