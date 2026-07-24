/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.citation;

import java.sql.SQLException;
import java.util.Iterator;
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
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.indexobject.IndexableCollection;
import org.dspace.discovery.indexobject.IndexableCommunity;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.scripts.DSpaceRunnable;
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

    private Context context;
    private String index;
    private boolean force;
    private int commitSize;

    private ItemService itemService;
    private CommunityService communityService;
    private CollectionService collectionService;
    private SearchService searchService;
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
        searchService = new DSpace().getSingletonService(SearchService.class);
        citationService = new DSpace().getSingletonService(CitationServiceImpl.class);
    }

    @Override
    public void internalRun() throws Exception {
        try {
            context.turnOffAuthorisationSystem();
            handler.logInfo("Citation metadata script started");
            handler.logInfo("Parameters: index=" + index + ", force=" + force + ", commitSize=" + commitSize);

            IndexableObject<?, ?> scopeObject = resolveScope();
            Iterator<Item> items = findItems(scopeObject);

            int processed = 0;
            while (items.hasNext()) {
                Item item = items.next();
                if (item == null || !item.isArchived()) {
                    continue;
                }

                if (!force && !needsUpdate(item)) {
                    continue;
                }

                // Save placeholder citation metadata (will be replaced with real generation later)
                saveCitationMetadata(item);
                processed++;

                if (processed % commitSize == 0) {
                    context.commit();
                    handler.logInfo("Committed after " + processed + " items");
                }
            }

            if (processed % commitSize != 0) {
                context.commit();
            }

            handler.logInfo("Citation metadata script completed. Total items processed: " + processed);
            context.restoreAuthSystemState();
        } finally {
            context.complete();
        }
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
     * Finds items using a direct Solr query with the appropriate filters.
     */
    private Iterator<Item> findItems(IndexableObject<?, ?> scopeObject) throws SearchServiceException {
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.setMaxResults(Integer.MAX_VALUE);
        discoverQuery.setQuery("search.resourcetype:Item"
                + " AND (entityType_keyword:Publication OR entityType_keyword:Product OR entityType_keyword:Patent)"
                + " AND -withdrawn:true AND -discoverable:false AND latestVersion:true");

        // When not forcing, exclude items that have never been modified since their citation was generated.
        // Items without epfl.citation.date are always included (they need generation).
        // Items WITH epfl.citation.date are also included — the needsUpdate check in Java will filter them.
        // We only optimize by excluding items that definitely don't need update:
        // those without citation.date are the primary target, but we include all to catch modified ones too.

        return searchService.iteratorSearch(context, scopeObject, discoverQuery);
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
            java.time.Instant citationInstant = java.time.Instant.parse(citationDateStr);
            java.util.Date lastModified = item.getLastModified();
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
     */
    private void saveCitationMetadata(Item item) throws SQLException {
        Map<String, String> citations = citationService.generateAllCitations(context, item);
        try {
            for (Map.Entry<String, String> entry : citations.entrySet()) {
                String qualifier = entry.getKey();
                String value = entry.getValue();
                if (StringUtils.isNotBlank(value)) {
                    itemService.clearMetadata(context, item, "epfl", "citation", qualifier, Item.ANY);
                    itemService.addMetadata(context, item, "epfl", "citation", qualifier, null, value);
                }
            }

            // Set citation date to current time, then update the item.
            // The update will set lastModified to the same instant (or very close),
            // so needsUpdate will return false until the item is genuinely modified again.
            itemService.clearMetadata(context, item, "epfl", "citation", "date", Item.ANY);
            int intervalMinutes = org.dspace.services.factory.DSpaceServicesFactory.getInstance()
                    .getConfigurationService().getIntProperty("citation-script.interval", 30);
            itemService.addMetadata(context, item, "epfl", "citation", "date", null,
                    java.time.Instant.now().plusSeconds(intervalMinutes * 60L).toString());
            itemService.update(context, item);
        } catch (org.dspace.authorize.AuthorizeException e) {
            throw new RuntimeException("Authorization error saving citation metadata for item " + item.getID(), e);
        }
    }
}
