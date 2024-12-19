/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.deduplication.scripts;

import java.util.List;
import java.sql.SQLException;

import org.apache.commons.cli.Options;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The {@link ScriptConfiguration} for the {@link DedupMergeRunnable} script
 */
public class DedupMergeScriptConfiguration<T extends DedupMergeRunnable> extends ScriptConfiguration<T> {

    @Autowired
    private AuthorizeService authorizeService;

    private Class<T> dspaceRunnableClass;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    /**
     * Generic setter for the dspaceRunnableClass
     * @param dspaceRunnableClass   The dspaceRunnableClass to be set on this DedupSetMergeScriptConfiguration
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

            options.addOption("h", "help", false, "help");

            options.addOption("t", "target", true, "Id of the target Item");
            options.getOption("t").setType(String.class);
            options.getOption("t").setRequired(true);

            options.addOption("m", "merge", true,
                "Item ids to be merged into the target (use multiple m if needed " +
                    "- merge occurs respecting the order from left to right)");
            options.getOption("m").setType(String.class);
            options.getOption("m").setRequired(true);

            options.addOption("x", "exclude", false,
                "Don't merge metadata, only move relationships");

            options.addOption("p", "replace_notempty", true,
                "metadata to override in the target with the values from the merged items IF NOT EMPTY");
            options.getOption("p").setType(String.class);

            options.addOption("r", "replace", true,
                "metadata to override in the target with the values from the merged items");
            options.getOption("r").setType(String.class);

            options.addOption("a", "append", true,
                "metadata to be appended to the target with the values from the merged items");
            options.getOption("a").setType(String.class);

            options.addOption("d", "delete", false,
                "delete merged items, but default is to withdraw them");

//            email option required when trying to run script by command line
            options.addOption("e", "email", true, "administrator email address");
            options.getOption("e").setType(String.class);

            super.options = options;
        }
        return options;
    }
}
