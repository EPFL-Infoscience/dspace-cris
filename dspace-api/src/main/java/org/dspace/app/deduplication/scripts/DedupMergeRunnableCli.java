/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.deduplication.scripts;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.dspace.utils.DSpace;

public class DedupMergeRunnableCli extends DedupMergeRunnable {

    @Override
    @SuppressWarnings({ "rawtypes" })
    public DedupMergeCliScriptConfiguration getScriptConfiguration() {
        DedupMergeCliScriptConfiguration configuration = new DSpace()
            .getServiceManager()
            .getServiceByName("deduplication-merge-items", DedupMergeCliScriptConfiguration.class);
        return configuration;
    }

    @Override
    public void setup() throws ParseException {
        super.setup();

        // in case of CLI we show the help prompt
        if (commandLine.hasOption('h')) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("merge deduplication set items ", getScriptConfiguration().getOptions());
            System.exit(0);
        }
    }
}
