/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import javax.mail.MessagingException;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.dspace.core.EmailUtils.UnmodifiableConfigurationService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 *
 */
public class EmailTemplate {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplate.class);

    /** Velocity template settings. */
    protected static final String RESOURCE_REPOSITORY_NAME = "Email";
    protected static final Properties VELOCITY_PROPERTIES = new Properties();
    static {
        VELOCITY_PROPERTIES.put(Velocity.RESOURCE_LOADERS, "string");
        VELOCITY_PROPERTIES.put("resource.loader.string.description",
                "Velocity StringResource loader");
        VELOCITY_PROPERTIES.put("resource.loader.string.class",
                StringResourceLoader.class.getName());
        VELOCITY_PROPERTIES.put("resource.loader.string.repository.name",
                RESOURCE_REPOSITORY_NAME);
        VELOCITY_PROPERTIES.put("resource.loader.string.repository.static",
                "false");
    }

    public static EmailTemplate getEmailTemplate(String templateFile) throws IOException {
        StringBuilder contentBuffer = new StringBuilder();
        try (
            InputStream is = new FileInputStream(templateFile);
            InputStreamReader ir = new InputStreamReader(is, "UTF-8");
            BufferedReader reader = new BufferedReader(ir);
        ) {
            boolean more = true;
            while (more) {
                String line = reader.readLine();
                if (line == null) {
                    more = false;
                } else {
                    contentBuffer.append(line);
                    contentBuffer.append("\n");
                }
            }
        }
        return new EmailTemplate(templateFile, contentBuffer.toString());
    }

    /** Velocity template for a message body */
    private Template template;
    private String content;
    private String templateFile;
    private VelocityContext vctx;

    public EmailTemplate(String templateFile, String content) {
        this.templateFile = templateFile;
        this.content = content;
    }

    public String generateTemplate(List<Object> arguments, Map<String, Object> argumentMap) throws MessagingException {
        ConfigurationService config = DSpaceServicesFactory.getInstance().getConfigurationService();

        VelocityEngine templateEngine = new VelocityEngine();
        templateEngine.init(VELOCITY_PROPERTIES);

        this.vctx = new VelocityContext();
        vctx.put("config", new UnmodifiableConfigurationService(config));

        Optional.ofNullable(argumentMap)
            .filter(map -> !map.isEmpty())
            .ifPresent(map ->
                map.entrySet()
                    .stream()
                    .forEach(entry -> vctx.put(entry.getKey(), entry.getValue()))
            );

        // TODO: replace positional items in templates with keys
        // email templates are using indexes of params array
        // so we fill it with empty strings to overcome IOB errors!

        ArrayList<Object> argumentList = new ArrayList<Object>(arguments);
        argumentList.addAll(Collections.nCopies(20, ""));

        Optional.ofNullable(argumentList)
            .filter(list -> !list.isEmpty())
            .ifPresent(list -> vctx.put("params", Collections.unmodifiableList(list)));

        if (null == template) {
            if (StringUtils.isBlank(content)) {
                // No template and no content -- PANIC!!!
                throw new MessagingException("Email has no body");
            }
            // No template, so use a String of content.
            StringResourceRepository repo = (StringResourceRepository)
                    templateEngine.getApplicationAttribute(RESOURCE_REPOSITORY_NAME);
            repo.putStringResource(templateFile, content);
            // Turn content into a template.
            template = templateEngine.getTemplate(templateFile);
        }

        StringWriter writer = new StringWriter();
        try {
            template.merge(vctx, writer);
        } catch (MethodInvocationException | ParseErrorException | ResourceNotFoundException ex) {
            log.error("Template not merged:  {}", ex.getMessage());
            throw new MessagingException("Template not merged", ex);
        }

        return writer.toString();
    }

    public Template getTemplate() {
        return template;
    }

    public void setTemplate(Template template) {
        this.template = template;
    }


    public String getContent() {
        return content;
    }


    public void setContent(String content) {
        this.content = content;
    }

    protected VelocityContext getVctx() {
        return vctx;
    }

}
