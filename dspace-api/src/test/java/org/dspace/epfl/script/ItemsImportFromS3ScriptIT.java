/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.dspace.builder.CollectionBuilder.createCollection;
import static org.dspace.builder.CommunityBuilder.createCommunity;
import static org.dspace.epfl.script.ItemsImportFromS3Script.COLLECTION_PROPERTY_PREFIX;
import static org.dspace.epfl.script.service.impl.MarcXmlParserImpl.TYPE_FILTER_PROPERTY_PREFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.Before;
import org.junit.Test;

public class ItemsImportFromS3ScriptIT extends AbstractIntegrationTestWithDatabase {

    private ConfigurationService configurationService;

    private Community community;

    private Collection collection;

    @Before
    public void beforeTests() throws SQLException, AuthorizeException {

        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

        context.turnOffAuthorisationSystem();
        community = createCommunity(context).build();
        collection = createCollection(context, community)
            .withEntityType("Publication")
            .build();
        context.restoreAuthSystemState();
        context.commit();

        readAllTypes().forEach(type -> setCollectionProperty(type));

    }

    @Test
    public void testPublicationsImport() throws Exception {

        deleteAllFilesOnExit();

        String[] args = new String[] { "items-import-from-s3", "-k", "55731.zip" };

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

    }

    private void deleteAllFilesOnExit() {
        for (String type : readAllTypes()) {
            File file = new File(type + ".xls");
            file.deleteOnExit();
        }
    }

    private List<String> readAllTypes() {
        return configurationService.getPropertyKeys(TYPE_FILTER_PROPERTY_PREFIX).stream()
            .map(propertyKey -> StringUtils.removeStart(propertyKey, TYPE_FILTER_PROPERTY_PREFIX + "."))
            .collect(Collectors.toList());
    }

    private void setCollectionProperty(String type) {
        configurationService.setProperty(COLLECTION_PROPERTY_PREFIX + "." + type, collection.getID().toString());
    }
}
