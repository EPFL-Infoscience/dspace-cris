/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import java.io.File;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.junit.Test;

public class OrgUnitXMLImportScriptIT extends AbstractIntegrationTestWithDatabase {

    private static final String BASE_XML_DIR_PATH = "./target/testing/dspace/assetstore/orgunits-xml-import/";

    @Test
    public void testOnlyActiveOrgUnitsImport() throws Exception {

        String fileLocation = getXMLFilePath("one_lab.xml");
        String[] args = new String[] { "orgunit-xml-import", "-f", fileLocation };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

    }

    private String getXMLFilePath(String name) {
        return new File(BASE_XML_DIR_PATH, name).getAbsolutePath();
    }
}
