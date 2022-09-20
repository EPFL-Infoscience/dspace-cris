/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.deduplication.factory;

import org.dspace.deduplication.service.DeduplicationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Factory implementation to get services for the deduplication package, use DeduplicationServiceFactory.getInstance()
 * to retrieve an implementation
 *
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */
public class DeduplicationServiceFactoryImpl extends DeduplicationServiceFactory {

    @Autowired
    private DeduplicationService deduplicationService;

    @Override
    public DeduplicationService getDeduplicationService() {
        return deduplicationService;
    }
}
