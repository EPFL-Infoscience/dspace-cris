/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.dataquality.service.impl;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.Logger;
import org.dspace.core.Context;
import org.dspace.dataquality.service.DeduplicationServiceAddonComposition;
import org.dspace.deduplication.Deduplication;
import org.dspace.deduplication.dao.DeduplicationDAO;
import org.springframework.beans.factory.annotation.Autowired;

public class DeduplicationServiceAddonCompositionImpl implements DeduplicationServiceAddonComposition {

    /**
     * log4j logger
     */
    private final Logger log =
        org.apache.logging.log4j.LogManager.getLogger(DeduplicationServiceAddonCompositionImpl.class);

    @Autowired
    protected DeduplicationDAO deduplicationDAO;

    protected DeduplicationServiceAddonCompositionImpl() {
        super();
    }

    @Override
    public List<Deduplication> getDeduplicationByFirstAndSecond(Context context, UUID firstId, UUID secondId)
        throws SQLException {
        return deduplicationDAO.findByFirstAndSecond(context, firstId, secondId);
    }

    @Override
    public Deduplication uniqueDeduplicationByFirstAndSecond(Context context, UUID firstId, UUID secondId)
        throws SQLException {
        return deduplicationDAO.uniqueByFirstAndSecond(context, firstId, secondId);
    }

    @Override
    public void delete(Context context, Deduplication deduplication)
        throws SQLException {
        deduplicationDAO.delete(context, deduplication);
    }
}
