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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.sql.SQLException;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.junit.Before;
import org.junit.Test;

public class OrgUnitTSVImportScriptIT extends AbstractIntegrationTestWithDatabase {

    private static final String BASE_TSV_DIR_PATH = "./target/testing/dspace/assetstore/orgunits-tsv-import/";

    private Community community;

    private Collection collection;

    @Before
    public void beforeTests() throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        community = createCommunity(context).build();
        collection = createCollection(context, community)
            .withEntityType("OrgUnit")
            .build();
        context.restoreAuthSystemState();
        context.commit();
    }

    @Test
    public void testOnlyActiveOrgUnitsImport() throws Exception {

        String fileLocation = getTSVFilePath("active_units.tsv");
        String[] args = new String[] { "orgunit-tsv-import", "-c", collection.getID().toString(), "-f", fileLocation };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

    }

    @Test
    public void testOnlyInactiveOrgUnitsImport() throws Exception {

        String fileLocation = getTSVFilePath("inactive_units.tsv");
        String[] args = new String[] { "orgunit-tsv-import", "-c", collection.getID().toString(), "-f", fileLocation };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

    }

    @Test
    public void testOUnitsImportWithIncompleteNames() throws Exception {

        String fileLocation = getTSVFilePath("units_with_incomplete_names.tsv");
        String[] args = new String[] { "orgunit-tsv-import", "-c", collection.getID().toString(), "-f", fileLocation };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
        assertEquals(handler.getInfoMessages().get(2),
                "Head name is missing in tsv, and it was not possible to get it from the api:" +
                        " head name metadata is not added");
        assertEquals(handler.getInfoMessages().get(5),
                "Head name is missing in tsv, taking head name from api: Dyson, Paul Joseph");
    }

    private String getTSVFilePath(String name) {
        return new File(BASE_TSV_DIR_PATH, name).getAbsolutePath();
    }

}
