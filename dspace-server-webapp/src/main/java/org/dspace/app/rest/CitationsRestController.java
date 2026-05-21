package org.dspace.app.rest;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.model.CitationsRequestRest;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.integration.crosswalks.csl.CSLGeneratorFactory;
import org.dspace.content.integration.crosswalks.csl.CSLResult;
import org.dspace.content.integration.crosswalks.csl.DSpaceListItemDataProvider;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.Context.Mode;
import org.dspace.discovery.DiscoverQuery;
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
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration endpoint for generating citations from items.
 */
@RestController
@RequestMapping("/api/integration/citations")
public class CitationsRestController {

    private static final String DEFAULT_DISPLAY_FORMAT = "full";
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
    private static final String CSL_OUTPUT_FORMAT = "text";
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
    private CSLGeneratorFactory cslGeneratorFactory;

    @Autowired
    private AuthorizeService authorizeService;

    @Autowired
    private org.dspace.app.rest.utils.RestDiscoverQueryBuilder restDiscoverQueryBuilder;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getCitations(HttpServletRequest request,
                                                            @RequestBody CitationsRequestRest citationsRequest)
            throws SQLException {
        Context context = obtainContext(request);
        if (context != null) {
            context.setMode(Mode.READ_ONLY);
        }

        validateRequest(citationsRequest);

        List<Item> items = resolveItems(context, citationsRequest);
        if (items.isEmpty()) {
            return ResponseEntity.ok(buildResponse(citationsRequest, Collections.emptyList(), Collections.emptyList()));
        }

        items = sortItems(items, citationsRequest.getSort());
        List<CitationItem> citationItems = buildCitationItems(items, normalizeStyle(citationsRequest.getStyle()));
        return ResponseEntity.ok(buildResponse(citationsRequest, items, citationItems));
    }

    private void validateRequest(CitationsRequestRest citationsRequest) {
        if (citationsRequest == null) {
            throw new DSpaceBadRequestException("The request body is required");
        }

        if (StringUtils.isBlank(citationsRequest.getStyle())) {
            throw new DSpaceBadRequestException("The 'style' field is required");
        }

        if (StringUtils.isNotBlank(citationsRequest.getFormat())
                && !isDisplayFormatValid(citationsRequest.getFormat())) {
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
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        return DISPLAY_FORMAT_LIGHT.equals(normalized) || DISPLAY_FORMAT_FULL.equals(normalized);
    }

    private boolean isGroupByValid(String groupBy) {
        String normalized = groupBy.trim().toLowerCase(Locale.ROOT);
        return GROUP_BY_TYPE.equals(normalized) || GROUP_BY_YEAR.equals(normalized) || GROUP_BY_BOTH.equals(normalized);
    }

    private boolean isSortValid(String sort) {
        String normalized = sort.trim().toLowerCase(Locale.ROOT);
        return SORT_DATE.equals(normalized) || SORT_TITLE.equals(normalized) || SORT_YEAR.equals(normalized);
    }

    private List<Item> resolveItems(Context context, CitationsRequestRest citationsRequest) throws SQLException {
        Map<UUID, Item> itemsByUuid = new LinkedHashMap<>();

        if (!isEmpty(citationsRequest.getUuids())) {
            for (String uuidString : citationsRequest.getUuids()) {
                if (StringUtils.isBlank(uuidString)) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidString.trim());
                } catch (IllegalArgumentException e) {
                    throw new DSpaceBadRequestException("Invalid item UUID: " + uuidString, e);
                }

                Item item = itemService.find(context, uuid);
                if (item == null) {
                    throw new ResourceNotFoundException("Could not find item with id " + uuid);
                }
                if (canRead(context, item)) {
                    itemsByUuid.putIfAbsent(item.getID(), item);
                }
            }
        }

        if (StringUtils.isNotBlank(citationsRequest.getQuery())) {
            itemsByUuid.putAll(resolveItemsFromQuery(context, citationsRequest));
        }

        return new ArrayList<>(itemsByUuid.values());
    }

    private Map<UUID, Item> resolveItemsFromQuery(Context context, CitationsRequestRest citationsRequest) {
        IndexableObject<?, ?> scopeObject = resolveScope(context, citationsRequest.getScope());
        DiscoveryConfiguration discoveryConfiguration = discoveryConfigurationService
            .getDiscoveryConfigurationByNameOrDso(citationsRequest.getConfiguration(), scopeObject);

        DiscoverQuery discoverQuery = restDiscoverQueryBuilder.buildQuery(context, scopeObject,
                discoveryConfiguration, citationsRequest.getQuery(), Collections.emptyList(), IndexableItem.TYPE, null);
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

    private List<Item> sortItems(List<Item> items, String sort) {
        if (StringUtils.isBlank(sort)) {
            return items;
        }

        String normalized = sort.trim().toLowerCase(Locale.ROOT);
        Comparator<Item> comparator;
        if (SORT_DATE.equals(normalized)) {
            comparator = Comparator.comparing(this::getIssuedDate, Comparator.nullsLast(String::compareTo)).reversed();
        } else if (SORT_YEAR.equals(normalized)) {
            comparator = Comparator.comparing(this::getYear, Comparator.nullsLast(String::compareTo));
        } else {
            comparator = Comparator.comparing(this::getTitle, Comparator.nullsLast(String::compareTo));
        }
        return items.stream().sorted(comparator).collect(Collectors.toList());
    }

    private String getTitle(Item item) {
        return getDcMetadataValue(item, "title", null).orElse(null);
    }

    private String getIssuedDate(Item item) {
        return getDcMetadataValue(item, "date", "issued").orElse(null);
    }

    private List<CitationItem> buildCitationItems(List<Item> items, String style) {
        DSpaceListItemDataProvider itemDataProvider = new DSpaceListItemDataProvider(itemService);
        itemDataProvider.processItems(items.iterator());

        CSLResult cslResult = cslGeneratorFactory.getCSLGenerator().generate(itemDataProvider, style,
                CSL_OUTPUT_FORMAT);
        if (cslResult == null) {
            throw new DSpaceBadRequestException("Unable to generate citations for style '" + style + "'");
        }

        List<CitationItem> citationItems = new ArrayList<>();
        String[] citations = cslResult.getCitationEntries();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            CitationItem citationItem = new CitationItem();
            citationItem.uuid = item.getID().toString();
            citationItem.citation = citations.length > i ? citations[i] : null;
            citationItem.title = getDcMetadataValue(item, "title", null).orElse(null);
            citationItem.entityType = Optional.ofNullable(itemService.getEntityType(item)).orElse(OTHER_GROUP);
            citationItem.year = getYear(item);
            citationItems.add(citationItem);
        }
        return citationItems;
    }

    private Map<String, Object> buildResponse(CitationsRequestRest request, List<Item> items,
                                              List<CitationItem> citationItems) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("style", normalizeStyle(request.getStyle()));
        response.put("format", normalizeDisplayFormat(request.getFormat()));
        response.put("groupBy", normalizeGroupBy(request.getGroupBy()));
        response.put("sort", normalizeSort(request.getSort()));
        response.put("groups", buildGroups(items, citationItems, request.getGroupBy(), request.getFormat()));
        return response;
    }

    private List<Map<String, Object>> buildGroups(List<Item> items, List<CitationItem> citationItems,
                                                  String groupBy, String displayFormat) {
        String normalizedGroupBy = normalizeGroupBy(groupBy);
        String normalizedDisplayFormat = normalizeDisplayFormat(displayFormat);

        LinkedHashMap<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            CitationItem citationItem = citationItems.get(i);
            String groupKey = buildGroupKey(item, normalizedGroupBy);
            groups.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(citationItem.toMap(normalizedDisplayFormat));
        }

        if (groups.isEmpty()) {
            groups.put("all", new ArrayList<>());
        }

        List<Map<String, Object>> responseGroups = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("key", entry.getKey());
            group.put("items", entry.getValue());
            responseGroups.add(group);
        }
        return responseGroups;
    }

    private String buildGroupKey(Item item, String groupBy) {
        if (StringUtils.isBlank(groupBy)) {
            return "all";
        }

        String type = Optional.ofNullable(itemService.getEntityType(item)).filter(StringUtils::isNotBlank)
            .orElse(OTHER_GROUP);
        String year = getYear(item);

        if (GROUP_BY_TYPE.equals(groupBy)) {
            return type;
        }
        if (GROUP_BY_YEAR.equals(groupBy)) {
            return year;
        }
        return type + " / " + year;
    }

    private String normalizeStyle(String style) {
        String normalized = style.trim();
        return StringUtils.endsWithIgnoreCase(normalized, ".csl") ? normalized : normalized + ".csl";
    }

    private String normalizeDisplayFormat(String format) {
        return StringUtils.isBlank(format) ? DEFAULT_DISPLAY_FORMAT : format.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeGroupBy(String groupBy) {
        return StringUtils.isBlank(groupBy) ? null : groupBy.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSort(String sort) {
        return StringUtils.isBlank(sort) ? null : sort.trim().toLowerCase(Locale.ROOT);
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

    private static class CitationItem {
        private String uuid;
        private String citation;
        private String title;
        private String entityType;
        private String year;

        private Map<String, Object> toMap(String format) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("uuid", uuid);
            map.put("citation", citation);
            if (DISPLAY_FORMAT_FULL.equals(format)) {
                map.put("title", title);
                map.put("entityType", entityType);
                map.put("year", year);
            }
            return map;
        }
    }
}



