/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailUtils {
    
    /**
     * Utility struct class for handling file attachments.
     *
     * @author ojd20
     */
    protected static class FileAttachment {
        public FileAttachment(File f, String n) {
            this.file = f;
            this.name = n;
        }

        File file;

        String name;
    }

    /**
     * Utility struct class for handling file attachments.
     *
     * @author Adán Román Ruiz at arvo.es
     */
    protected static class InputStreamAttachment {
        public InputStreamAttachment(InputStream is, String name, String mimetype) {
            this.is = is;
            this.name = name;
            this.mimetype = mimetype;
        }

        InputStream is;
        String mimetype;
        String name;
    }

    /**
     * @author arnaldo
     */
    public static class InputStreamDataSource implements DataSource {
        private final String name;
        private final String contentType;
        private final ByteArrayOutputStream baos;

        InputStreamDataSource(String name, String contentType, InputStream inputStream) throws IOException {
            this.name = name;
            this.contentType = contentType;
            baos = new ByteArrayOutputStream();
            int read;
            byte[] buff = new byte[256];
            while ((read = inputStream.read(buff)) != -1) {
                baos.write(buff, 0, read);
            }
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(baos.toByteArray());
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            throw new IOException("Cannot write to this read-only resource");
        }
    }

    /**
     * Wrap ConfigurationService to prevent templates from modifying
     * the configuration.
     */
    public static class UnmodifiableConfigurationService {
        private final ConfigurationService configurationService;

        /**
         * Swallow an instance of ConfigurationService.
         *
         * @param cs the real instance, to be wrapped.
         */
        public UnmodifiableConfigurationService(ConfigurationService cs) {
            configurationService = cs;
        }

        /**
         * Look up a key in the actual ConfigurationService.
         *
         * @param key to be looked up in the DSpace configuration.
         * @return whatever value ConfigurationService associates with {@code key}.
         */
        public String get(String key) {
            return configurationService.getProperty(key);
        }
    }


    private static final Logger log = LoggerFactory.getLogger(EmailUtils.class);

    private EmailUtils() {
    }

    public static void send(Email email, String fullMessage) throws MessagingException, IOException {
        ConfigurationService config = DSpaceServicesFactory.getInstance().getConfigurationService();

        // Get the mail configuration properties
        String from = config.getProperty("mail.from.address");
        boolean disabled = config.getBooleanProperty("mail.server.disabled", false);
        String[] fixedRecipients = config.getArrayProperty("mail.server.fixedRecipient");
        String charset = null;

        // If no character set specified, attempt to retrieve a default
        if (charset == null) { charset = config.getProperty("mail.charset"); }

        // Get session
        Session session = DSpaceServicesFactory.getInstance().getEmailService().getSession();

        // Create message
        MimeMessage message = new MimeMessage(session);

        // Set the recipients of the message
        if (disabled && fixedRecipients.length > 0) {
            for (String recipient : fixedRecipients) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            }
        } else {
            for (String recipient : email.getRecipients()) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            }

            for (String ccAddress : email.getCcAddresses()) {
                message.addRecipient(Message.RecipientType.CC, new InternetAddress(ccAddress));
            }
        }
        
        VelocityContext vctx = null;
        if (StringUtils.isBlank(fullMessage)) {            
            EmailTemplate emailTemplate = new EmailTemplate(email.getContentName(), email.getContent());
            vctx = emailTemplate.getVctx();
            fullMessage = emailTemplate.generateTemplate(email.getArguments(), null);
        }

        if (disabled && fixedRecipients.length > 0) {
            fullMessage += "\n===REAL RECIPIENT===\n";

            for (String r : email.getRecipients()) {
                fullMessage += r + "\n";
            }

            if (!email.getCcAddresses().isEmpty()) {
                fullMessage += "\n===REAL RECIPIENT (cc)===\n";

                for (String c : email.getCcAddresses()) {
                    fullMessage += c + "\n";
                }
            }
        }

        // Set some message header fields
        Date date = new Date();
        message.setSentDate(date);
        message.setFrom(new InternetAddress(from));

        // Get headers defined by the template.
        String subject = email.getSubject();
        if (vctx != null) {
            for (String headerName : config.getArrayProperty("mail.message.headers")) {
                String headerValue = null;
                    headerValue = (String) vctx.get(headerName);
                if ("subject".equalsIgnoreCase(headerName)) {
                    if (null != subject) { subject = headerValue; }
                } else if ("charset".equalsIgnoreCase(headerName)) {
                    charset = headerValue;
                } else {
                    message.setHeader(headerName, headerValue);
                }
            }
        }

        // Set the subject of the email.
        if (charset != null) {
            message.setSubject(subject, charset);
        } else {
            message.setSubject(subject);
        }

        // Add attachments
        if (email.getAttachments().isEmpty() && email.getMoreAttachments().isEmpty()) {
            // If a character set has been specified, or a default exists
            if (charset != null) {
                message.setText(fullMessage, charset);
            } else {
                message.setText(fullMessage);
            }
        } else {
            Multipart multipart = new MimeMultipart();

            // create the first part of the email
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText(fullMessage);
            multipart.addBodyPart(messageBodyPart);

            // Add file attachments
            for (FileAttachment attachment : email.getAttachments()) {
                // add the file
                messageBodyPart = new MimeBodyPart();
                messageBodyPart.setDataHandler(new DataHandler(new FileDataSource(attachment.file)));
                messageBodyPart.setFileName(attachment.name);
                multipart.addBodyPart(messageBodyPart);
            }

            // Add stream attachments
            for (InputStreamAttachment attachment : email.getMoreAttachments()) {
                // add the stream
                messageBodyPart = new MimeBodyPart();
                messageBodyPart.setDataHandler(new DataHandler(
                        new InputStreamDataSource(attachment.name, attachment.mimetype, attachment.is)));
                messageBodyPart.setFileName(attachment.name);
                multipart.addBodyPart(messageBodyPart);
            }

            message.setContent(multipart);
        }

        if (email.getReplyTo() != null) {
            Address[] replyToAddr = new Address[1];
            replyToAddr[0] = new InternetAddress(email.getReplyTo());
            message.setReplyTo(replyToAddr);
        }

        if (disabled) {
            StringBuilder text = new StringBuilder("Message not sent due to mail.server.disabled:\n");

            if (fixedRecipients.length > 0) {
                text.append(String.format("Sending to fixedRecipient instead: %s\n", Arrays.toString(fixedRecipients)));
            }

            Enumeration<String> headers = message.getAllHeaderLines();
            while (headers.hasMoreElements()) {
                text.append(headers.nextElement()).append('\n');
            }

            if (!email.getAttachments().isEmpty()) {
                text.append("\nAttachments:\n");
                for (FileAttachment f : email.getAttachments()) {
                    text.append(f.name).append('\n');
                }
                text.append('\n');
            }

            text.append('\n').append(fullMessage);

            if (fixedRecipients.length > 0) { Transport.send(message); }

            log.info(text.toString());
        } else {
            Transport.send(message);
        }
    }

}
