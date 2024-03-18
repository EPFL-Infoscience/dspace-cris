/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.dataquality.service;

import org.dspace.dataquality.adapter.factory.AddonFactory;
import org.dspace.deduplication.service.DeduplicationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DeduplicationServiceAddonFactory extends AddonFactory<AbstractDeduplicationServiceAddon> {

    @Autowired
    private DeduplicationService deduplicationService;
    @Autowired
    private DeduplicationServiceAddonComposition deduplicationServiceAddonComposition;

    @Override
    public AbstractDeduplicationServiceAddon createAdapter() {
        return createAdapter(deduplicationServiceAddonComposition, deduplicationService);
    }


}
