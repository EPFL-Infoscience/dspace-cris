package org.dspace.submit.factory;

import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.submit.service.ChangeSubmitterService;

public abstract class SubmitterServiceFactory {
    
    public abstract ChangeSubmitterService getSubmitterService();

    public static SubmitterServiceFactory getInstance() {
        return DSpaceServicesFactory.getInstance().getServiceManager()
            .getServiceByName("submitterServiceFactory", SubmitterServiceFactory.class);
    }
}