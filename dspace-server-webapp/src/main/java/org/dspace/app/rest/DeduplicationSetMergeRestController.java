/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import java.sql.SQLException;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.DeduplicationSetMergeRest;
import org.dspace.app.rest.model.hateoas.DeduplicationSetMergeResource;
import org.dspace.app.rest.repository.DeduplicationSetMergeRestRepository;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ControllerUtils;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RestController to merge items from a specific set
 * This is handled by calling "/api/deduplications/merge/{id}" with the correct RequestMethod
 * 
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
@RestController
@RequestMapping("/api/" + DeduplicationSetMergeRest.CATEGORY + "/" + DeduplicationSetMergeRest.PLURAL_NAME)
public class DeduplicationSetMergeRestController {

    @Autowired
    DeduplicationSetMergeRestRepository deduplicationSetMergeRestRepository;

    @Autowired
    private ConverterService converterService;

    /**
     * Method to merge the items from a set.
     *
     * @param uuid the uuid of the target item
     * @param jsonNode the request body
     * @param response The response object
     * @param request  The request object
     */
    @RequestMapping(method = PUT, path = "/{uuid}")
    public ResponseEntity<RepresentationModel<?>> mergeItems(@PathVariable UUID uuid,
                                                             @RequestBody JsonNode jsonNode,
                                                             HttpServletResponse response,
                                                             HttpServletRequest request) {

        DeduplicationSetMergeRest deduplicationSetMergeRest;
        Context context = obtainContext(request);
        try {
            deduplicationSetMergeRest = deduplicationSetMergeRestRepository.put(context, request,
                DeduplicationSetMergeRest.CATEGORY, DeduplicationSetMergeRest.PLURAL_NAME, uuid, jsonNode);

            context.commit();
        } catch (SQLException e) {
            throw new RuntimeException(
                "Unable to update DSpace object " + DeduplicationSetMergeRest.PLURAL_NAME + " with id=" + uuid, e);
        } catch (AuthorizeException e) {
            throw new RuntimeException(
                "Unable to perform PUT request as the current user does not have sufficient rights", e);
        }
        DeduplicationSetMergeResource resource = converterService.toResource(deduplicationSetMergeRest);
        return ControllerUtils.toResponseEntity(HttpStatus.OK, new HttpHeaders(), resource);
    }
}
