/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.citation;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.Context.Mode;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.SearchUtils;
import org.dspace.discovery.indexobject.IndexableCollection;
import org.dspace.discovery.indexobject.IndexableCommunity;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;

/**
 * Script that pre-computes citation metadata (epfl.citation.*) for Publication, Patent and Product items.
 * Designed to be run periodically (e.g. nightly) to keep cached citations up-to-date.
 *
 * @author Daniele Ninfo (daniele.ninfo at 4science.com)
 */
public class CitationMetadataScript
        extends DSpaceRunnable<CitationMetadataScriptConfiguration<CitationMetadataScript>> {

    private static final int DEFAULT_COMMIT_SIZE = 100;
    private static final String[] DEFAULT_STYLES =
        { "apa", "chicago", "ieee", "vancouver", "harvard", "mla", "iso690" };

    private Context context;
    private String index;
    private boolean force;
    private int commitSize;

    private String[] styles;
    private int checkIntervalHours;
    private int dateOffsetMinutes;

    private ItemService itemService;
    private CommunityService communityService;
    private CollectionService collectionService;
    private CitationService citationService;

    @SuppressWarnings("unchecked")
    @Override
    public CitationMetadataScriptConfiguration<CitationMetadataScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("citation-metadata",
                CitationMetadataScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        context = new Context(Mode.BATCH_EDIT);
        index = commandLine.getOptionValue('i');
        force = commandLine.hasOption('f');
        commitSize = Integer.parseInt(commandLine.getOptionValue("c", String.valueOf(DEFAULT_COMMIT_SIZE)));

        itemService = ContentServiceFactory.getInstance().getItemService();
        communityService = ContentServiceFactory.getInstance().getCommunityService();
        collectionService = ContentServiceFactory.getInstance().getCollectionService();
        citationService = new DSpace().getSingletonService(CitationServiceImpl.class);
        if (citationService == null) {
            throw new IllegalStateException(
                "CitationServiceImpl bean not found. Check Spring configuration.");
        }
        ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        styles = configurationService.getArrayProperty("citation-script.filter", DEFAULT_STYLES);
        checkIntervalHours = configurationService.getIntProperty("citation-script.check-interval", 25);
        dateOffsetMinutes = configurationService.getIntProperty("citation-script.date-offset", 10);
    }

    @Override
    public void internalRun() throws Exception {
        try {
            context.turnOffAuthorisationSystem();
            handler.logInfo("Citation metadata script started");

            IndexableObject<?, ?> scopeObject = resolveScope();

            int processed = 0;
            int errors = 0;
            int skipped = 0;
            // Forward-only cursor: tracks the last processed/seen item ID.
            // Each Solr query fetches items with resourceid > lastSeenId.
            String lastSeenId = null;
            boolean hasMore = true;

            while (hasMore) {
                List<Item> page = fetchPage(scopeObject, lastSeenId);

                if (page.isEmpty()) {
                    break;
                }

                int processedInPage = 0;

                for (Item item : page) {
                    if (item == null || !item.isArchived()) {
                        continue;
                    }

                    // Always track last seen ID for cursor advancement
                    lastSeenId = item.getID().toString();

                    if (!force && !needsUpdate(item)) {
                        skipped++;
                        context.uncacheEntity(item);
                        continue;
                    }

                    try {
                        saveCitationMetadata(item);
                        processedInPage++;
                        processed++;
                    } catch (Exception e) {
                        errors++;
                        handler.logError("Error processing item " + item.getID() + ": "
                                + e.getClass().getName() + " - " + e.getMessage());
                    } finally {
                        context.uncacheEntity(item);
                    }
                }

                context.commit();

                if (processedInPage > 0) {
                    handler.logInfo("Committed after " + processed + " items processed so far");
                }

                // Cursor always advances forward (lastSeenId tracks the last item in the page).
                // In force mode: items still match the query, cursor skips past them.
                // In non-force mode: cursor advances past skipped items; processed items
                // will still match the Solr time filter but needsUpdate() will skip them
                // if they reappear in a future page. No reset needed.
                // Termination: when the page is empty (no more items beyond lastSeenId).
            }

            handler.logInfo("Citation metadata script completed. Total items processed: " + processed
                    + ", skipped: " + skipped + ", errors: " + errors);
            context.restoreAuthSystemState();
            context.complete();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        }
    }

    /**
     * Fetches a single page of items from Solr.
     *
     * <p>Uses cursor-based pagination: the {@code lastSeenId} filter skips all items with
     * resourceid &lt;= lastSeenId. The cursor always advances forward through the result set.</p>
     */
    @SuppressWarnings("rawtypes")
    private List<Item> fetchPage(IndexableObject<?, ?> scopeObject, String lastSeenId)
            throws SearchServiceException {
        DiscoverQuery discoverQuery = buildDiscoverQuery(lastSeenId);
        SearchService searchService = SearchUtils.getSearchService();

        DiscoverResult result;
        if (scopeObject == null) {
            result = searchService.search(context, discoverQuery);
        } else {
            result = searchService.search(context, scopeObject, discoverQuery);
        }

        List<IndexableObject> indexableObjects = result.getIndexableObjects();
        return indexableObjects.stream()
                .filter(obj -> obj instanceof IndexableItem)
                .map(obj -> ((IndexableItem) obj).getIndexedObject())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Builds the Solr discovery query. Always starts from offset 0.
     *
     * @param lastSeenId if not null, adds a range filter to skip past this ID (cursor advancement)
     */
    private DiscoverQuery buildDiscoverQuery(String lastSeenId) {
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.setMaxResults(commitSize);
        discoverQuery.setStart(0);
        discoverQuery.setSortField("search.resourceid", DiscoverQuery.SORT_ORDER.asc);
        discoverQuery.addFilterQueries(
                "entityType_keyword:Publication OR entityType_keyword:Product OR entityType_keyword:Patent",
                "-withdrawn:true",
                "-discoverable:false",
                "latestVersion:true"
        );

        // When not forcing, narrow down to only items that likely need processing:
        // 1. Items without epfl.citation.date -> never processed, need generation
        // 2. Items whose lastModified is recent -> potentially modified since last run
        // The needsUpdate check will do the precise lastModified > citationDate comparison.
        if (!force) {
            discoverQuery.addFilterQueries(
                "(*:* -epfl.citation.date:*) OR lastModified:[NOW-" + checkIntervalHours + "HOURS TO NOW]"
            );
        }

        // Cursor-based advancement: skip past items already seen in the current run.
        // The cursor always moves forward, ensuring O(n) total work across all pages.
        if (lastSeenId != null) {
            discoverQuery.addFilterQueries("search.resourceid:{" + lastSeenId + " TO *}");
        }

        return discoverQuery;
    }

    /**
     * Resolves the scope object from the -i parameter (item, collection, or community).
     * Returns null if no -i is specified (global scope).
     */
    private IndexableObject<?, ?> resolveScope() throws SQLException {
        if (StringUtils.isBlank(index)) {
            return null;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(index.trim());
        } catch (IllegalArgumentException e) {
            handler.logError("The given index is not a valid UUID: " + index);
            throw e;
        }

        // Check if it's a single item — handled separately in the caller if needed
        Item item = itemService.find(context, uuid);
        if (item != null) {
            return new IndexableItem(item);
        }

        Community community = communityService.find(context, uuid);
        if (community != null) {
            return new IndexableCommunity(community);
        }

        Collection collection = collectionService.find(context, uuid);
        if (collection != null) {
            return new IndexableCollection(collection);
        }

        handler.logError("UUID does not match any item, collection, or community: " + uuid);
        throw new IllegalArgumentException("UUID not found: " + uuid);
    }

    /**
     * Checks if the item needs a citation update by comparing epfl.citation.date with lastModified.
     * Returns true if the item has no citation date or if lastModified is strictly after the citation date.
     */
    private boolean needsUpdate(Item item) {
        String citationDateStr = itemService.getMetadataFirstValue(item, "epfl", "citation", "date", Item.ANY);
        if (StringUtils.isBlank(citationDateStr)) {
            return true;
        }
        try {
            Instant citationInstant = Instant.parse(citationDateStr);
            Date lastModified = item.getLastModified();
            if (lastModified == null) {
                return true;
            }
            return lastModified.toInstant().isAfter(citationInstant);
        } catch (Exception e) {
            handler.logWarning("Unable to parse epfl.citation.date '" + citationDateStr
                    + "' for item " + item.getID() + " — forcing regeneration");
            return true;
        }
    }

    /**
     * Saves citation metadata on the item using the CitationService.
     * Throws an exception if processing fails, allowing the caller to handle it per-item.
     */
    private void saveCitationMetadata(Item item) throws SQLException {
        handler.logInfo("Saving citation metadata for item " + item.getID());
        Map<String, String> citations = citationService.generateAllCitations(context, item, styles);
        try {
            for (Map.Entry<String, String> entry : citations.entrySet()) {
                String qualifier = entry.getKey();
                String value = entry.getValue();
                if (StringUtils.isNotBlank(value)) {
                    itemService.clearMetadata(context, item, "epfl", "citation", qualifier, Item.ANY);
                    itemService.addMetadata(context, item, "epfl", "citation", qualifier, null, value);
                }
            }

            // Set citation date slightly in the future to ensure it's after the
            // lastModified timestamp that itemService.update() will set.
            itemService.clearMetadata(context, item, "epfl", "citation", "date", Item.ANY);
            itemService.addMetadata(context, item, "epfl", "citation", "date", null,
                    Instant.now().plusSeconds(dateOffsetMinutes * 60L).toString());
            itemService.update(context, item);
        } catch (org.dspace.authorize.AuthorizeException e) {
            throw new RuntimeException("Authorization error saving citation metadata for item " + item.getID(), e);
        }
    }
}
