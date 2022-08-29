/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
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
