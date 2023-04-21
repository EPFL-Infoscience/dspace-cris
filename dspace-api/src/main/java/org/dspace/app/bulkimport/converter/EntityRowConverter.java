/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.bulkimport.converter;

import org.dspace.app.bulkimport.model.EntityRow;
import org.dspace.content.Collection;
import org.dspace.core.Context;

public interface EntityRowConverter<T> {

    EntityRow convert(Context context, Collection collection, T source);
}
