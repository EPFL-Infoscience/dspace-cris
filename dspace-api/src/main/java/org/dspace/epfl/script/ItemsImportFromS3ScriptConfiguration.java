/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import java.sql.SQLException;

import org.apache.commons.cli.Options;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

public class ItemsImportFromS3ScriptConfiguration<T extends ItemsImportFromS3Script> extends ScriptConfiguration<T> {

    @Autowired
    private AuthorizeService authorizeService;

    private Class<T> dspaceRunnableClass;

    @Override
    public boolean isAllowedToExecute(Context context) {
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

            options.addOption("c", "collection", true, "the own collection of the imported items");
            options.getOption("c").setType(String.class);
            options.getOption("c").setRequired(true);

            options.addOption("k", "keys", true, "the object's key to download");
            options.getOption("k").setType(String.class);
            options.getOption("k").setRequired(false);

            options.addOption("l", "limit", true, "the number of items to download");
            options.getOption("l").setType(Integer.class);
            options.getOption("l").setRequired(false);

            options.addOption("a", "after", true, "the key from which to start the download");
            options.getOption("a").setType(String.class);
            options.getOption("a").setRequired(false);

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
