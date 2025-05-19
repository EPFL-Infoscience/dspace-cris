/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.security.service;

import java.util.List;

import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.Context;

/**
 * A service to manage a cache of allowed metadata in the current request
 *
 * @author Andrea Bollini (4science.com)
 */
public interface MetadataSecurityCacheService {
    /**
     * Invalidate the current request cache if any, it must be triggered by changes affecting the layout structure
     * or the public metadata
     */
    public void invalidateRequestCache();

    /**
     * Return the allowed metadata value according to the provided parameters if
     * available in the cache, null otherwise
     * 
     * @param context
     * @param item
     * @param preventBoxSecurityCheck true if the reduced metadata list based on
     *                                fast to check public metadata only should be
     *                                returned
     * @return the allowed metadatavalue in the cache if available, null otherwise
     */
    public List<MetadataValue> getCache(Context context, Item item,
            boolean preventBoxSecurityCheck);

    /**
     * Store a list of metadata value as allowed metadata values according to the
     * other parameters
     * 
     * @param context
     * @param item
     * @param preventBoxSecurityCheck
     * @param valuesToCache
     */
    public void storeCache(Context context, Item item, boolean preventBoxSecurityCheck,
            List<MetadataValue> valuesToCache);
}