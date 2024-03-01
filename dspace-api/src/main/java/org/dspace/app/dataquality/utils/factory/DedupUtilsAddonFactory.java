/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.dataquality.utils.factory;

import org.dspace.app.dataquality.utils.service.AbstractDedupUtilsAddon;
import org.dspace.app.dataquality.utils.service.IDedupUtilsAddonComposition;
import org.dspace.dataquality.adapter.factory.AddonFactory;
import org.dspace.app.deduplication.utils.IDedupUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DedupUtilsAddonFactory extends AddonFactory<AbstractDedupUtilsAddon> {

    @Autowired
    IDedupUtilsAddonComposition dedupUtilsAddonComposition;

    @Autowired
    IDedupUtils dedupUtils;

    @Override
    public AbstractDedupUtilsAddon createAdapter() {
        return this.createAdapter(dedupUtilsAddonComposition, dedupUtils);
    }
}
