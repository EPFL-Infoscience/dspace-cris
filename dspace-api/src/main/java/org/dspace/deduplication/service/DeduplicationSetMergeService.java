/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.deduplication.service;


import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

import org.dspace.app.deduplication.model.DeduplicationMerge;
import org.dspace.app.deduplication.model.DeduplicationSetMerge;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.deduplication.dto.DeduplicationSetMergeDTO;
import org.dspace.discovery.SearchServiceException;

/**
 * Service interface class for the {@link DeduplicationSetMerge} object.
 * The implementation of this class is responsible for all business logic calls for
 * the {@link DeduplicationSetMerge} object.
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 *
 */
public interface DeduplicationSetMergeService {

    /**
     * Create a new DeduplicationSetMerge object
     *
     * @param context The relevant DSpace Context.
     * @param targetUUID The UUID of target Item.
     * @param deduplicationSetMergeDTO the object that contains data about merging.
     * @return the created DeduplicationSetMerge object
     * @throws java.sql.SQLException An exception that provides information on a database
     *                      access error or other errors.
     * @throws AuthorizeException if there is an authorization problem with permissions.
     * @throws SearchServiceException if search error.
     * @throws IOException if IO error.
     */
    public DeduplicationSetMerge merge(Context context, UUID targetUUID,
                                       DeduplicationSetMergeDTO deduplicationSetMergeDTO)
        throws SQLException, AuthorizeException, SearchServiceException, IOException;

    /**
     * merge data from target Item and merged Items
     *
     * @param context The relevant DSpace Context.
     * @param deduplicationMerge the object that contains data about merging.
     * @throws java.sql.SQLException An exception that provides information on a database
     *                      access error or other errors.
     * @throws AuthorizeException if there is an authorization problem with permissions.
     * @throws SearchServiceException if search error.
     * @throws IOException if IO error.
     */
    public void merge(Context context, DeduplicationMerge deduplicationMerge)
        throws SQLException, AuthorizeException, SearchServiceException, IOException;
}
