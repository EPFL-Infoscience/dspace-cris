/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.email.template;

import java.util.List;
import javax.mail.MessagingException;

import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.core.EmailTemplate;
import org.dspace.eperson.EPerson;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.xmlworkflow.storedcomponents.ClaimedTask;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 *
 */
public class EmailTemplateServiceImpl implements EmailTemplateService {

    ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.dspace.app.email.template.EmailTemplateService#generateContent(org.dspace
     * .core.Context, org.dspace.core.EmailTemplate,
     * org.dspace.xmlworkflow.storedcomponents.ClaimedTask)
     */
    @Override
    public String generateContent(
            Context context,
            EmailTemplate emailTemplate,
            ClaimedTask claimedTask
    ) throws MessagingException {
        XmlWorkflowItem workflowItem = claimedTask.getWorkflowItem();
        Item item = workflowItem.getItem();
        Collection collection = workflowItem.getCollection();
        EPerson submitter = claimedTask.getOwner();
        String fullName = submitter.getFullName();
        String email = submitter.getEmail();
        String itemName = item.getName();
        String collectionName = collection.getName();
        List<Object> arguments = List.of(
            itemName,
            collectionName,
            fullName + "(" + email + ")",
            configurationService.getProperty("dspace.ui.url") + "/mydspace"
        );
        return emailTemplate.generateTemplate(arguments, null);
    }
}
