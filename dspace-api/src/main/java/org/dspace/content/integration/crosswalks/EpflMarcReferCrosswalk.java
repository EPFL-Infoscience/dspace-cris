/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.sql.SQLException;
import java.util.List;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.CrosswalkObjectNotSupported;
import org.dspace.core.Context;

public class EpflMarcReferCrosswalk extends ReferCrosswalk {

    @Override
    public void disseminate(Context context, DSpaceObject dso, OutputStream out)
            throws CrosswalkException, IOException, SQLException, AuthorizeException {

        if (!canDisseminate(context, dso)) {
            throw new CrosswalkObjectNotSupported("Can only crosswalk an Item with the configured type: ");
        }

        if (!isAuthorized(context)) {
            throw new AuthorizeException("The current user is not allowed to perform a zip item export");
        }
        try (OutputStreamWriter osw = new OutputStreamWriter(out, UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.newLine();
            List<String> lines = getItemLines(context, dso, true);
            writeLines(writer, lines);
            writer.newLine();
            writer.flush();
        }
    }
}
