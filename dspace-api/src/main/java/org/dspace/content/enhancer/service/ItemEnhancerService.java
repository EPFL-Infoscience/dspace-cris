/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.enhancer.service;

import org.dspace.content.Item;
import org.dspace.core.Context;

/**
 * Service related to item enhancement.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public interface ItemEnhancerService {

    /**
     * Enhances the given item with all the item enhancers defined adding virtual
     * metadata fields on it. ItemEnhancer will use the information stored in the
     * source metadata to decide if virtual metadata must be calculated. This could
     * lead to stale information if the given item is linked to the same related items
     * than before but in the mean time the related items have been changed in a way
     * that could affect the generated virtual metadata (for example a publication
     * listing 3 authors assuming that we are flatting on the publication the information
     * about the author current affiliation would not update the virtual affiliation
     * if this method is invoked on the item without touching the author list - in this
     * scenario you need to use the {@link #forceEnhancement(Context, Item)} method
     *
     * @param context the DSpace Context
     * @param item    the item to enhance
     */
    void enhance(Context context, Item item);

    /**
     * Remove all the already calculated virtual metadata fields from the given item
     * and perform a new enhancement. This remove the risk of stale data related to
     * unforeseen update on the related items
     *
     * @param context the DSpace Context
     * @param item    the item to enhance
     */
    void forceEnhancement(Context context, Item item);
}
