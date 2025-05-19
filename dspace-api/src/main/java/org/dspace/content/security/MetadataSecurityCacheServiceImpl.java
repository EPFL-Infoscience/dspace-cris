/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.security.service.MetadataSecurityCacheService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.services.RequestService;
import org.dspace.services.model.Request;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A service to manage a cache of allowed metadata in the current request
 *
 * @author Andrea Bollini (4science.com)
 */
public class MetadataSecurityCacheServiceImpl implements MetadataSecurityCacheService {

    /** 
     * The name of the request attribute holding the eperson for which the cache was populated
     */
    private static final String SECURITY_METADATA_CACHE_EPERSON = "securityMetadataCache.eperson";

    /**
     * The name of the request attribute holding the cache with the metadata that
     * are allowed to the user considering the box
     */
    private static final String SECURITY_METADATA_CACHE = "securityMetadataCache";

    /**
     * The name of the request attribute holding the cache with the metadata that
     * are allowed to the user WITHOUT considering the box
     */
    private static final String SECURITY_METADATA_CACHE_PREVENT_BOX_SECURITY_CHECK =
            "securityMetadataCache.preventBoxSecurityCheck";

    /**
     * The maximum amount of items to keep in the request cache
     */
    private static final int SECURITY_METADATA_CACHE_MAX_ITEMS = 3;

    @Autowired
    private RequestService requestService;

    @Override
    public void invalidateRequestCache() {
        Request currentRequest = requestService.getCurrentRequest();
        // this is almost always true but could be null in thread started manually with a scheduler, etc.
        if (currentRequest != null) {
            currentRequest.setAttribute(SECURITY_METADATA_CACHE, null);
            currentRequest.setAttribute(
                    SECURITY_METADATA_CACHE_PREVENT_BOX_SECURITY_CHECK, null);
            currentRequest.setAttribute(SECURITY_METADATA_CACHE_EPERSON, null);
        }
    }

    @Override
    public List<MetadataValue> getCache(Context context, Item item,
            boolean preventBoxSecurityCheck) {
        Request currentRequest = requestService.getCurrentRequest();
        Map<String, List<MetadataValue>> cache = null;
        // this is almost always true but could be null in thread started manually with a scheduler, etc.
        if (currentRequest != null) {
            final String cacheName = preventBoxSecurityCheck ? SECURITY_METADATA_CACHE_PREVENT_BOX_SECURITY_CHECK
                    : SECURITY_METADATA_CACHE;
            EPerson currUser = context != null ? context.getCurrentUser() : null;
            UUID currUserUUID = currUser != null ? currUser.getID() : null;
            UUID cacheUserUUID = (UUID) currentRequest.getAttribute(SECURITY_METADATA_CACHE_EPERSON);
            cache = (Map<String, List<MetadataValue>>) currentRequest
                    .getAttribute(cacheName);

            if (cache != null) {
                if (!Objects.equals(cacheUserUUID, currUserUUID)) {
                    // cache is invalid as it was generated for a different user
                    cache.clear();
                } else {
                    String cacheKey = getCacheKey(item);
                    if (cache.get(cacheKey) != null) {
                        return cache.get(cacheKey);
                    } else if (cache.size() > SECURITY_METADATA_CACHE_MAX_ITEMS) {
                        // we only want to cache in the current thread a maximum amount of items
                        // a single thread could check metadata of different items going back and
                        // forward among them when traversing a graph, i.e. during the export of
                        // an item fetching related items
                        cache.clear();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void storeCache(Context context, Item item,
            boolean preventBoxSecurityCheck, List<MetadataValue> valuesToCache) {
        Request currentRequest = requestService.getCurrentRequest();
        // this is almost always true but could be null in thread started manually with a scheduler, etc.
        if (currentRequest != null) {
            Map<String, List<MetadataValue>> cache = new HashMap<String, List<MetadataValue>>();
            final String cacheName = preventBoxSecurityCheck ? SECURITY_METADATA_CACHE_PREVENT_BOX_SECURITY_CHECK
                    : SECURITY_METADATA_CACHE;
            EPerson currUser = context != null ? context.getCurrentUser() : null;
            UUID currUserUUID = currUser != null ? currUser.getID() : null;
            cache.put(getCacheKey(item), valuesToCache);
            currentRequest.setAttribute(cacheName, cache);
            currentRequest.setAttribute(SECURITY_METADATA_CACHE_EPERSON, currUserUUID);
        }
    }

    private String getCacheKey(Item item) {
        String cacheKey = item.getID() + String.valueOf(item.getLastModified().getTime());
        return cacheKey;
    }

}