/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.rest;

import static org.apache.commons.lang.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.model.SearchResultsRest;
import org.dspace.app.rest.parameter.SearchFilter;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.RestDiscoverQueryBuilder;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.crosswalk.StreamDisseminationCrosswalk;
import org.dspace.content.integration.crosswalks.StreamDisseminationCrosswalkMapper;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.Context.Mode;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResultItemIterator;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.configuration.DiscoveryConfiguration;
import org.dspace.discovery.configuration.DiscoveryConfigurationService;
import org.dspace.discovery.configuration.DiscoveryRelatedItemConfiguration;
import org.dspace.discovery.indexobject.IndexableClaimedTask;
import org.dspace.discovery.indexobject.IndexableCollection;
import org.dspace.discovery.indexobject.IndexableCommunity;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.discovery.indexobject.IndexablePoolTask;
import org.dspace.discovery.indexobject.IndexableWorkflowItem;
import org.dspace.discovery.indexobject.IndexableWorkspaceItem;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.GroupService;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * This controller perform a query as if it were performed on DSpace search page, accepting same parameters, and returns
 * search results (only publications in this implementation) in their marc xml representation.
 */
@RestController
@RequestMapping("/api/" + SearchResultsRest.CATEGORY)
public class DiscoveryExportController {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(DiscoveryExportController.class);

    @Autowired
    private CollectionService collectionService;
    @Autowired
    private CommunityService communityService;
    @Autowired
    private ItemService itemService;
    @Autowired
    private ConfigurationService configurationService;
    @Autowired
    private AuthorizeService authorizeService;
    @Autowired
    private DiscoveryConfigurationService discoveryConfigurationService;
    @Autowired
    private RestDiscoverQueryBuilder restDiscoverQueryBuilder;
    @Autowired
    private GroupService groupService;

    private Semaphore lowUsageSemaphore;
    private Semaphore highUsageSemaphore;
    private Semaphore highUsageAuthenticatedSemaphore;
    private Semaphore lowUsageAuthenticatedSemaphore;

    @Value("${discover-export.concurrent.lowUsage:10}")
    public void setLowUsageSemaphore(int lowUsageSemaphore) {
        this.lowUsageSemaphore = new Semaphore(lowUsageSemaphore);
    }

    @Value("${discover-export.concurrent.highUsage:3}")
    public void setHighUsageSemaphore(int highUsageSemaphore) {
        this.highUsageSemaphore = new Semaphore(highUsageSemaphore);
    }

    @Value("${discover-export.concurrent.lowAuthenticatedUsage:20}")
    public void setLowUsageAuthenticatedSemaphore(int lowUsageAuthenticatedSemaphore) {
        this.lowUsageAuthenticatedSemaphore = new Semaphore(lowUsageAuthenticatedSemaphore);
    }

    @Value("${discover-export.concurrent.highAuthenticatedUsage:5}")
    public void setHighUsageAuthenticatedSemaphore(int highUsageAuthenticatedSemaphore) {
        this.highUsageAuthenticatedSemaphore = new Semaphore(highUsageAuthenticatedSemaphore);
    }

    private StreamDisseminationCrosswalk streamDisseminationCrosswalk =
            new DSpace().getSingletonService(StreamDisseminationCrosswalkMapper.class)
                .getByType("epfl-publication-marc-xml");

    @GetMapping(produces = "application/xml", path = "/export")
    public void export(HttpServletRequest request, HttpServletResponse response,
                                 @RequestParam(value = "query", required = false) String query,
                                 @RequestParam(value = "scope", required = false) String scope,
                                 @RequestParam(value = "spc.sf", required = false) String sort,
                                 @RequestParam(value = "spc.sd", required = false) String sortDirection,
                                 @RequestParam(value = "configuration", required = false) String configuration,
                                 @RequestParam(value = "spc.page", required = false) Integer pageNumber,
                                 @RequestParam(value = "spc.rpp", required = false) Integer resultsPerPage,
                                 List<SearchFilter> searchFilters,
                                 Pageable page) {

        Context context = ContextUtil.obtainContext(request);
        context.setMode(Mode.READ_ONLY);
        sort = defaultIfBlank(sort, "dc.title");
        sortDirection = defaultIfBlank(sortDirection, "ASC");
        int limit = resultsPerPage != null ? resultsPerPage.intValue() : page.getPageSize();
        int p = pageNumber != null ? pageNumber.intValue() : page.getPageNumber();

        if (streamDisseminationCrosswalk == null) {
            throw new IllegalStateException("No dissemination configured for format epfl-publication-marc-xml");
        }

        boolean acquired = false;
        Semaphore semaphore;
        int limitThreshold = configurationService.getIntProperty("discover-export.limit.threshold", 100);
        if (context.getCurrentUser() != null) {
            if (limit > limitThreshold) {
                semaphore = highUsageAuthenticatedSemaphore;
            } else {
                semaphore = lowUsageAuthenticatedSemaphore;
            }
        } else {
            if (limit > limitThreshold) {
                semaphore = highUsageSemaphore;
            } else {
                semaphore = lowUsageSemaphore;
            }
        }
        try {
            acquired = semaphore.tryAcquire();
            if (!acquired) {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Too much concurrent export request at this time, try again later");
                return;
            }

            int maxResults = maxResults(context, limit);
            if (maxResults == 0) {
                throw new AuthorizeException("You are not allowed to run the export process");
            }
            //sort, sortDirection, p, limit
            if (p > 0) {
                p--;
            }
            Pageable correctedPage = PageRequest.of(p, limit, Sort.by(Direction.valueOf(sortDirection), sort));
            DiscoverResultItemIterator itemsIterator = searchItemsToExport(context, scope, configuration,
                    query, searchFilters, correctedPage, maxResults,
                    streamDisseminationCrosswalk.isPubliclyReadable());
            final long totalSearchResults = itemsIterator.getTotalSearchResults();
            final long reqItemsToExport = totalSearchResults - correctedPage.getOffset();
            log.info("Found {} items to export", reqItemsToExport);
            if (reqItemsToExport > maxResults) {
                log.info("Export will be limited to {} items.", maxResults);
            }
            streamDisseminationCrosswalk.disseminate(context, itemsIterator, (int) totalSearchResults,
                    (int) correctedPage.getOffset(), maxResults, response.getOutputStream());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private int maxResults(Context context, int limit) throws SQLException {

        StringBuilder property = new StringBuilder("discover-export.limit.");
        if (authorizeService.isAdmin(context) || authorizeService.isComColAdmin(context)) {
            property.append("admin");
        } else {
            property.append(Optional.ofNullable(context.getCurrentUser()).map(ignored -> "loggedIn")
                                .orElse("notLoggedIn"));
        }
        int maxByUserCategory = configurationService.getIntProperty(property.toString(), -1);
        if (maxByUserCategory > 0 && limit > 0) {
            return Optional.ofNullable(limit)
                .map(l -> Math.min(l, maxByUserCategory))
                .orElse(maxByUserCategory);
        } else if (maxByUserCategory == -1  && limit > 0) {
            return limit;
        } else {
            return maxByUserCategory;
        }
    }

    private DiscoverResultItemIterator searchItemsToExport(Context context, String scope, String configuration,
            String query, List<SearchFilter> searchFilters, Pageable page, int maxResults, boolean onlyPublic)
            throws SearchServiceException, SQLException {
        IndexableObject<?, ?> scopeObject = resolveScope(context, scope);
        DiscoveryConfiguration discoveryConfiguration = discoveryConfigurationService
            .getDiscoveryConfigurationByNameOrDso(configuration, scopeObject);

        boolean isRelatedItem = discoveryConfiguration != null &&
            discoveryConfiguration instanceof DiscoveryRelatedItemConfiguration;

        List<String> dsoTypes = List.of(IndexableItem.TYPE, IndexableWorkspaceItem.TYPE, IndexableWorkflowItem.TYPE,
                IndexablePoolTask.TYPE, IndexableClaimedTask.TYPE);

        DiscoverQuery discoverQuery = restDiscoverQueryBuilder.buildQuery(context, scopeObject,
                discoveryConfiguration, query, searchFilters, dsoTypes, page);
        // force the iterator to use a pagination of 20 items, we are only interested in the start
        // offset of the original query
        discoverQuery.setMaxResults(20);
        if (onlyPublic) {
            Group anonymous = null;
            try {
                anonymous = groupService.findByName(context, Group.ANONYMOUS);
            } catch (SQLException e) {
                throw new RuntimeException("Cannot find anonymous group!", e);
            }
            discoverQuery.addFilterQueries("read:g" + anonymous.getID().toString());
        }

        if (isRelatedItem) {
            return new DiscoverResultItemIterator(context, discoverQuery, maxResults);
        } else {
            return new DiscoverResultItemIterator(context, scopeObject, discoverQuery, maxResults);
        }
    }

    private IndexableObject<?, ?> resolveScope(Context context, String scope) {
        IndexableObject<?, ?> scopeObj = null;
        if (StringUtils.isBlank(scope)) {
            return scopeObj;
        }

        try {

            UUID uuid = UUID.fromString(scope);
            scopeObj = new IndexableCommunity(communityService.find(context, uuid));
            if (scopeObj.getIndexedObject() == null) {
                scopeObj = new IndexableCollection(collectionService.find(context, uuid));
            }
            if (scopeObj.getIndexedObject() == null) {
                scopeObj = new IndexableItem(itemService.find(context, uuid));
            }

        } catch (IllegalArgumentException ex) {
            String message = "The given scope string " + trimToEmpty(scope) + " is not a UUID";
            log.warn(message);
        } catch (SQLException ex) {
            String message = "Unable to retrieve DSpace Object with ID " + trimToEmpty(scope) + " from the database";
            log.warn(message, ex);
        }
        return scopeObj;
    }

    /**
     * Don't use, available just for mocking purpose
     * @param streamDisseminationCrosswalk
     */
    public void setStreamDisseminationCrosswalk(StreamDisseminationCrosswalk streamDisseminationCrosswalk) {
        this.streamDisseminationCrosswalk = streamDisseminationCrosswalk;
    }
}
