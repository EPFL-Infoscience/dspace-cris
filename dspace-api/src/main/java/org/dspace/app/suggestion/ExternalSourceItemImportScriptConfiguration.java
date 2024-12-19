/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.suggestion;

import java.util.List;
import java.sql.SQLException;

import org.apache.commons.cli.Options;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

public class ExternalSourceItemImportScriptConfiguration<T extends ExternalSourceItemImportRunnable>
    extends ScriptConfiguration<T> {

    @Autowired
    private AuthorizeService authorizeService;

    private Class<T> dspaceRunnableClass;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    /**
     * Generic setter for the dspaceRunnableClass
     * @param dspaceRunnableClass   The dspaceRunnableClass to be set on this
     * ExternalSourceItemImportScriptConfiguration
     */
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

            options.addOption("p", "provider", true, "suggestion provider name");
            options.getOption("p").setType(String.class);
            options.getOption("p").setRequired(true);

            options.addOption("s", "score", true, "minimum score of " +
                "suggestions to be imported");
            options.getOption("s").setType(String.class);
            options.getOption("s").setRequired(true);

            options.addOption("t", "target", true, "uuid of collection where " +
                "suggestions will be imported");
            options.getOption("t").setType(String.class);
            options.getOption("t").setRequired(true);

            options.addOption("ty", "type", true, "dc.type metadata value");
            options.getOption("ty").setType(String.class);
            options.getOption("ty").setRequired(false);

            options.addOption("pe", "person", true, "uuid of a person." +
                "If set restricts the imported suggestions only to those of this person");
            options.getOption("pe").setType(String.class);
            options.getOption("pe").setRequired(false);

            // this option is mandatory if we run from CLI
            options.addOption("e", "email", true, "importing eperson email");
            options.getOption("e").setType(String.class);

            options.addOption("l", "limit", true, "limits the number of " +
                "suggestions to be imported");
            options.getOption("l").setType(String.class);

            super.options = options;
        }
        return options;
    }

}
