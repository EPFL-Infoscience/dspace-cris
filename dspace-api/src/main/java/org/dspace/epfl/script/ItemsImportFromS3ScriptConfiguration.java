/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import java.io.InputStream;
import java.util.List;
import java.sql.SQLException;

import org.apache.commons.cli.Options;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

public class ItemsImportFromS3ScriptConfiguration<T extends ItemsImportFromS3Script> extends ScriptConfiguration<T> {

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

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();

            options.addOption("k", "keys", true, "the object's key to download");
            options.getOption("k").setType(String.class);
            options.getOption("k").setRequired(false);

            options.addOption("l", "limit", true, "the number of items to download");
            options.getOption("l").setType(Integer.class);
            options.getOption("l").setRequired(false);

            options.addOption("cs", "commitSize", true, "the commit size (default 20)");
            options.getOption("cs").setType(Integer.class);
            options.getOption("cs").setRequired(false);

            options.addOption("a", "after", true, "the key from which to start the download");
            options.getOption("a").setType(String.class);
            options.getOption("a").setRequired(false);

            options.addOption("kf", "keysFile", true, "the file with the list of object's keys to download");
            options.getOption("kf").setType(InputStream.class);
            options.getOption("kf").setRequired(false);

            options.addOption("sbu", "skipBitstreamsUpload", false, "skip the bitstreams upload");
            options.getOption("sbu").setType(boolean.class);
            options.getOption("sbu").setRequired(false);

            options.addOption("w", "workbookMode", false, "enable workbook mode");
            options.getOption("w").setType(boolean.class);
            options.getOption("w").setRequired(false);

            options.addOption("m", "modificationDateMode", false, "enable modification date mode");
            options.getOption("m").setType(boolean.class);
            options.getOption("m").setRequired(false);

            options.addOption("f", "forceMode", false,
                    "enable force mode (delete the item if already exists before to import)");
            options.getOption("f").setType(boolean.class);
            options.getOption("f").setRequired(false);

            options.addOption("cd", "creationDates", true, "import only zip with creation dates");
            options.getOption("cd").setType(InputStream.class);
            options.getOption("cd").setRequired(false);

            super.options = options;
        }
        return options;
    }

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

}
