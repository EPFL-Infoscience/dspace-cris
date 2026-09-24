/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.citation;

import java.sql.SQLException;
import java.util.List;

import org.apache.commons.cli.Options;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Configuration for the {@link CitationMetadataScript}.
 *
 * @author Daniele Ninfo (daniele.ninfo at 4science.com)
 */
public class CitationMetadataScriptConfiguration<T extends CitationMetadataScript>
        extends ScriptConfiguration<T> {

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

            options.addOption("i", "index", true,
                    "UUID of an item, collection, or community to use as scope for the generation");
            options.getOption("i").setType(String.class);
            options.getOption("i").setRequired(false);

            options.addOption("f", "force", false,
                    "Force regeneration of citations regardless of modification date");
            options.getOption("f").setRequired(false);

            options.addOption("c", "commit", true,
                    "Number of items to process before each commit (default: 100)");
            options.getOption("c").setType(Integer.class);
            options.getOption("c").setRequired(false);

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
