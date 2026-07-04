/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.model.CitationsRequestRest;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.StreamDisseminationCrosswalk;
import org.dspace.content.integration.crosswalks.StreamDisseminationCrosswalkMapper;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.Context.Mode;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverQuery.SORT_ORDER;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.configuration.DiscoveryConfiguration;
import org.dspace.discovery.configuration.DiscoveryConfigurationService;
import org.dspace.discovery.indexobject.IndexableCollection;
import org.dspace.discovery.indexobject.IndexableCommunity;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration endpoint for generating citations from items.
 *
 * @author  Daniele Ninfo (daniele.ninfo at 4science.com)
 */
@RestController
@RequestMapping("/api/integration/citations")
public class CitationsRestController {

    private static final Logger log = LogManager.getLogger(CitationsRestController.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final String DISPLAY_FORMAT_LIGHT = "light";
    private static final String DISPLAY_FORMAT_FULL = "full";
    private static final String GROUP_BY_TYPE = "type";
    private static final String GROUP_BY_YEAR = "year";
    private static final String GROUP_BY_BOTH = "both";
    private static final String SORT_DATE = "date";
    private static final String SORT_TITLE = "title";
    private static final String SORT_YEAR = "year";
    private static final String UNKNOWN_GROUP = "Unknown";
    private static final String OTHER_GROUP = "Other";
    private static final String SEARCH_RESOURCE_ID_FIELD = "search.resourceid";
    private static final String TITLE_SORT_FIELD = "dc.title_sort";
    private static final String DATE_ISSUED_SORT_FIELD = "dc.date.issued_dt";
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");

    @Autowired
    private ItemService itemService;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private DiscoveryConfigurationService discoveryConfigurationService;

    @Autowired
    private SearchService searchService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private StreamDisseminationCrosswalkMapper streamDisseminationCrosswalkMapper;

    @Autowired
    private AuthorizeService authorizeService;

    @Autowired
    private org.dspace.app.rest.utils.RestDiscoverQueryBuilder restDiscoverQueryBuilder;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getCitations(HttpServletRequest request,
                                                            @RequestBody CitationsRequestRest citationsRequest) {
        Context context = obtainContext(request);
        if (context != null) {
            context.setMode(Mode.READ_ONLY);
        }

        validateRequest(citationsRequest);

        List<Item> items = resolveItems(context, citationsRequest);
        if (items.isEmpty()) {
            return ResponseEntity.ok(buildResponse(citationsRequest, Collections.emptyList()));
        }

        List<CitationItem> citationItems = buildCitationItems(context, items, citationsRequest);
        return ResponseEntity.ok(buildResponse(citationsRequest, citationItems));
    }

    private void validateRequest(CitationsRequestRest citationsRequest) {
        if (citationsRequest == null) {
            throw new DSpaceBadRequestException("The request body is required");
        }

        if (StringUtils.isBlank(citationsRequest.getStyle())) {
            throw new DSpaceBadRequestException("The 'style' field is required");
        }

        if (StringUtils.isBlank(citationsRequest.getFormat())
                || !isDisplayFormatValid(citationsRequest.getFormat())) {
            throw new DSpaceBadRequestException("The 'format' field must be 'light' or 'full'");
        }

        if (StringUtils.isNotBlank(citationsRequest.getGroupBy())
                && !isGroupByValid(citationsRequest.getGroupBy())) {
            throw new DSpaceBadRequestException("The 'groupBy' field must be 'type', 'year' or 'both'");
        }

        if (StringUtils.isNotBlank(citationsRequest.getSort()) && !isSortValid(citationsRequest.getSort())) {
            throw new DSpaceBadRequestException("The 'sort' field must be 'date', 'title' or 'year'");
        }

        if (isEmpty(citationsRequest.getUuids()) && StringUtils.isBlank(citationsRequest.getQuery())) {
            throw new DSpaceBadRequestException("Either 'uuids' or 'query' must be provided");
        }
    }

    private boolean isEmpty(List<String> uuids) {
        return uuids == null || uuids.isEmpty();
    }

    private boolean isDisplayFormatValid(String format) {
        String normalized = normalize(format);
        return DISPLAY_FORMAT_LIGHT.equals(normalized) || DISPLAY_FORMAT_FULL.equals(normalized);
    }

    private boolean isGroupByValid(String groupBy) {
        String normalized = normalize(groupBy);
        return GROUP_BY_TYPE.equals(normalized) || GROUP_BY_YEAR.equals(normalized) || GROUP_BY_BOTH.equals(normalized);
    }

    private boolean isSortValid(String sort) {
        String normalized = normalize(sort);
        return SORT_DATE.equals(normalized) || SORT_TITLE.equals(normalized) || SORT_YEAR.equals(normalized);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private List<Item> resolveItems(Context context, CitationsRequestRest citationsRequest) {
        String combinedQuery = buildCombinedQuery(citationsRequest);
        if (StringUtils.isBlank(combinedQuery)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(resolveItemsFromQuery(context, citationsRequest, combinedQuery).values());
    }

    private String buildCombinedQuery(CitationsRequestRest citationsRequest) {
        String uuidQuery = buildUuidQuery(citationsRequest.getUuids());
        String textQuery = StringUtils.trimToNull(citationsRequest.getQuery());

        if (textQuery == null) {
            return uuidQuery;
        }
        if (uuidQuery == null) {
            return textQuery;
        }
        return "(" + textQuery + ") AND (" + uuidQuery + ")";
    }

    private String buildUuidQuery(List<String> uuidStrings) {
        if (isEmpty(uuidStrings)) {
            return null;
        }

        List<String> uuidClauses = uuidStrings.stream()
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .map(this::parseUuid)
            .map(uuid -> SEARCH_RESOURCE_ID_FIELD + ":\"" + uuid + "\"")
            .distinct()
            .collect(Collectors.toList());

        if (uuidClauses.isEmpty()) {
            return null;
        }
        if (uuidClauses.size() == 1) {
            return uuidClauses.get(0);
        }
        return uuidClauses.stream().collect(Collectors.joining(" OR ", "(", ")"));
    }

    private UUID parseUuid(String uuidString) {
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            throw new DSpaceBadRequestException("Invalid item UUID: " + uuidString, e);
        }
    }

    private Map<UUID, Item> resolveItemsFromQuery(Context context, CitationsRequestRest citationsRequest,
                                                  String query) {
        IndexableObject<?, ?> scopeObject = resolveScope(context, citationsRequest.getScope());
        DiscoveryConfiguration discoveryConfiguration = discoveryConfigurationService
            .getDiscoveryConfigurationByNameOrDso(citationsRequest.getConfiguration(), scopeObject);

        DiscoverQuery discoverQuery = restDiscoverQueryBuilder.buildQuery(context, scopeObject,
                discoveryConfiguration, query, Collections.emptyList(), IndexableItem.TYPE, null);
        applySort(discoverQuery, citationsRequest.getSort());
        discoverQuery.setMaxResults(configurationService.getIntProperty("rest.search.max.results", 100));

        Map<UUID, Item> itemsByUuid = new LinkedHashMap<>();
        try {
            Iterator<Item> iterator = searchService.iteratorSearch(context, scopeObject, discoverQuery);
            while (iterator.hasNext()) {
                Item item = iterator.next();
                if (item != null && canRead(context, item)) {
                    itemsByUuid.putIfAbsent(item.getID(), item);
                }
            }
        } catch (SearchServiceException e) {
            throw new DSpaceBadRequestException("Unable to execute the discovery query", e);
        }
        return itemsByUuid;
    }

    private IndexableObject<?, ?> resolveScope(Context context, String scope) {
        if (StringUtils.isBlank(scope)) {
            return null;
        }

        try {
            UUID uuid = UUID.fromString(scope.trim());
            Community community = communityService.find(context, uuid);
            if (community != null) {
                return new IndexableCommunity(community);
            }

            Collection collection = collectionService.find(context, uuid);
            if (collection != null) {
                return new IndexableCollection(collection);
            }

            Item item = itemService.find(context, uuid);
            if (item != null) {
                return new IndexableItem(item);
            }
        } catch (IllegalArgumentException ex) {
            throw new DSpaceBadRequestException("The given scope string '" + scope + "' is not a UUID", ex);
        } catch (SQLException ex) {
            throw new RuntimeException("Unable to retrieve DSpace Object with ID '" +
                    scope + "' from the database", ex);
        }

        return null;
    }

    private boolean canRead(Context context, Item item) {
        try {
            return authorizeService.authorizeActionBoolean(context, item, Constants.READ);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to verify read permission for item " + item.getID(), e);
        }
    }

    private void applySort(DiscoverQuery discoverQuery, String sort) {
        if (StringUtils.isBlank(sort)) {
            return;
        }

        String normalized = normalize(sort);
        if (SORT_DATE.equals(normalized)) {
            discoverQuery.setSortField(DATE_ISSUED_SORT_FIELD, SORT_ORDER.asc);
        } else if (SORT_YEAR.equals(normalized)) {
            discoverQuery.setSortField(DATE_ISSUED_SORT_FIELD, SORT_ORDER.asc);
        } else {
            discoverQuery.setSortField(TITLE_SORT_FIELD, SORT_ORDER.asc);
        }
    }

    private List<CitationItem> buildCitationItems(Context context, List<Item> items,
                                                  CitationsRequestRest citationsRequest) {
        String style = citationsRequest.getStyle();
        String crosswalkType = normalizeStyleForCrosswalk(style);
        StreamDisseminationCrosswalk crosswalkPatent =
                streamDisseminationCrosswalkMapper.getByType("patent-" + crosswalkType);
        StreamDisseminationCrosswalk crosswalkProduct =
                streamDisseminationCrosswalkMapper.getByType("product-" + crosswalkType);
        StreamDisseminationCrosswalk crosswalkPublication =
                streamDisseminationCrosswalkMapper.getByType("publication-" + crosswalkType);
        StreamDisseminationCrosswalk crosswalkPatentJson =
                streamDisseminationCrosswalkMapper.getByType("patent-json");
        StreamDisseminationCrosswalk crosswalkProductJson =
                streamDisseminationCrosswalkMapper.getByType("product-json");
        StreamDisseminationCrosswalk crosswalkPublicationJson =
                streamDisseminationCrosswalkMapper.getByType("publication-json");
        if (crosswalkPatent == null || crosswalkProduct == null || crosswalkPublication == null) {
            throw new DSpaceBadRequestException("Unable to generate citations for style '" + style + "'");
        }
        boolean isFullFormat = DISPLAY_FORMAT_FULL.equals(normalize(citationsRequest.getFormat()));

        List<CitationItem> citationItems = new ArrayList<>();
        for (Item item : items) {
            StreamDisseminationCrosswalk crosswalk;
            StreamDisseminationCrosswalk jsonCrosswalk;
            String entityType = itemService.getEntityType(item);
            switch (entityType) {
                case "Patent":
                    crosswalk = crosswalkPatent;
                    jsonCrosswalk = crosswalkPatentJson;
                    break;
                case "Product":
                    crosswalk = crosswalkProduct;
                    jsonCrosswalk = crosswalkProductJson;
                    break;
                case "Publication":
                    crosswalk = crosswalkPublication;
                    jsonCrosswalk = crosswalkPublicationJson;
                    break;
                default:
                    crosswalk = null;
                    jsonCrosswalk = null;
            }
            if (crosswalk == null) {
                log.warn("Skipping item " + item.getID() + " as it is not a supported entity type: " + entityType);
                continue;
            }
            if (isFullFormat) {
                CitationItemFull citationItem = new CitationItemFull();
                citationItem.uuid = item.getID().toString();
                citationItem.citation = exportCitationByStyle(context, item, crosswalk, crosswalkType);

                citationItem.handle = item.getHandle();
                citationItem.type = getType(item);
                citationItem.collection = Optional.ofNullable(item.getOwningCollection())
                        .map(Collection::getName)
                        .orElse(null);
                citationItem.year = getYear(item);
                citationItem.cslItem = exportCitationByStyle(context, item, jsonCrosswalk, crosswalkType);
                citationItems.add(citationItem);
            } else {
                CitationItemLight citationItem = new CitationItemLight();
                citationItem.uuid = item.getID().toString();
                citationItem.citation = exportCitationByStyle(context, item, crosswalk, crosswalkType);
                citationItems.add(citationItem);
            }

        }
        return citationItems;
    }

    private Map<String, Object> buildResponse(CitationsRequestRest request, List<CitationItem> citationItems) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (DISPLAY_FORMAT_FULL.equals(normalize(request.getFormat()))) {
            //response.put("groupBy", normalizeGroupByAsList(request.getGroupBy()));
            response.put("style", normalizeStyleForResponse(request.getStyle()));
        }
        response.put("results", citationItems.stream().map(CitationItem::toMap).collect(Collectors.toList()));
        return response;
    }

    private String normalizeStyleForCrosswalk(String style) {
        String normalized = style.trim();
        return StringUtils.removeEndIgnoreCase(normalized, ".csl").toLowerCase(Locale.ROOT);
    }

    private String normalizeStyleForResponse(String style) {
        String normalized = style.trim();
        return StringUtils.removeEndIgnoreCase(normalized, ".csl");
    }

    private String normalizeGroupBy(String groupBy) {
        return StringUtils.isBlank(groupBy) ? null : groupBy.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeGroupByAsList(String groupBy) {
        String normalized = normalizeGroupBy(groupBy);
        if (normalized == null) {
            return Collections.emptyList();
        }
        if (GROUP_BY_BOTH.equals(normalized)) {
            return List.of(GROUP_BY_YEAR, GROUP_BY_TYPE);
        }
        return List.of(normalized);
    }

    private String getYear(Item item) {
        String issued = getDcMetadataValue(item, "date", "issued").orElse(null);
        if (StringUtils.isBlank(issued)) {
            return UNKNOWN_GROUP;
        }
        Matcher matcher = YEAR_PATTERN.matcher(issued);
        return matcher.find() ? matcher.group(1) : UNKNOWN_GROUP;
    }

    private Optional<String> getDcMetadataValue(Item item, String element, String qualifier) {
        return Optional.ofNullable(itemService.getMetadataFirstValue(item, "dc", element, qualifier, Item.ANY))
                       .filter(StringUtils::isNotBlank);
    }

    private String getType(Item item) {
        return getDcMetadataValue(item, "type", null)
                .orElse(Optional.ofNullable(itemService.getEntityType(item)).orElse(OTHER_GROUP));
    }

    private String exportCitationByStyle(Context context, Item item, StreamDisseminationCrosswalk crosswalk,
                                         String style) {
        try {
            if (!crosswalk.canDisseminate(context, item)) {
                throw new DSpaceBadRequestException("Unable to generate citation for item " + item.getID()
                        + " with style '" + style + "'");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            crosswalk.disseminate(context, item, out);
            return out.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (IOException | RuntimeException | org.dspace.authorize.AuthorizeException
                 | org.dspace.content.crosswalk.CrosswalkException | SQLException e) {
            throw new DSpaceBadRequestException("Unable to generate citation for item " + item.getID()
                    + " with style '" + style + "'", e);
        }
    }
    private interface CitationItem {
        Map<String, Object> toMap();
    }

    private static class CitationItemLight implements CitationItem {
        private String uuid;
        private String citation;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("uuid", uuid);
            map.put("citation", citation);
            return map;
        }
    }

    private static class CitationItemFull implements CitationItem {
        private String uuid;
        private String handle;
        private String type;
        private String collection;
        private String citation;
        private String year;
        private String cslItem;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("uuid", uuid);
            map.put("handle", handle);
            map.put("type", type);
            map.put("collection", collection);
            map.put("year", year);
            map.put("citation", citation);
            try {
                map.put("cslItem", JSON_MAPPER.readValue(cslItem, Object.class));
            } catch (JsonProcessingException e) {
                log.error("Error converting cslItem to json: " + cslItem, e);
            }
            return map;
        }
    }
}



