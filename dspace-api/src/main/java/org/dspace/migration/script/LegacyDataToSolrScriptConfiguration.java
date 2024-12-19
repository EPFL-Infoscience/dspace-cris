/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.migration.script;

import java.sql.SQLException;
import java.util.List;

import org.apache.commons.cli.Options;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

public class LegacyDataToSolrScriptConfiguration<T extends LegacyDataToSolrScript> extends ScriptConfiguration<T> {
    private Class<T> dspaceRunnableClass;

    @Autowired
    private AuthorizeService authorizeService;

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
            throw new RuntimeException("Unable to check if current user is admin", e);
        }
    }

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();

            options.addOption("l", "limit", true, "maximum number of items to import");
            options.getOption("l").setType(String.class);
            options.getOption("l").setRequired(false);

            options.addOption("f", "from", true, "start from bucket file having name");
            options.getOption("f").setType(String.class);
            options.getOption("f").setRequired(false);

            options.addOption("b", "bucket", true, "bucket name");
            options.getOption("b").setType(String.class);
            options.getOption("b").setRequired(true);

            super.options = options;
        }
        return options;
    }
}
