/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.sync;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.cli.Options;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.springframework.beans.factory.annotation.Autowired;


public class EpflUserSynchronizationScriptConfiguration<T extends EpflUserSynchronizationScript>
    extends ScriptConfiguration<T> {

    @Autowired
    private AuthorizeService authorizeService;

    private Class<T> dspaceRunnableClass;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

    @Override
    public boolean isAllowedToExecute(Context context, List<DSpaceCommandLineParameter> commandLineParameters) {
        try {
            return authorizeService.isAdmin(context);
        } catch (SQLException e) {
            throw new RuntimeException("SQLException occurred when checking if the current user is an admin", e);
        }
    }

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();

            options.addOption("f", "file", true,
                "optional, a xml file with scipers to be imported / updated");
            options.getOption("f").setType(InputStream.class);
            options.getOption("f").setRequired(false);

            options.addOption("q", "query", true, "optional parameter (query) to be used to query epfl's ldap system");
            options.getOption("q").setType(String.class);
            options.getOption("q").setRequired(false);

            options.addOption("e", "email", true, "optional email of the ePerson performing this action");
            options.getOption("e").setType(String.class);
            options.getOption("e").setRequired(false);

            options.addOption("dq", "deactivationOnQuery", false,
                    "optional parameter to be used to allow deactivation" +
                            " of users specified with the -q parameter (works only with -q");
            options.getOption("dq").setType(Boolean.class);
            options.getOption("dq").setRequired(false);

            super.options = options;
        }
        return options;
    }
}
