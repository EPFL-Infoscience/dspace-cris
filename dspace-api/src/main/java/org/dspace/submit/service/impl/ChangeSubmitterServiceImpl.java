/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submit.service.impl;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.dspace.submit.service.ChangeSubmitterService;
import org.dspace.util.UUIDUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class ChangeSubmitterServiceImpl implements ChangeSubmitterService {

    @Autowired
    AuthorizeService authorizeService;

    @Autowired
    EPersonService ePersonService;

    @Autowired
    ItemService itemService;

    @Override
    public void setUpSubmitter(Context context, Item item, String submitterIdentifier)
            throws SQLException, AuthorizeException {
        if (StringUtils.isBlank(submitterIdentifier)) {
            return;
        }
        EPerson submitter = findSubmitter(context, submitterIdentifier);
        if (Objects.isNull(submitter) || submitter.equals(item.getSubmitter())) {
            return;
        }
        EPerson previousSubmitter = item.getSubmitter();
        item.setSubmitter(submitter);
        // fix the policies for inprogress submission
        if (itemService.isInProgressSubmission(context, item)) {
            int[] actionIds = { Constants.READ, Constants.WRITE, Constants.ADD, Constants.REMOVE, Constants.DELETE };
            authorizeService.removeAllPoliciesByDSOAndEPersonAndType(context, item, previousSubmitter,
                    ResourcePolicy.TYPE_SUBMISSION);
            for (int actionId : actionIds) {
                authorizeService.addPolicy(context, item, actionId, submitter, ResourcePolicy.TYPE_SUBMISSION);
            }
        }
    }

    private EPerson findSubmitter(Context context, String submitter) throws SQLException {
        EPerson ePerson = null;
        UUID uuid = UUIDUtils.fromString(submitter);
        if (Objects.nonNull(uuid)) {
            ePerson = ePersonService.find(context, uuid);
        }
        if (Objects.nonNull(ePerson)) {
            return ePerson;
        }
        ePerson = ePersonService.findByEmail(context, submitter);
        if (Objects.nonNull(ePerson)) {
            return ePerson;
        }
        return ePersonService.findByNetid(context, submitter);
    }
}
