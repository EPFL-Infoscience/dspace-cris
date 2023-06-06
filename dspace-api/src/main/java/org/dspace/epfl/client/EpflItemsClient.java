/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.client;

import java.io.InputStream;
import java.util.Iterator;

import com.amazonaws.services.s3.model.S3ObjectSummary;

public interface EpflItemsClient {

    Iterator<S3ObjectSummary> iterateObjects();

    InputStream get(String key);

}
