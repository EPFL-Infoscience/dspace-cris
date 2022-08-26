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
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.workflow.WorkflowException;
import org.dspace.xmlworkflow.factory.XmlWorkflowServiceFactory;
import org.dspace.xmlworkflow.state.Step;
import org.dspace.xmlworkflow.state.actions.ActionResult;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

/**
 * EmailAction that can be used in step to send an
 * email template
 * 
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 *
 */
public class EmailAction extends ProcessingAction {

    private static final String MAIL_SUBJECT = "subject";
    private static final String MAIL_CONTENT = "content";
    public static final int MAIN_PAGE = 0;
    public static final int REJECT_PAGE = 1;

    private static final String SUBMIT_MAIL = "submit_mail";

    private final List<String> options = List.of(SUBMIT_MAIL);

    @Override
    public void activate(Context c, XmlWorkflowItem wf)
            throws SQLException, IOException, AuthorizeException, WorkflowException {

    }

    @Override
    public ActionResult execute(Context c, XmlWorkflowItem wfi, Step step, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException, WorkflowException {
        if (super.isOptionInParam(request)) {
            switch (Util.getSubmitButton(request, SUBMIT_CANCEL)) {
                case SUBMIT_MAIL:
                    return processMail(c, wfi, request);
                default:
                    return new ActionResult(ActionResult.TYPE.TYPE_CANCEL);
            }
        }
        return new ActionResult(ActionResult.TYPE.TYPE_CANCEL);
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

        Email email = new Email(subject, content);
        
        XmlWorkflowServiceFactory
            .getInstance()
            .getXmlWorkflowService()
            .sendWorkflowItemBackSubmission(c, wfi, c.getCurrentUser(),this.getProvenanceStartId(), subject);


        return new ActionResult(ActionResult.TYPE.TYPE_SUBMISSION_PAGE);
    }

    private ActionResult getErrorActionResult(HttpServletRequest request, String errorField) {
        request.setAttribute("page", REJECT_PAGE);
        addErrorField(request, errorField);
        return new ActionResult(ActionResult.TYPE.TYPE_ERROR);
    }

    @Override
    public List<String> getOptions() {
        return this.options;
    }

}
