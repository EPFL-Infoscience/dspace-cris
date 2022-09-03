/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.mail.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class EmailTemplateRest {

    @JsonInclude(value = Include.NON_NULL)
    private String content;
    @JsonInclude(value = Include.NON_NULL)
    private String subject;

    public EmailTemplateRest(String content) {
        this(content, null);
    }

    public EmailTemplateRest(String content, String subject) {
        super();
        this.content = content;
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

}
