/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks.csl;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import de.undercouch.citeproc.csl.CSLType;

public class CSLTypeAdapter extends TypeAdapter<CSLType> {
    @Override
    public void write(JsonWriter out, CSLType cslType) throws IOException {
        if (cslType == null) {
            out.nullValue();
        } else {
            out.value(cslType.toString());
        }
    }

    @Override
    public CSLType read(JsonReader jsonReader) throws IOException {
        if (jsonReader.peek() == JsonToken.NULL) {
            jsonReader.nextNull();
            return null;
        } else {
            try {
                return CSLType.fromString(jsonReader.nextString());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

    }
}
