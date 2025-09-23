/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.policy;

import java.sql.SQLException;
import java.util.List;

import org.apache.commons.cli.Options;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

public class PolicyMetadataScriptConfiguration<T extends PolicyMetadataScript> extends ScriptConfiguration<T> {

    @Autowired
    private AuthorizeService authorizeService;

    private Class<T> dspaceRunnableClass;

    @Override
    public boolean isAllowedToExecute(Context context, List<DSpaceCommandLineParameter> commandLineParameters) {
        try {
            return authorizeService.isAdmin(context);
        } catch (SQLException e) {
            throw new RuntimeException("SQLException occurred when checking if the current user is an admin", e);
        }
    }

    public Options getOptions() {
        if (options == null) {
            Options options = new Options();
            options.addOption("i", "index", true, "optional parameter (index) to specify the item uuid");
            options.getOption("i").setType(String.class);
            options.getOption("i").setRequired(false);

            options.addOption("ps", "pageSize", true, "optional pagination size (default 20)." +
                    " A commit will be performed every pageSize items processed. Does not work with -i parameter");
            options.getOption("ps").setType(Integer.class);
            options.getOption("ps").setRequired(false);

            options.addOption("m", "metadata", true,
                    "optional value of the datacite.rights metadatum to filter the items to elaborate");
            options.getOption("m").setType(String.class);
            options.getOption("m").setRequired(false);

            super.options = options;
        }
        return options;
    }

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    /**
     * Generic setter for the dspaceRunnableClass
     *
     * @param dspaceRunnableClass The dspaceRunnableClass to be set on this
     *                            BulkImportScriptConfiguration
     */
    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }
}
