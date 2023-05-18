/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.deduplication.factory;

import org.dspace.deduplication.service.DeduplicationService;
import org.dspace.deduplication.service.DeduplicationSetMergeService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Abstract factory to get services for the deduplication package, use DeduplicationServiceFactory.getInstance()
 * to retrieve an implementation
 *
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */
public abstract class DeduplicationServiceFactory {

    public abstract DeduplicationService getDeduplicationService();
    public abstract DeduplicationSetMergeService getDeduplicationSetMergeService();

    public static DeduplicationServiceFactory getInstance() {
        return DSpaceServicesFactory.getInstance().getServiceManager()
            .getServiceByName("deduplicationServiceFactory", DeduplicationServiceFactory.class);
    }
}
