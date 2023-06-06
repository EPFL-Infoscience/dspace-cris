/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service;

import java.io.InputStream;
import java.util.stream.Stream;

public interface ItemsS3Service {

    Stream<String> getAllItemsKeys();

    InputStream getObject(String key);

}
