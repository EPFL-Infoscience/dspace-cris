/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery.indexobject.document;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import org.apache.solr.common.SolrInputDocument;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

public class TruncatedSolrInputDocument extends SolrInputDocument {
    private static final int FIELD_MAX_BYTE_LENGTH = 32000;

    private ConfigurationService configurationService
            = DSpaceServicesFactory.getInstance().getConfigurationService();

    @Override
    public void addField(String name, Object value) {
        if (configurationService.getBooleanProperty("discovery.index.value.truncate", false)) {
            super.addField(name, truncateValue(value));
        } else {
            super.addField(name, value);
        }
    }

    private Object truncateValue(Object value) {
        if (value instanceof String) {
            return truncateToFitUtf8ByteLength((String) value);
        }
        return value;
    }

    public static String truncateToFitUtf8ByteLength(String s) {
        if (s == null) {
            return null;
        }
        Charset charset = Charset.forName("UTF-8");
        CharsetDecoder decoder = charset.newDecoder();
        byte[] sba = s.getBytes(charset);
        if (sba.length <= FIELD_MAX_BYTE_LENGTH) {
            return s;
        }
        // Ensure truncation by having byte buffer = maxBytes
        ByteBuffer bb = ByteBuffer.wrap(sba, 0, FIELD_MAX_BYTE_LENGTH);
        CharBuffer cb = CharBuffer.allocate(FIELD_MAX_BYTE_LENGTH);
        // Ignore an incomplete character
        decoder.onMalformedInput(CodingErrorAction.IGNORE);
        decoder.decode(bb, cb, true);
        decoder.flush(cb);
        return new String(cb.array(), 0, cb.position());
    }
}
