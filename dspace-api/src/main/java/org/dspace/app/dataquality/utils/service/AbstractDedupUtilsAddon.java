/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.dataquality.utils.service;

import org.dspace.app.dataquality.utils.service.IDedupUtilsAddonComposition;
import org.dspace.app.deduplication.utils.IDedupUtils;

/**
 * Composition Interface (cannot extend two interfaces, overcoming this limitation by using implementing multiple
 * interfaces)
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public abstract class AbstractDedupUtilsAddon implements IDedupUtilsAddonComposition, IDedupUtils {
}
