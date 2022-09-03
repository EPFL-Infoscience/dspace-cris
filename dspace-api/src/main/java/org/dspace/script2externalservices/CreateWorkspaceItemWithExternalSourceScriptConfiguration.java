/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.script2externalservices;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.cli.Options;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.external.provider.impl.LiveImportDataProvider;
import org.dspace.external.service.ExternalDataService;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@link ScriptConfiguration} for the {@link CreateWorkspaceItemWithExternalSource}.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 */
public class CreateWorkspaceItemWithExternalSourceScriptConfiguration<T extends CreateWorkspaceItemWithExternalSource>
       extends ScriptConfiguration<T> {

    private static final Logger log = LoggerFactory
                                .getLogger(CreateWorkspaceItemWithExternalSourceScriptConfiguration.class);

    private Class<T> dspaceRunnableClass;

    protected Map<String, LiveImportDataProvider> nameToPrider;

    protected ExternalDataService externalDataService;

    @Autowired
    private AuthorizeService authorizeService;

    @Override
    public boolean isAllowedToExecute(Context context) {
        try {
            return authorizeService.isAdmin(context) || authorizeService.isCollectionAdmin(context);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public Options getOptions() {
        if (Objects.isNull(options)) {
            Options options = new Options();
            options.addOption("s", "service", true, "the name of the external service to be " +
                "queried (\"scopus\" or \"wos\" or \"crossref\")");
            options.getOption("s").setType(String.class);
            options.getOption("s").setRequired(true);
            options.addOption("e", "eperson", true, "email of the eperson performing the import");
            options.addOption("f", "final status", true, "the final status of import " +
                    "choose between (\"workspace\" or \"workflow\" or \"item\")");
            options.getOption("f").setType(String.class);
            options.getOption("f").setRequired(true);
            options.addOption("c", "collection-uuid", true, "collection-uuid into which to make import");
            options.getOption("c").setType(String.class);
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

    public Map<String, LiveImportDataProvider> getNameToPrider() {
        return nameToPrider;
    }

    public void setNameToPrider(Map<String, LiveImportDataProvider> nameToPrider) {
        this.nameToPrider = nameToPrider;
    }

    public ExternalDataService getExternalDataService() {
        return externalDataService;
    }

    public void setExternalDataService(ExternalDataService externalDataService) {
        this.externalDataService = externalDataService;
    }

}