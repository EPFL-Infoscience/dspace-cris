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

public class PublicationLoaderScriptConfiguration<T extends PublicationLoaderRunnable>
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
     * @param dspaceRunnableClass   The dspaceRunnableClass to be set on this OAIREPublicationLoaderScriptConfiguration
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

            options.addOption("s", "single-researcher", true, "Single researcher UUID");
            options.getOption("s").setType(String.class);

            options.addOption("l", "loader", true, "publication loader to be used " +
                "(oaire, pubmed, orcid)");
            options.getOption("l").setRequired(true);

            options.addOption("q", "query", true, "extra parameters to append to the generated query "
                + "(to limit the publications year, record creation time, etc. according "
                + "to the datasource capabilities)");
            options.getOption("q").setType(String.class);
            options.getOption("q").setRequired(false);

            options.addOption("il", "item-limit", true, "the max number of profiles. If no limit is provided, "
                + "the default one will be used");
            options.getOption("il").setType(Integer.class);
            options.getOption("il").setRequired(false);

            super.options = options;
        }
        return options;
    }

}
