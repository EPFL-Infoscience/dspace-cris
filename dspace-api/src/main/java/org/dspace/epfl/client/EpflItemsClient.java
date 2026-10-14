/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.client;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import software.amazon.awssdk.services.s3.model.S3Object;

public interface EpflItemsClient {

    Iterator<S3Object> iterateObjects();

    List<S3Object> getObjects(Integer limit, String startAfter);

    File get(String key);

    String getCreationDate(String id);

    String getCreationDateByKey(String key);

    Iterator<S3Object> iterateCreationDate();

}
