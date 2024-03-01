/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.dataquality.utils.service.impl;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.FacetParams;
import org.dspace.app.dataquality.utils.service.IDedupUtilsAddonComposition;
import org.dspace.app.deduplication.service.DedupService;
import org.dspace.app.deduplication.service.impl.SolrDedupServiceImpl;
import org.dspace.app.deduplication.service.impl.SolrDedupServiceImpl.DeduplicationFlag;
import org.dspace.app.deduplication.utils.DeduplicationSignature;
import org.dspace.app.deduplication.utils.DuplicateInfo;
import org.dspace.app.deduplication.utils.DuplicateSignatureInfo;
import org.dspace.app.deduplication.utils.Signature;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.deduplication.Deduplication;
import org.dspace.deduplication.service.DeduplicationService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DedupUtilsComposition implements IDedupUtilsAddonComposition {

    private static final Logger log = LoggerFactory.getLogger(DedupUtilsComposition.class);

    protected DedupService dedupService;

    @Autowired
    protected ConfigurationService configurationService;
    @Autowired
    protected DeduplicationService deduplicationService;


    public DeduplicationSignature findSignature(String id) throws SearchServiceException {
        Set<String> signatureTypes = retrieveAllSignatures();
        if (signatureTypes == null || signatureTypes.isEmpty() || !signatureTypes.contains(id)) {
            return null;
        }
        return buildSignature(id);
    }


    public List<DuplicateInfo> findAllGroups(Context context) throws SearchServiceException, SQLException {
        List<DuplicateInfo> results = new ArrayList<>();
        Set<String> signatureTypes = retrieveAllSignatures();
        if (signatureTypes != null && !signatureTypes.isEmpty()) {
            for (String signatureType : signatureTypes) {
                List<DuplicateInfo> duplicateInfos = findAllGroups(context, signatureType);
                if (duplicateInfos != null && !duplicateInfos.isEmpty()) {
                    results.addAll(duplicateInfos);
                }
            }
        }
        return results;
    }


    public List<DuplicateInfo> findAllGroups(Context context, String signatureId)
        throws SearchServiceException, SQLException {
        return findAllGroups(context, signatureId, "");

    }


    public List<DuplicateInfo> findAllGroups(Context context, String signatureId, String sRule)
        throws SearchServiceException, SQLException {
        Integer rule = -1;
        switch (sRule) {
            case "submitter":
                rule = 1;
                break;
            case "reviewer":
                rule = 2;
                break;
            default:
                rule = -1;
                break;
        }

        List<DuplicateInfo> results = new ArrayList<>();
        List<DuplicateInfo> duplicateInfos = findSignatureWithDuplicates(context, signatureId, null,
                                                                         Constants.ITEM, 0, Integer.MAX_VALUE, rule);
        if (duplicateInfos != null && !duplicateInfos.isEmpty()) {
            List<String> managedGroups = new ArrayList<>();
            for (DuplicateInfo duplicateInfo : duplicateInfos) {
                boolean found = false;
                for (String relatedGroup : duplicateInfo.getOtherGroupIds()) {
                    if (managedGroups.contains(relatedGroup)) {
                        found = true;
                        break;
                    }
                }
                if (!found && !managedGroups.contains(duplicateInfo.getGroupChecksum())) {
                    managedGroups.add(duplicateInfo.getGroupChecksum());
                    results.add(duplicateInfo);
                }
            }
        }
        return results;

    }


    public DuplicateInfo findGroup(Context context, String id)
        throws SearchServiceException, SQLException {
        if (StringUtils.isBlank(id) || !StringUtils.contains(id, ":")) {
            return null;
        }

        String signatureId = id.split(":")[0];
        String groupChecksum = id.split(":")[1];
        List<DuplicateInfo> duplicateInfos = findSignatureWithDuplicates(context, signatureId,
                                                                         groupChecksum, Constants.ITEM, 0,
                                                                         Integer.MAX_VALUE, -1);

        if (duplicateInfos != null && !duplicateInfos.isEmpty()) {
            return duplicateInfos.get(0);
        }

        return null;
    }

    public int countSignatureWithDuplicates(String query, int resourceTypeId, String signatureType)
        throws SearchServiceException {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        solrQuery.setRows(0);
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        solrQuery.addFacetField(SolrDedupServiceImpl.RESOURCE_SIGNATURETYPE_FIELD);
        solrQuery.addFilterQuery(SolrDedupServiceImpl.RESOURCE_FLAG_FIELD + ":"
                                     + SolrDedupServiceImpl.DeduplicationFlag.MATCH.getDescription());
        solrQuery.addFilterQuery(SolrDedupServiceImpl.RESOURCE_RESOURCETYPE_FIELD + ":" + resourceTypeId);
        solrQuery.addFilterQuery(SolrDedupServiceImpl.RESOURCE_SIGNATURETYPE_FIELD + ":" + signatureType);
        if (configurationService.getBooleanProperty("deduplication.tool.duplicatechecker.ignorewithdrawn")) {
            solrQuery.addFilterQuery("-" + SolrDedupServiceImpl.RESOURCE_WITHDRAWN_FIELD + ":true");
        }

        QueryResponse response = getDedupService().search(solrQuery);
        FacetField facetField = response.getFacetField(SolrDedupServiceImpl.RESOURCE_SIGNATURETYPE_FIELD);
        if (facetField != null) {
            for (Count count : facetField.getValues()) {
                solrQuery = new SolrQuery();
                solrQuery.setQuery(query);
                solrQuery.setRows(0);
                solrQuery.setFacet(true);
                solrQuery.setFacetMinCount(1);
                solrQuery.addFacetField(count.getName());
                solrQuery.addFilterQuery(SolrDedupServiceImpl.RESOURCE_FLAG_FIELD + ":"
                                             + SolrDedupServiceImpl.DeduplicationFlag.MATCH.getDescription());
                if (configurationService.getBooleanProperty("deduplication.tool.duplicatechecker.ignorewithdrawn")) {
                    solrQuery.addFilterQuery("-" + SolrDedupServiceImpl.RESOURCE_WITHDRAWN_FIELD + ":true");
                }
                solrQuery.addFilterQuery(count.getAsFilterQuery());
                response = getDedupService().search(solrQuery);

                FacetField facetField2 = response.getFacetField(count.getName());
                return facetField2.getValueCount();
            }
        }

        return 0;
    }

    protected DuplicateSignatureInfo findPotentialMatchByID(Context context, String signatureType, int resourceType,
                                                          UUID itemID) throws SearchServiceException, SQLException {
        return findPotentialMatchByID(context, signatureType, null, resourceType, itemID);
    }

    protected DuplicateSignatureInfo findPotentialMatchByID(Context context, String signatureType,
                                                            String groupChecksum, int resourceType, UUID itemID)
        throws SearchServiceException, SQLException {
        if (StringUtils.isNotEmpty(signatureType)) {
            if (!StringUtils.contains(signatureType, "_signature")) {
                signatureType += "_signature";
            }
        }
        SolrQuery solrQuery = new SolrQuery();

        if (itemID != null) {
            solrQuery.setQuery(SolrDedupServiceImpl.RESOURCE_IDS_FIELD + ":" + itemID);
        }

        solrQuery.addFilterQuery(SolrDedupServiceImpl.RESOURCE_SIGNATURETYPE_FIELD + ":" + signatureType);
        solrQuery.addFilterQuery(SolrDedupServiceImpl.RESOURCE_RESOURCETYPE_FIELD + ":" + resourceType);
        solrQuery.addFilterQuery(SolrDedupServiceImpl.RESOURCE_FLAG_FIELD + ":"
                                     + DeduplicationFlag.MATCH.getDescription());

        if (StringUtils.isNotBlank(groupChecksum)) {
            solrQuery.addFilterQuery(SolrDedupServiceImpl.RESOURCE_SIGNATURE_FIELD + ":" + groupChecksum);
        }

        QueryResponse response = getDedupService().search(solrQuery);

        SolrDocumentList solrDocumentList = response.getResults();

        DuplicateSignatureInfo dsi = new DuplicateSignatureInfo(signatureType);
        for (SolrDocument solrDocument : solrDocumentList) {

            String signatureTypeString = (String) ((List) (solrDocument.getFieldValue(signatureType))).get(0);

            dsi.setGroupChecksum(signatureTypeString);

            List<String> ids = (List<String>) solrDocument.getFieldValue(SolrDedupServiceImpl.RESOURCE_IDS_FIELD);

            for (String obj : ids) {
                Item item = ContentServiceFactory.getInstance().getItemService().find(context, UUID.fromString(obj));
                if (!(dsi.getItems().contains(item))) {
                    dsi.getItems().add(item);
                }
            }
        }

        return dsi;
    }

    @Override
    public List<DeduplicationSignature> findAllSignatures() throws SearchServiceException {
        Set<String> signatureTypes = retrieveAllSignatures();
        if (signatureTypes == null || signatureTypes.isEmpty()) {
            return null;
        }

        List<DeduplicationSignature> signatures = new ArrayList<>();
        for (String signatureType : signatureTypes) {
            signatures.add(buildSignature(signatureType));
        }
        return signatures;
    }

    protected DeduplicationSignature buildSignature(String id) throws SearchServiceException {
        String signatureType = id + "_signature";
        DeduplicationSignature signature = new DeduplicationSignature();
        signature.setId(id);
        signature.setSignatureType(id);
        signature.setGroupReviewerCheck(
            countSignatureWithDuplicates(SolrDedupServiceImpl.SUBQUERY_NOT_IN_REJECTED_OR_VERIFYWF,
                                         Constants.ITEM, signatureType));
        signature.setGroupSubmitterCheck(
            countSignatureWithDuplicates(SolrDedupServiceImpl.SUBQUERY_NOT_IN_REJECTED_OR_VERIFY,
                                         Constants.ITEM, signatureType));
        signature.setGroupAdminstratorCheck(
            countSignatureWithDuplicates(SolrDedupServiceImpl.SUBQUERY_NOT_IN_REJECTED,
                                         Constants.ITEM, signatureType));
        return signature;
    }


    protected Set<String> retrieveAllSignatures() {
        List<Signature> signatures = new DSpace().getServiceManager().getServicesByType(Signature.class);
        if (signatures == null || signatures.isEmpty()) {
            return null;
        }

        Set<String> signatureTypes = new HashSet<>();
        for (Signature signature : signatures) {
            signatureTypes.add(signature.getSignatureType());
        }
        return signatureTypes;
    }

    public List<DuplicateInfo> findSuggestedDuplicate(Context context, int resourceType, int start, int rows)
        throws SearchServiceException, SQLException {

        SolrQuery solrQueryInternal = new SolrQuery();

        solrQueryInternal.setQuery(SolrDedupServiceImpl.SUBQUERY_NOT_IN_REJECTED);

        solrQueryInternal.addFilterQuery(SolrDedupServiceImpl.RESOURCE_RESOURCETYPE_FIELD + ":" + resourceType);
        boolean ignoreSubmitterSuggestion = configurationService.getBooleanProperty(
            "deduplication.tool.duplicatechecker.ignore.submitter.suggestion", true);
        if (ignoreSubmitterSuggestion) {
            solrQueryInternal.addFilterQuery(SolrDedupServiceImpl.RESOURCE_FLAG_FIELD + ":"
                                                 + DeduplicationFlag.VERIFYWF.getDescription());
        } else {
            solrQueryInternal.addFilterQuery(SolrDedupServiceImpl.RESOURCE_FLAG_FIELD + ":verify*");
        }

        QueryResponse response = getDedupService().search(solrQueryInternal);

        SolrDocumentList solrDocumentList = response.getResults();

        List<DuplicateInfo> result = new ArrayList<DuplicateInfo>();

        int index = 0;

        for (SolrDocument solrDocument : solrDocumentList) {
            if (index >= start + rows) {
                break;
            }
            DuplicateSignatureInfo dsi = new DuplicateSignatureInfo("suggested",
                                                                    (String) solrDocument.getFirstValue("_version_"));

            List<String> ids = (List<String>) solrDocument.getFieldValue(SolrDedupServiceImpl.RESOURCE_IDS_FIELD);

            for (String obj : ids) {
                Item item = ContentServiceFactory.getInstance().getItemService().find(context, UUID.fromString(obj));
                if (item != null) {
                    if (!(dsi.getItems().contains(item))) {
                        dsi.getItems().add(item);
                    }
                }
            }
            result.add(dsi);
            index++;
        }

        return result;
    }

    public List<DuplicateInfo> findSignatureWithDuplicates(Context context, String signatureId, String groupChecksum,
                                                           int resourceType, int limit, int offset, int rule)
        throws SearchServiceException, SQLException {
        return findPotentialMatch(context, signatureId, groupChecksum, resourceType, limit, offset, rule);
    }

    private List<DuplicateInfo> findPotentialMatch(Context context, String signatureId,
                                                   String groupChecksum, int resourceType, int start, int rows,
                                                   int rule)
        throws SearchServiceException, SQLException {

        String signatureType = signatureId + "_signature";
        SolrQuery solrQueryExternal = new SolrQuery();

        solrQueryExternal.setRows(0);

        String subqueryNotInRejected = null;

        switch (rule) {
            case 1:
                subqueryNotInRejected = SolrDedupServiceImpl.SUBQUERY_NOT_IN_REJECTED_OR_VERIFY;
                break;
            case 2:
                subqueryNotInRejected = SolrDedupServiceImpl.SUBQUERY_NOT_IN_REJECTED_OR_VERIFYWF;
                break;
            default:
                subqueryNotInRejected = SolrDedupServiceImpl.SUBQUERY_NOT_IN_REJECTED;
                break;
        }

        solrQueryExternal.setQuery(subqueryNotInRejected);

        solrQueryExternal.addFilterQuery(SolrDedupServiceImpl.RESOURCE_SIGNATURETYPE_FIELD + ":" + signatureType);

        solrQueryExternal.addFilterQuery(SolrDedupServiceImpl.RESOURCE_RESOURCETYPE_FIELD + ":" + resourceType);
        solrQueryExternal.addFilterQuery(SolrDedupServiceImpl.RESOURCE_FLAG_FIELD + ":"
                                             + DeduplicationFlag.MATCH.getDescription());
        if (configurationService.getBooleanProperty("deduplication.tool.duplicatechecker.ignorewithdrawn")) {
            solrQueryExternal.addFilterQuery("-" + SolrDedupServiceImpl.RESOURCE_WITHDRAWN_FIELD + ":true");
        }
        if (StringUtils.isNotBlank(groupChecksum)) {
            solrQueryExternal.addFilterQuery(signatureType + ":" + groupChecksum);
        }
        solrQueryExternal.setFacet(true);
        solrQueryExternal.setFacetMinCount(1);
        solrQueryExternal.addFacetField(signatureType);
        solrQueryExternal.setFacetSort(FacetParams.FACET_SORT_COUNT);

        QueryResponse responseFacet = getDedupService().search(solrQueryExternal);

        FacetField facetField = responseFacet.getFacetField(signatureType);

        List<DuplicateInfo> result = new ArrayList<DuplicateInfo>();

        int index = 0;
        for (Count facetHit : facetField.getValues()) {
            if (index >= start + rows) {
                break;
            }
            if (index >= start) {
                String name = facetHit.getName();

                SolrQuery solrQueryInternal = new SolrQuery();

                solrQueryInternal.setQuery(subqueryNotInRejected);

                solrQueryInternal
                    .addFilterQuery(SolrDedupServiceImpl.RESOURCE_SIGNATURETYPE_FIELD + ":" + signatureType);
                solrQueryInternal.addFilterQuery(facetHit.getAsFilterQuery());
                solrQueryInternal.addFilterQuery(SolrDedupServiceImpl.RESOURCE_RESOURCETYPE_FIELD + ":" + resourceType);
                solrQueryInternal.setRows(Integer.MAX_VALUE);
                solrQueryInternal.addFilterQuery(SolrDedupServiceImpl.RESOURCE_FLAG_FIELD + ":"
                                                     + DeduplicationFlag.MATCH.getDescription());
                if (configurationService.getBooleanProperty("deduplication.tool.duplicatechecker.ignorewithdrawn")) {
                    solrQueryInternal.addFilterQuery("-" + SolrDedupServiceImpl.RESOURCE_WITHDRAWN_FIELD + ":true");
                }
                QueryResponse response = getDedupService().search(solrQueryInternal);

                SolrDocumentList solrDocumentList = response.getResults();

                DuplicateSignatureInfo dsi = new DuplicateSignatureInfo(signatureId, name);

                for (SolrDocument solrDocument : solrDocumentList) {

                    List<String> signatureTypeList = (List<String>) (solrDocument.getFieldValue(signatureType));
                    for (String signatureTypeString : signatureTypeList) {
                        if (name.equals(signatureTypeString)) {

                            dsi.setGroupChecksum(signatureTypeString);
                            List<String> ids = (List<String>) solrDocument
                                .getFieldValue(SolrDedupServiceImpl.RESOURCE_IDS_FIELD);

                            for (String obj : ids) {
                                Item item = ContentServiceFactory.getInstance().getItemService().find(context,
                                                                                                      UUID.fromString(
                                                                                                          obj));
                                if (item != null) {
                                    if (!(dsi.getItems().contains(item))) {
                                        dsi.getItems().add(item);
                                    }
                                }
                            }

                            result.add(dsi);
                        } else {
                            dsi.getOtherGroupIds().add(dsi.getSignatureId() + ":" + signatureTypeString);
                        }
                    }
                }
            }
            index++;
        }

        return result;
    }

    public boolean rejectAdminDups(Context context, UUID itemID, String signatureType, int resourceType)
        throws SQLException, AuthorizeException, SearchServiceException {
        DuplicateSignatureInfo dsi = findPotentialMatchByID(context, signatureType, resourceType, itemID);
        return rejectAdminDups(context, dsi, itemID, resourceType);
    }


    public boolean rejectAdminDups(Context context, DuplicateInfo dsi, int type)
        throws SearchServiceException, SQLException, AuthorizeException {
        if (dsi.getNumItems() > 1) {
            for (DSpaceObject item1 : dsi.getItems()) {
                for (DSpaceObject item2 : dsi.getItems()) {
                    if (item1 != null && item2 != null && item1.getID() != item2.getID()) {
                        rejectAdminDups(context, item1.getID(), item2.getID(), type);
                    }
                }
            }
        }
        return true;
    }

    public boolean rejectAdminDups(Context context, DuplicateInfo dsi, UUID itemID, int type)
        throws SearchServiceException, SQLException, AuthorizeException {
        boolean found = false;
        for (DSpaceObject item : dsi.getItems()) {
            if (item != null) {
                if (item.getID().equals(itemID)) {
                    found = true;
                    break;
                }
            }
        }

        if (found && dsi.getNumItems() > 1) {
            for (DSpaceObject item : dsi.getItems()) {
                if (item != null) {
                    if (!item.getID().equals(itemID)) {
                        rejectAdminDups(context, itemID, item.getID(), type);
                    }
                }
            }
        }
        return true;
    }


    @Override
    public boolean rejectAdminDups(Context context, UUID firstId, UUID secondId, Integer type)
        throws SQLException, AuthorizeException {
        if (firstId == secondId) {
            return false;
        }
        if (!AuthorizeServiceFactory.getInstance().getAuthorizeService().isAdmin(context)) {
            throw new AuthorizeException(
                "Only the administrator can reject the duplicate in the administrative section");
        }
        UUID[] sortedIds = new UUID[] {firstId, secondId};
        Arrays.sort(sortedIds);

        Deduplication row = null;
        try {

            row = deduplicationService.uniqueDeduplicationByFirstAndSecond(context, sortedIds[0], sortedIds[1]);
            if (row != null) {
                row.setAdminId(context.getCurrentUser().getID());
                row.setAdminTime(new Date());
                row.setAdminDecision(DeduplicationFlag.REJECTADMIN.getDescription());

                deduplicationService.update(context, row);
            } else {
                row = new Deduplication();
                row.setAdminId(context.getCurrentUser().getID());
                row.setFirstItemId(sortedIds[0]);
                row.setSecondItemId(sortedIds[1]);
                row.setAdminTime(new Date());
                row.setAdminDecision(DeduplicationFlag.REJECTADMIN.getDescription());

                row = deduplicationService.create(context, row);
            }
            dedupService
                .buildDecision(context, firstId, secondId, DeduplicationFlag.REJECTADMIN,
                                     null);
            return true;
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
        return false;
    }

    public void verifyOrRejectDups(Context context, Deduplication deduplication, String action, boolean check)
        throws SQLException, AuthorizeException {
        if ("verify".equals(action)) {
            verify(context, deduplication, check);
        } else if ("reject".equals(action)) {
            rejectDups(context, deduplication, check);
        } else if ("adminreject".equals(action)) {
            rejectAdminDups(context, deduplication.getFirstItemId(), deduplication.getSecondItemId(), Constants.ITEM);
        }
    }


    public void verify(Context context, Deduplication deduplication, boolean check)
        throws SQLException, AuthorizeException {
        verify(context, deduplication.getDeduplicationId(), deduplication.getFirstItemId(),
               deduplication.getSecondItemId(), Constants.ITEM, deduplication.isTofix(),
               deduplication.getReaderNote(), check);

    }

    public boolean rejectDups(Context context, Deduplication deduplication, boolean check) {
        return rejectDups(context, deduplication.getFirstItemId(), deduplication.getSecondItemId(),
                          Constants.ITEM, deduplication.isFake(), deduplication.getNote(), check);
    }

    public void rejectAdminDups(Context context, List<DSpaceObject> items, String signatureID)
        throws SQLException, AuthorizeException, SearchServiceException {
        for (DSpaceObject item : items) {
            rejectAdminDups(context, item.getID(), signatureID, item.getType());
        }
    }


    public void verify(Context context, int dedupId, UUID firstId, UUID secondId, int type, boolean toFix, String note,
                       boolean check) throws SQLException, AuthorizeException {
        UUID[] sortedIds = new UUID[] {firstId, secondId};
        Arrays.sort(sortedIds);
        firstId = sortedIds[0];
        secondId = sortedIds[1];
        Item firstItem = ContentServiceFactory.getInstance().getItemService().find(context, firstId);
        Item secondItem = ContentServiceFactory.getInstance().getItemService().find(context, secondId);
        if (AuthorizeServiceFactory.getInstance().getAuthorizeService().authorizeActionBoolean(context, firstItem,
                                                                                               Constants.WRITE)
            || AuthorizeServiceFactory.getInstance().getAuthorizeService().authorizeActionBoolean(context,
                                                                                                  secondItem,
                                                                                                  Constants.WRITE)) {
            Deduplication row =
                deduplicationService.uniqueDeduplicationByFirstAndSecond(context, firstId, secondId);

            if (row != null) {
                String submitterDecision = row.getSubmitterDecision();
                if (check && StringUtils.isNotBlank(submitterDecision)) {
                    row.setSubmitterDecision(submitterDecision);
                }
            } else {
                row = deduplicationService.create(context, new Deduplication());
            }

            row.setFirstItemId(firstId);
            row.setSecondItemId(secondId);
            row.setTofix(toFix);
            row.setFake(false);
            row.setReaderNote(note);
            row.setReaderId(context.getCurrentUser().getID());
            row.setReaderTime(new Date());
            if (check) {
                row.setWorkflowDecision(DeduplicationFlag.VERIFYWF.getDescription());
            } else {
                row.setSubmitterDecision(DeduplicationFlag.VERIFYWS.getDescription());
            }

            deduplicationService.update(context, row);
            getDedupService().buildDecision(context, firstId, secondId,
                                       check ? DeduplicationFlag.VERIFYWF : DeduplicationFlag.VERIFYWS,
                                       note);
        } else {
            throw new AuthorizeException("Only authorize users can access to the deduplication");
        }
    }

    public boolean rejectDups(Context context, UUID firstId, UUID secondId, Integer type, boolean notDupl, String note,
                              boolean check) {
        UUID[] sortedIds = new UUID[] {firstId, secondId};
        Arrays.sort(sortedIds);
        Deduplication row = null;
        try {

            row = deduplicationService.uniqueDeduplicationByFirstAndSecond(context, sortedIds[0], sortedIds[1]);

            Item firstItem = ContentServiceFactory.getInstance().getItemService().find(context, firstId);
            Item secondItem = ContentServiceFactory.getInstance().getItemService().find(context, secondId);
            AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
            if (
                authorizeService.authorizeActionBoolean(context, firstItem, Constants.WRITE) ||
                    authorizeService.authorizeActionBoolean(context, secondItem, Constants.WRITE)
            ) {

                if (row != null) {
                    String submitterDecision = row.getSubmitterDecision();
                    if (check && StringUtils.isNotBlank(submitterDecision)) {
                        row.setSubmitterDecision(submitterDecision);
                    }
                } else {
                    row = deduplicationService.create(context, new Deduplication());
                }

                row.setEpersonId(context.getCurrentUser().getID());
                row.setFirstItemId(sortedIds[0]);
                row.setSecondItemId(sortedIds[1]);
                row.setRejectTime(new Date());
                row.setNote(note);
                row.setFake(notDupl);
                if (check) {
                    row.setWorkflowDecision(DeduplicationFlag.REJECTWF.getDescription());
                } else {
                    row.setSubmitterDecision(DeduplicationFlag.REJECTWS.getDescription());
                }
                deduplicationService.update(context, row);
                getDedupService().buildDecision(context, firstId, secondId,
                                           check ? DeduplicationFlag.REJECTWF :
                                               DeduplicationFlag.REJECTWS, note);
                return true;
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
        return false;
    }

    protected DedupService getDedupService() {
        if (this.dedupService == null) {
            this.dedupService =
                DSpaceServicesFactory.getInstance().getServiceManager().getServicesByType(DedupService.class).get(0);
        }
        return this.dedupService;
    }


}
