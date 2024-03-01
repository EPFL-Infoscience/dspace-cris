/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.dataquality.service;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.dspace.core.Context;
import org.dspace.deduplication.Deduplication;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public interface DeduplicationServiceAddonComposition {
    List<Deduplication> getDeduplicationByFirstAndSecond(Context context, UUID firstId, UUID secondId)
        throws SQLException;

    Deduplication uniqueDeduplicationByFirstAndSecond(Context context, UUID firstId, UUID secondId)
        throws SQLException;

    void delete(Context context, Deduplication deduplication)
        throws SQLException;
}
