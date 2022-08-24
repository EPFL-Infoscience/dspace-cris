/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.link.process;

import java.util.LinkedList;
import java.util.Map;

import org.dspace.app.rest.RestResourceController;
import org.dspace.app.rest.link.HalLinkFactory;
import org.dspace.app.rest.model.DeduplicationMergeTargetRest;
import org.dspace.app.rest.model.hateoas.DeduplicationMergeTargetResource;
import org.dspace.services.RequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * This class will provide the DeduplicationMergeTargetResource with links
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
@Component
public class DeduplicationMergeTargetResourceHalLinkFactory
        extends HalLinkFactory<DeduplicationMergeTargetResource, RestResourceController> {

    @Autowired
    RequestService requestService;

    /**
     * Add a self link based on the search parameters
     *
     * @param halResource - The halResource
     * @param pageable    - The page information
     * @param list        - The list of present links
     * @throws Exception
     */
    @Override
    protected void addLinks(DeduplicationMergeTargetResource halResource, final Pageable pageable,
                            LinkedList<Link> list)
            throws Exception {

        halResource.removeLinks();
        Map<String, String[]> parameterMap = requestService.getCurrentRequest().getHttpServletRequest()
                                                           .getParameterMap();


        UriComponentsBuilder uriComponentsBuilder = uriBuilder(getMethodOn().executeSearchMethods(
            DeduplicationMergeTargetRest.CATEGORY, DeduplicationMergeTargetRest.PLURAL_NAME,
            "findTargets", null, null, null, null, new LinkedMultiValueMap<>()));
        for (String key : parameterMap.keySet()) {
            uriComponentsBuilder.queryParam(key, parameterMap.get(key));
        }

        list.add(buildLink("self", uriComponentsBuilder.build().toUriString()));
    }


    @Override
    protected Class<RestResourceController> getControllerClass() {
        return RestResourceController.class;
    }

    @Override
    protected Class<DeduplicationMergeTargetResource> getResourceClass() {
        return DeduplicationMergeTargetResource.class;
    }
}
