/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.integration.crosswalks.CSLItemDataCrosswalk;
import org.dspace.content.integration.crosswalks.StreamDisseminationCrosswalkMapper;
import org.dspace.content.integration.crosswalks.csl.CSLPreparedItemData;
import org.dspace.content.integration.crosswalks.csl.CSLResult;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
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
import org.springframework.http.HttpStatus;
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
    private static final String GROUP_BY_TYPE_YEAR = "type,year";
    private static final String GROUP_BY_YEAR_TYPE = "year,type";
    private static final String SORT_DATE = "date";
    private static final String SORT_TITLE = "title";
    private static final String SORT_YEAR = "year";
    private static final String SORT_ASC = "asc";
    private static final String SORT_DESC = "desc";
    private static final String SORT_SEPARATOR = ":";
    private static final String SORT_MULTI_SEPARATOR = ",";
    private static final String UNKNOWN_GROUP = "Unknown";
    private static final String SEARCH_RESOURCE_ID_FIELD = "search.resourceid";
    private static final String TITLE_SORT_FIELD = "dc.title_sort";
    private static final String DATE_ISSUED_SORT_FIELD = "dc.date.issued_dt";
    private static final String DEFAULT_MAP_KEY = "nogroup";
    private static final int DEFAULT_BATCH_CHUNK_SIZE = 50;
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");
    private static final List<String> GROUP_VALUES =
            Arrays.asList(GROUP_BY_TYPE, GROUP_BY_YEAR, GROUP_BY_TYPE_YEAR, GROUP_BY_YEAR_TYPE);
    private static final Map<String, Map<String, List<CitationItem>>> EMPTY_RESULT =
            Collections.singletonMap(DEFAULT_MAP_KEY,
                    Collections.singletonMap(DEFAULT_MAP_KEY, Collections.emptyList()));

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
    private org.dspace.app.rest.utils.RestDiscoverQueryBuilder restDiscoverQueryBuilder;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getCitations(HttpServletRequest request,
                                                            @RequestBody CitationsRequestRest citationsRequest) {
        Context context = obtainContext(request);
        if (context != null && context.getCurrentUser() != null) {
            context.setMode(Mode.READ_ONLY);
        } else {
            return unauthorizedResponse();
        }

        try {
            validateRequest(citationsRequest);
        } catch (DSpaceBadRequestException e) {
            return badRequestResponse(e.getMessage());
        }


        List<Item> items = resolveItems(context, citationsRequest);
        if (items.isEmpty()) {
            return ResponseEntity.ok(buildResponse(citationsRequest, EMPTY_RESULT));
        }

        Map<String, Map<String, List<CitationItem>>> citationItems =
                buildCitationItems(context, items, citationsRequest);
        return ResponseEntity.ok(buildResponse(citationsRequest, citationItems));
    }

    private ResponseEntity<Map<String, Object>> unauthorizedResponse() {
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Unauthorized. Please provide a valid JWT token in the Authorization header");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    private ResponseEntity<Map<String, Object>> badRequestResponse(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
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
            throw new DSpaceBadRequestException(
                    "The 'groupBy' field must be 'type', 'year', 'type,year' or 'year,type");
        }

        if (StringUtils.isNotBlank(citationsRequest.getSort()) && !isSortValid(citationsRequest.getSort())) {
            throw new DSpaceBadRequestException(
                    "The 'sort' field must contain comma-separated clauses of 'date', 'title' or 'year', "
                    + "optionally followed by ':asc' or ':desc' (e.g. 'date:asc,title:desc')");
        }

        if (isEmpty(citationsRequest.getUuids()) && StringUtils.isBlank(citationsRequest.getQuery())
            && StringUtils.isBlank(citationsRequest.getConfiguration())
                && StringUtils.isBlank(citationsRequest.getScope())) {
            throw new DSpaceBadRequestException(
                    "Either 'uuids' or 'query' or 'configuration' or 'scope' must be provided");
        }
    }

    private boolean isEmpty(List<String> uuids) {
        return uuids == null || uuids.isEmpty();
    }

    private boolean isDisplayFormatValid(String format) {
        return DISPLAY_FORMAT_LIGHT.equals(format) || DISPLAY_FORMAT_FULL.equals(format);
    }

    private boolean isGroupByValid(String groupBy) {
        return GROUP_VALUES.contains(groupBy);
    }

    private boolean isSortValid(String sort) {
        String normalizedSort = StringUtils.trimToNull(sort);
        if (normalizedSort == null) {
            return false;
        }

        String[] clauses = normalizedSort.split(SORT_MULTI_SEPARATOR, -1);
        for (String clause : clauses) {
            if (!isSingleSortClauseValid(clause.trim())) {
                return false;
            }
        }
        return true;
    }

    private boolean isSingleSortClauseValid(String clause) {
        if (StringUtils.isBlank(clause)) {
            return false;
        }

        String[] tokens = clause.split(SORT_SEPARATOR, -1);
        if (tokens.length == 1) {
            return SORT_DATE.equals(tokens[0]) || SORT_TITLE.equals(tokens[0]) || SORT_YEAR.equals(tokens[0]);
        }

        if (tokens.length != 2) {
            return false;
        }

        String sortField = tokens[0];
        String sortOrder = tokens[1];
        return (SORT_DATE.equals(sortField) || SORT_TITLE.equals(sortField) || SORT_YEAR.equals(sortField))
                && (SORT_ASC.equals(sortOrder) || SORT_DESC.equals(sortOrder));
    }

    private List<Item> resolveItems(Context context, CitationsRequestRest citationsRequest) {
        String combinedQuery = buildCombinedQuery(citationsRequest);
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
                if (item != null) {
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

    private void applySort(DiscoverQuery discoverQuery, String sort) {
        if (StringUtils.isBlank(sort)) {
            return;
        }

        String[] clauses = StringUtils.trim(sort).split(SORT_MULTI_SEPARATOR, -1);
        for (String clause : clauses) {
            applySingleSortClause(discoverQuery, clause.trim());
        }
    }

    private void applySingleSortClause(DiscoverQuery discoverQuery, String clause) {
        String[] tokens = clause.split(SORT_SEPARATOR, -1);
        String sortField = tokens[0];
        SORT_ORDER sortOrder = getDefaultSortOrder(sortField);

        if (tokens.length == 2) {
            sortOrder = SORT_DESC.equals(tokens[1]) ? SORT_ORDER.desc : SORT_ORDER.asc;
        }

        String solrField;
        if (SORT_DATE.equals(sortField) || SORT_YEAR.equals(sortField)) {
            solrField = DATE_ISSUED_SORT_FIELD;
        } else {
            solrField = TITLE_SORT_FIELD;
        }

        discoverQuery.addSortField(solrField, sortOrder);
    }

    private SORT_ORDER getDefaultSortOrder(String sortField) {
        return SORT_TITLE.equals(sortField) ? SORT_ORDER.desc : SORT_ORDER.asc;
    }

    private Map<String, Map<String, List<CitationItem>>> buildCitationItems(Context context, List<Item> items,
                                                  CitationsRequestRest citationsRequest) {
        String style = citationsRequest.getStyle();
        String crosswalkType = normalizeStyleForCrosswalk(style);
        CSLItemDataCrosswalk crosswalkPatent =
                (CSLItemDataCrosswalk) streamDisseminationCrosswalkMapper.getByType("patent-" + crosswalkType);
        CSLItemDataCrosswalk crosswalkProduct =
                (CSLItemDataCrosswalk) streamDisseminationCrosswalkMapper.getByType("product-" + crosswalkType);
        CSLItemDataCrosswalk crosswalkPublication =
                (CSLItemDataCrosswalk) streamDisseminationCrosswalkMapper.getByType("publication-" + crosswalkType);
        if (crosswalkPatent == null || crosswalkProduct == null || crosswalkPublication == null) {
            throw new DSpaceBadRequestException("Unable to generate citations for style '" + style + "'");
        }
        boolean isFullFormat = DISPLAY_FORMAT_FULL.equals(citationsRequest.getFormat());

        // Group items by entity type for batch processing
        List<Item> patents = new ArrayList<>();
        List<Item> products = new ArrayList<>();
        List<Item> publications = new ArrayList<>();

        for (Item item : items) {
            String entityType = itemService.getEntityType(item);
            switch (entityType) {
                case "Patent":
                    patents.add(item);
                    break;
                case "Product":
                    products.add(item);
                    break;
                case "Publication":
                    publications.add(item);
                    break;
                default:
                    log.warn("Skipping item " + item.getID()
                            + " as it is not a supported entity type: " + entityType);
            }
        }

        // Batch generate citations per entity type (one CSL call per type)
        Map<UUID, String> citationsByUuid = new LinkedHashMap<>();
        Map<UUID, Object> parsedCslJsonByUuid = new LinkedHashMap<>();

        batchGenerateCitations(context, patents, crosswalkPatent, style, citationsByUuid, parsedCslJsonByUuid);
        batchGenerateCitations(context, products, crosswalkProduct, style, citationsByUuid, parsedCslJsonByUuid);
        batchGenerateCitations(context, publications, crosswalkPublication, style,
                citationsByUuid, parsedCslJsonByUuid);

        // Build the grouped result maintaining the original Solr sort order
        Map<String, Map<String, List<CitationItem>>> citationItems = new LinkedHashMap<>();
        for (Item item : items) {
            UUID itemId = item.getID();
            String citation = citationsByUuid.get(itemId);
            if (citation == null) {
                // Item was skipped (unsupported entity type)
                continue;
            }
            Object parsedCslJson = parsedCslJsonByUuid.get(itemId);

            if (isFullFormat) {
                addCitationItemFull(item, citation, parsedCslJson, citationItems, citationsRequest);
            } else {
                addCitationItemLight(item, citation, parsedCslJson, citationItems, citationsRequest);
            }
        }
        return citationItems;
    }

    private void batchGenerateCitations(Context context, List<Item> items, CSLItemDataCrosswalk crosswalk,
                                        String style, Map<UUID, String> citationsByUuid,
                                        Map<UUID, Object> parsedCslJsonByUuid) {
        if (items.isEmpty()) {
            return;
        }

        int chunkSize = configurationService.getIntProperty("citations.batch.chunk-size", DEFAULT_BATCH_CHUNK_SIZE);

        for (int offset = 0; offset < items.size(); offset += chunkSize) {
            List<Item> chunk = items.subList(offset, Math.min(offset + chunkSize, items.size()));
            processChunk(context, chunk, crosswalk, style, citationsByUuid, parsedCslJsonByUuid);
        }
    }

    private void processChunk(Context context, List<Item> chunk, CSLItemDataCrosswalk crosswalk,
                              String style, Map<UUID, String> citationsByUuid,
                              Map<UUID, Object> parsedCslJsonByUuid) {
        CSLPreparedItemData preparedItemData;
        try {
            preparedItemData = crosswalk.prepareItemData(context, chunk);
        } catch (Exception e) {
            throw new DSpaceBadRequestException("Unable to prepare citation data with style '" + style + "'", e);
        }

        // Parse the chunk JSON once for all items in this chunk
        Object parsedChunkJson = parseCslJsonObject(preparedItemData.getJson());

        CSLResult result = crosswalk.generateCitations(preparedItemData);

        if (result == null) {
            log.warn("CSL generator returned null for chunk of " + chunk.size()
                    + " items with style '" + style + "'");
            return;
        }

        UUID[] resultItemIds = result.getItemIds();
        String[] citationEntries = result.getCitationEntries();

        // Map each item's citation by UUID
        for (int i = 0; i < resultItemIds.length; i++) {
            citationsByUuid.put(resultItemIds[i], citationEntries[i]);
        }

        // Store the parsed JSON object for each item in this chunk (shared reference)
        for (Item item : chunk) {
            parsedCslJsonByUuid.put(item.getID(), parsedChunkJson);
        }
    }

    private void addCitationItemFull(Item item, String citation, Object parsedCslJson,
                                     Map<String, Map<String, List<CitationItem>>> citationItems,
                                     CitationsRequestRest citationsRequest) {
        CitationItemFull citationItem = new CitationItemFull();
        citationItem.uuid = item.getID().toString();
        citationItem.citation = citation;

        citationItem.handle = item.getHandle();
        citationItem.collection = Optional.ofNullable(item.getOwningCollection())
                .map(Collection::getName)
                .orElse(null);
        final String year = getYear(item);
        citationItem.year = year;

        citationItem.parsedCslItem = parsedCslJson;
        final String type = extractCslType(parsedCslJson, item.getID().toString());
        citationItem.type = type;

        List<CitationItem> destinationList =
                getGroupedCitationItemList(citationItems, citationsRequest.getGroupBy(), year, type);
        destinationList.add(citationItem);
    }

    private void addCitationItemLight(Item item, String citation, Object parsedCslJson,
                                      Map<String, Map<String, List<CitationItem>>> citationItems,
                                      CitationsRequestRest citationsRequest) {
        CitationItemLight citationItem = new CitationItemLight();
        citationItem.uuid = item.getID().toString();
        citationItem.citation = citation;
        List<CitationItem> destinationList;
        String groupBy = citationsRequest.getGroupBy();
        if (StringUtils.isBlank(groupBy)) {
            destinationList = getGroupedCitationItemList(citationItems, groupBy, null, null);
        } else if (GROUP_BY_YEAR.equals(groupBy)) {
            // Only year is needed for grouping — skip type extraction
            final String year = getYear(item);
            destinationList = getGroupedCitationItemList(citationItems, groupBy, year, null);
        } else {
            final String year = getYear(item);
            final String type = extractCslType(parsedCslJson, item.getID().toString());
            destinationList = getGroupedCitationItemList(citationItems, groupBy, year, type);
        }
        destinationList.add(citationItem);
    }

    /**
     * Retrieves a grouped list of CitationItem objects based on the specified parameters.
     * The grouping behavior is determined by the values of the provided groupBy, year, and type parameters.
     * If the input parameters are invalid or missing, default groups may be created in the citationItems map.
     *
     * @param citationItems the map containing existing groups of citation items, organized by keys like year and type
     * @param groupBy the grouping criteria to organize the citation items (e.g., "year", "type", "year,type")
     * @param year the year value used for grouping the citation items, when applicable
     * @param type the type value used for grouping the citation items, when applicable
     * @return a list of CitationItem objects corresponding to the specified grouping parameters
     * @throws DSpaceBadRequestException if the provided groupBy value is invalid
     */
    private static List<CitationItem> getGroupedCitationItemList(
            Map<String, Map<String, List<CitationItem>>> citationItems, String groupBy, String year, String type) {
        List<CitationItem> destinationList;
        if (StringUtils.isBlank(groupBy)) {
            if (citationItems.isEmpty()) {
                citationItems.put(DEFAULT_MAP_KEY, new LinkedHashMap<>());
                citationItems.get(DEFAULT_MAP_KEY).put(DEFAULT_MAP_KEY, new ArrayList<>());
            }
            destinationList = citationItems.get(DEFAULT_MAP_KEY).get(DEFAULT_MAP_KEY);
        } else {
            if (StringUtils.isBlank(year)) {
                year = UNKNOWN_GROUP;
            }
            if (StringUtils.isBlank(type)) {
                type = UNKNOWN_GROUP;
            }
            switch (groupBy) {
                case GROUP_BY_YEAR:
                    if (!citationItems.containsKey(year)) {
                        citationItems.put(year, new LinkedHashMap<>());
                        citationItems.get(year).put(DEFAULT_MAP_KEY, new ArrayList<>());
                    }
                    destinationList = citationItems.get(year).get(DEFAULT_MAP_KEY);
                    break;
                case GROUP_BY_TYPE:
                    if (!citationItems.containsKey(type)) {
                        citationItems.put(type, new LinkedHashMap<>());
                        citationItems.get(type).put(DEFAULT_MAP_KEY, new ArrayList<>());
                    }
                    destinationList = citationItems.get(type).get(DEFAULT_MAP_KEY);
                    break;
                case GROUP_BY_YEAR_TYPE:
                    if (!citationItems.containsKey(year)) {
                        citationItems.put(year, new LinkedHashMap<>());
                    }
                    if (!citationItems.get(year).containsKey(type)) {
                        citationItems.get(year).put(type, new ArrayList<>());
                    }
                    destinationList = citationItems.get(year).get(type);
                    break;
                case GROUP_BY_TYPE_YEAR:
                    if (!citationItems.containsKey(type)) {
                        citationItems.put(type, new LinkedHashMap<>());
                    }
                    if (!citationItems.get(type).containsKey(year)) {
                        citationItems.get(type).put(year, new ArrayList<>());
                    }
                    destinationList = citationItems.get(type).get(year);
                    break;
                default:
                    throw new DSpaceBadRequestException("Invalid groupBy value: " + groupBy);
            }
        }
        return destinationList;
    }

    private Map<String, Object> buildResponse(CitationsRequestRest request,
                                              Map<String, Map<String, List<CitationItem>>> citationItems) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (DISPLAY_FORMAT_FULL.equals(request.getFormat())) {
            response.put("style", normalizeStyleForResponse(request.getStyle()));
        }

        if (request.getGroupBy() != null && !request.getGroupBy().isEmpty()) {
            response.put("groupBy", request.getGroupBy());
        }

        Object results;

        boolean noGrouping = citationItems.size() == 1 && citationItems.containsKey(DEFAULT_MAP_KEY);
        boolean singleLevelGrouping = !noGrouping && citationItems.values().stream()
                .allMatch(map -> map.size() == 1 && map.containsKey(DEFAULT_MAP_KEY));

        if (noGrouping) {
            results = citationItems.get(DEFAULT_MAP_KEY)
                    .values()
                    .stream()
                    .flatMap(List::stream)
                    .map(CitationItem::toMap)
                    .collect(Collectors.toList());

        } else if (singleLevelGrouping) {
            results = citationItems.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().get(DEFAULT_MAP_KEY).stream()
                                    .map(CitationItem::toMap)
                                    .collect(Collectors.toList()),
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));

        } else {
            results = citationItems.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            outerEntry -> outerEntry.getValue().entrySet().stream()
                                    .collect(Collectors.toMap(
                                            Map.Entry::getKey,
                                            innerEntry -> innerEntry.getValue().stream()
                                                    .map(CitationItem::toMap)
                                                    .collect(Collectors.toList()),
                                            (a, b) -> a,
                                            LinkedHashMap::new
                                    )),
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));

        }

        response.put("results", results);
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

    private String getYear(Item item) {
        String issued = itemService.getMetadataFirstValue(item, "dc", "date", "issued", Item.ANY);
        if (StringUtils.isBlank(issued)) {
            return UNKNOWN_GROUP;
        }
        Matcher matcher = YEAR_PATTERN.matcher(issued);
        return matcher.find() ? matcher.group(1) : UNKNOWN_GROUP;
    }

    /**
     * Parses the CSL JSON string into a deserialized Object (Map/List structure).
     * This avoids parsing the same JSON multiple times.
     */
    private Object parseCslJsonObject(String cslJson) {
        try {
            return JSON_MAPPER.readValue(cslJson, Object.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing cslJson: " + cslJson, e);
            return null;
        }
    }

    /**
     * Extracts the CSL type from an already-parsed JSON object for the given item ID.
     */
    @SuppressWarnings("unchecked")
    private String extractCslType(Object parsedCslItem, String itemId) {
        if (parsedCslItem == null) {
            return null;
        }
        try {
            Map<String, Object> root = (Map<String, Object>) parsedCslItem;
            List<Map<String, Object>> items = (List<Map<String, Object>>) root.get("items");
            if (items == null) {
                return null;
            }
            for (Map<String, Object> item : items) {
                if (itemId.equals(String.valueOf(item.get("id")))) {
                    Object type = item.get("type");
                    return type != null ? type.toString() : null;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting type from parsed cslItem", e);
            return null;
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
        private Object parsedCslItem;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("uuid", uuid);
            map.put("handle", handle);
            map.put("type", type);
            map.put("collection", collection);
            map.put("year", year);
            map.put("citation", citation);
            map.put("cslItem", parsedCslItem);
            return map;
        }
    }
}
