package org.dspace.submit.factory.impl;

import org.dspace.submit.factory.SubmitterServiceFactory;
import org.dspace.submit.service.ChangeSubmitterService;
import org.springframework.beans.factory.annotation.Autowired;

public class SubmitterServiceFactoryImpl extends SubmitterServiceFactory {
    @Autowired
    private ChangeSubmitterService submitterService;

    @Override
    public ChangeSubmitterService getSubmitterService() {
        return submitterService;
    }
}
