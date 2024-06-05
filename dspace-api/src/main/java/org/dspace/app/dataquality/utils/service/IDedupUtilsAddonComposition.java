/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.dataquality.utils.service;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.dspace.app.deduplication.utils.DeduplicationSignature;
import org.dspace.app.deduplication.utils.DuplicateInfo;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.deduplication.Deduplication;
import org.dspace.discovery.SearchServiceException;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public interface IDedupUtilsAddonComposition {

    List<DeduplicationSignature> findAllSignatures() throws SearchServiceException;

    List<DuplicateInfo> findAllGroups(Context context)
        throws SearchServiceException, SQLException;

    List<DuplicateInfo> findAllGroups(Context context, String signatureId)
        throws SearchServiceException, SQLException;

    List<DuplicateInfo> findAllGroups(Context context, String signatureId, String sRule)
        throws SearchServiceException, SQLException;

    DuplicateInfo findGroup(Context context, String id)
        throws SearchServiceException, SQLException;

    boolean rejectAdminDups(Context context, UUID firstId, UUID secondId, Integer type) throws SQLException,
        AuthorizeException;

    boolean rejectAdminDups(Context context, UUID itemID, String signatureType, int resourceType)
        throws SQLException, AuthorizeException, SearchServiceException;

    boolean rejectAdminDups(Context context, DuplicateInfo dsi, int type)
        throws SearchServiceException, SQLException, AuthorizeException;

    boolean rejectAdminDups(Context context, DuplicateInfo dsi, UUID itemID, int type)
        throws SearchServiceException, SQLException, AuthorizeException;

    void verifyOrRejectDups(Context context, Deduplication deduplication, String action, boolean check)
        throws SQLException, AuthorizeException;

    void rejectAdminDups(Context context, List<DSpaceObject> items, String signatureID)
        throws SQLException, AuthorizeException, SearchServiceException;

    void verify(Context context, Deduplication deduplication, boolean check) throws SQLException, AuthorizeException;

    void verify(Context context, int dedupId, UUID firstId, UUID secondId, int type, boolean toFix, String note,
                boolean check) throws SQLException, AuthorizeException;

    boolean rejectDups(Context context, UUID firstId, UUID secondId, Integer type, boolean notDupl, String note,
                       boolean check);

    boolean rejectDups(Context context, Deduplication deduplication, boolean check);

    DeduplicationSignature findSignature(String id) throws SearchServiceException;
}
