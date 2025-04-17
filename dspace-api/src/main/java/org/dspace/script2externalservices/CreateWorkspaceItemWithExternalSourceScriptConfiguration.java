/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.script2externalservices;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.external.provider.impl.LiveImportDataProvider;
import org.dspace.external.service.ExternalDataService;
import org.dspace.kernel.ServiceManager;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.dspace.utils.DSpace;
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

    protected Map<String, LiveImportDataProvider> nameToProvider;

    protected ExternalDataService externalDataService;

    @Autowired
    private AuthorizeService authorizeService;

    @Override
    public boolean isAllowedToExecute(Context context, List<DSpaceCommandLineParameter> commandLineParameters) {
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
            ServiceManager serviceManager = new DSpace().getServiceManager();
            List<String> providers = new ArrayList<String>();
            if (serviceManager.isServiceExists("scopusLiveImportDataProvider")) {
                providers.add("\"scopus\"");
            }
            if (serviceManager.isServiceExists("wosLiveImportDataProvider")) {
                providers.add("\"wos\"");
            }
            if (serviceManager.isServiceExists("crossRefLiveImportDataProvider")) {
                providers.add("\"crossref\"");
            }
            if (serviceManager.isServiceExists("arxivLiveImportDataProvider")) {
                providers.add("\"arxiv\"");
            }
            if (serviceManager.isServiceExists("epoLiveImportDataProvider")) {
                providers.add("\"epo\"");
            }
            Options options = new Options();
            options.addOption("s", "service", true, "the name of the external service to be " +
                "queried (" + StringUtils.join(providers, ",") + ")");
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

            options.addOption("q", "query", true, "extra parameters to append to the generated query "
                    + "(to limit the publications year, record creation time, etc. according "
                    + "to the datasource capabilities)");
            options.getOption("q").setType(String.class);
            options.getOption("q").setRequired(false);

            options.addOption("l", "limit", true,
                    "the max number of request to the external service (search/count )to be performed."
                    + " If no limit is provided, the default one will be used");
            options.getOption("l").setType(Integer.class);
            options.getOption("l").setRequired(false);

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

    public Map<String, LiveImportDataProvider> getNameToProvider() {
        return nameToProvider;
    }

    public void setNameToProvider(Map<String, LiveImportDataProvider> nameToProvider) {
        this.nameToProvider = nameToProvider;
    }

    public ExternalDataService getExternalDataService() {
        return externalDataService;
    }

    public void setExternalDataService(ExternalDataService externalDataService) {
        this.externalDataService = externalDataService;
    }

}