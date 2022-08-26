package org.dspace.submit.service.impl;

import java.sql.SQLException;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Item;
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

    @Override
    public void setUpSubmitter(Context context, Item item, String submitterName) throws SQLException, AuthorizeException {
        if (StringUtils.isBlank(submitterName)) {
            return;
        }
        EPerson submitter = findSubmitter(context, submitterName);
        if (Objects.isNull(submitter) || submitter.equals(item.getSubmitter())) {
            return;
        }
        EPerson previousSubmitter = item.getSubmitter();
        item.setSubmitter(submitter);
        int[] actionIds = { Constants.READ, Constants.WRITE, Constants.ADD, Constants.REMOVE, Constants.DELETE };
        for (int actionId : actionIds) {
            authorizeService.removeEPersonPolicies(context, item, previousSubmitter);
            authorizeService.addPolicy(context, item, actionId, item.getSubmitter(), ResourcePolicy.TYPE_SUBMISSION);
        }
    }

    private EPerson findSubmitter(Context context, String submitter) throws SQLException {
        EPerson ePerson = null;
        if (StringUtils.isNumeric(submitter) || Objects.nonNull(UUIDUtils.fromString(submitter))) {
            ePerson = ePersonService.findByIdOrLegacyId(context, submitter);
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
