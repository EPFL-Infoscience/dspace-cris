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
import org.dspace.discovery.DiscoverResultItemIterator;
import org.dspace.discovery.IndexableObject;
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
            Iterator<Item> items = findItems(scopeObject);

            int processed = 0;
            boolean needsReload = false;

            while (items.hasNext()) {
                Item item = items.next();
                if (item == null || !item.isArchived()) {
                    continue;
                }

                // After a commit, items from the iterator may be detached — reload from DB
                if (needsReload) {
                    item = itemService.find(context, item.getID());
                    if (item == null || !item.isArchived()) {
                        continue;
                    }
                }

                if (!force && !needsUpdate(item)) {
                    handler.logInfo("Skipped item " + item.getID() + " (no update needed)");
                    context.uncacheEntity(item);
                    continue;
                }

                saveCitationMetadata(item);
                context.uncacheEntity(item);
                processed++;

                if (processed % commitSize == 0) {
                    context.commit();
                    needsReload = true;
                    handler.logInfo("Committed after " + processed + " items");
                }
            }

            if (processed % commitSize != 0) {
                context.commit();
            }

            handler.logInfo("Citation metadata script completed. Total items processed: " + processed);
            context.restoreAuthSystemState();
            context.complete();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
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
     * Finds items using a paginated Solr query with the appropriate filters.
     * Uses {@link DiscoverResultItemIterator} which handles pagination and entity uncaching automatically.
     *
     * When not forcing, the Solr query only returns:
     * - Items that have never been processed (no epfl.citation.date)
     * - Items modified recently (lastModified within the configured interval)
     *
     * This avoids loading hundreds of thousands of unchanged items into memory.
     */
    private Iterator<Item> findItems(IndexableObject<?, ?> scopeObject) {
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.setMaxResults(commitSize);
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
                "(-epfl.citation.date:*) OR lastModified:[NOW-" + checkIntervalHours + "HOURS TO NOW]"
            );
        }

        return new DiscoverResultItemIterator(context, scopeObject, discoverQuery);
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
