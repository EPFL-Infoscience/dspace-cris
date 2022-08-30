/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submit.factory;

import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.submit.service.ChangeSubmitterService;

public abstract class SubmitterServiceFactory {

    public abstract ChangeSubmitterService getSubmitterService();

    public static SubmitterServiceFactory getInstance() {
        return DSpaceServicesFactory.getInstance().getServiceManager().getServiceByName("submitterServiceFactory",
                SubmitterServiceFactory.class);
    }
}