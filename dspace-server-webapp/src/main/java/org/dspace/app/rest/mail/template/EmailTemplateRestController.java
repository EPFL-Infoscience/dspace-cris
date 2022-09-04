/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.mail.template;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.email.template.EmailTemplateService;
import org.dspace.app.rest.model.ClaimedTaskRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.core.Context;
import org.dspace.core.EmailTemplate;
import org.dspace.core.I18nUtil;
import org.dspace.xmlworkflow.storedcomponents.ClaimedTask;
import org.dspace.xmlworkflow.storedcomponents.service.ClaimedTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(EmailTemplateRestController.BASE_PATH)
public class EmailTemplateRestController {

    protected static final String BASE_PATH = "api/email/template";

    private static final Logger log = LogManager.getLogger();

    @Autowired
    ClaimedTaskService claimedTaskService;

    @Autowired
    EmailTemplateService emailTemplateService;

    @GetMapping(produces = "application/json;charset=UTF-8")
    public ResponseEntity<List<String>> getAll(HttpServletRequest request) {
        List<String> emailTemplates = I18nUtil.getEmailTemplates(ContextUtil.obtainContext(request).getCurrentLocale());
        return new ResponseEntity<>(
                emailTemplates
                    .stream()
                    .filter(t -> StringUtils.startsWith(t, "workflowcustomemail_"))
                    .sorted()
                    .collect(Collectors.toList()),
                HttpStatus.OK
        );
    }

    @GetMapping(
        value = "/{name}/" + ClaimedTaskRest.NAME + "/{claimedTaskId}",
        produces = "application/json;charset=UTF-8"
    )
    public ResponseEntity<EmailTemplateRest> generate(
            @PathVariable("name") String templateName,
            @PathVariable("claimedTaskId") Integer claimedTaskId,
            HttpServletRequest request
    ) throws MessagingException, IOException, SQLException {
        Context context = ContextUtil.obtainContext(request);
        ClaimedTask claimedTask = this.claimedTaskService.find(context, claimedTaskId);
        EmailTemplate emailTemplate = EmailTemplate.getEmailTemplate(
                I18nUtil.getEmailFilename(context.getCurrentLocale(), templateName)
        );
        String[] generateContent = this.emailTemplateService.generateContent(
            context,
            emailTemplate,
            claimedTask
        );
        return new ResponseEntity<>(
                new EmailTemplateRest(generateContent[0],generateContent[1]),
                HttpStatus.OK
        );
    }
}