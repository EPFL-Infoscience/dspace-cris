/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.builder;

import org.dspace.app.dataquality.utils.service.AbstractDedupUtilsAddon;
import org.dspace.dataquality.service.AbstractDeduplicationServiceAddon;
import org.dspace.deduplication.factory.DeduplicationServiceFactory;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public interface AbstractDeduplicationBuilder {

    default AbstractDeduplicationServiceAddon getDeduplicationService() {
        return DeduplicationServiceFactory.getInstance().getDeduplicationService();
    }

    default AbstractDedupUtilsAddon getDedupUtils() {
        return DeduplicationServiceFactory.getInstance().getDedupUtilsAddon();
    }
}
