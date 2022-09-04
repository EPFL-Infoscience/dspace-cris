/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xmlworkflow.state.actions.processingaction;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.EmailUtils;
import org.dspace.core.LogHelper;
import org.dspace.eperson.EPerson;
import org.dspace.xmlworkflow.factory.XmlWorkflowServiceFactory;
import org.dspace.xmlworkflow.service.XmlWorkflowService;
import org.dspace.xmlworkflow.state.Step;
import org.dspace.xmlworkflow.state.actions.ActionResult;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EmailAction that can be used in step to send an
 * email template
 * 
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 *
 */
public class EmailAction extends AcceptEditRejectAction {

    private static final Logger logger = LoggerFactory.getLogger(EmailAction.class);

    private static final String MAIL_SUBJECT = "subject";
    private static final String MAIL_CONTENT = "content";
    private static final String SUBMIT_MAIL = "submit_mail";
    private static final String REJECT_FLAG = "reject";

    public static final int MAIN_PAGE = 0;
    public static final int REJECT_PAGE = 1;

    @Override
    public ActionResult execute(Context c, XmlWorkflowItem wfi, Step step, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException {
        if (super.isOptionInParam(request)) {
            switch (Util.getSubmitButton(request, SUBMIT_CANCEL)) {
                case SUBMIT_APPROVE:
                    return processAccept(c, wfi);
                case SUBMITTER_IS_DELETED_PAGE:
                    return processSubmitterIsDeletedPage(c, wfi, request);
                case SUBMIT_MAIL:
                    return processMail(c, wfi, request);
                default:
                    return new ActionResult(ActionResult.TYPE.TYPE_CANCEL);
            }
        }
        return new ActionResult(ActionResult.TYPE.TYPE_CANCEL);
    }

    @Override
    public List<String> getOptions() {
        List<String> options = new ArrayList<>();
        options.add(SUBMIT_APPROVE);
        options.add(ProcessingAction.SUBMIT_EDIT_METADATA);
        options.add(SUBMIT_MAIL);
        return options;
    }

    private ActionResult processMail(Context c, XmlWorkflowItem wfi, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException {

        String content = request.getParameter(MAIL_CONTENT);
        String subject = request.getParameter(MAIL_SUBJECT);

        if (StringUtils.isBlank(content)) {
            return getErrorActionResult(request, MAIL_CONTENT);
        }

        if (StringUtils.isBlank(subject)) {
            return getErrorActionResult(request, MAIL_SUBJECT);
        }

        EPerson currentUser = c.getCurrentUser();
        EPerson eperson = wfi.getSubmitter();

        Email email = new Email(subject, content);
        email.addRecipient(eperson.getEmail());
        email.addCcAddress(currentUser.getEmail());

        try {
            EmailUtils.send(email, content);
        } catch (MessagingException e) {
            logger.warn(LogHelper.getHeader(c, "submit_mail",
                    "cannot email user" + " eperson_id" + eperson.getID()
                    + " eperson_email" + eperson.getEmail()
                    + " workflow_item_id" + wfi.getID()), e);
        }

        XmlWorkflowService xmlWorkflowService = XmlWorkflowServiceFactory.getInstance().getXmlWorkflowService();
        boolean reject = Util.getBoolParameter(request, REJECT_FLAG);
        if (reject) {
            String reason = "Subject: " + subject  + "\n\n" + content;
            // We have pressed reject, so remove the task the user has & put it back
            // to a workspace item
            xmlWorkflowService.sendWorkflowItemBackSubmission(c, wfi,
                    c.getCurrentUser(), this.getProvenanceStartId(), reason, false);
        } else {
            Item item = wfi.getItem();
            // Get current date
            String now = DCDate.getCurrent().toString();
            // Get user's name + email address
            String usersName = xmlWorkflowService.getEPersonName(c.getCurrentUser());
            String provenance = this.getProvenanceStartId();
            // Here's what happened
            String provDescription = provenance + " Additional information requested by " + usersName + ", subject: "
                + subject + "\n\n" + content + " on " + now + " (GMT) ";

            item.getItemService().addMetadata(c, item, "dc", "description", "provenance", "en", provDescription);
            item.getItemService().update(c, item);
            c.commit();
        }

        return new ActionResult(ActionResult.TYPE.TYPE_SUBMISSION_PAGE);
    }

    private ActionResult getErrorActionResult(HttpServletRequest request, String errorField) {
        request.setAttribute("page", REJECT_PAGE);
        addErrorField(request, errorField);
        return new ActionResult(ActionResult.TYPE.TYPE_ERROR);
    }

}
