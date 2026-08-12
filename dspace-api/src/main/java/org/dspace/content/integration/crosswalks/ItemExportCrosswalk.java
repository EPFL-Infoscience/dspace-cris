/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks;

import java.util.List;
import java.util.Optional;

import org.dspace.content.crosswalk.CrosswalkMode;
import org.dspace.content.crosswalk.StreamDisseminationCrosswalk;
import org.dspace.core.Context;

/**
 * Implementation of {@link StreamDisseminationCrosswalk} related to item
 * export.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public interface ItemExportCrosswalk extends StreamDisseminationCrosswalk, FileNameDisseminator {

    /**
     * Returns the type of entities that the specific ItemExportCrosswalk
     * implementation is capable of processing, if any. If an entity type is not
     * configured or special word 'all' used the export can be performed on any item.
     *
     * @return the entity type, if configured, or an empty Optional
     */
    public default Optional<String> getEntityType() {
        return Optional.empty();
    }

    /**
     * Returns a list of all the discovery configurations that the specific
     * ItemExportCrosswalk is associated with. This means that the crosswalk
     * will be used with the entities that are related to the configurations.
     *
     * @return the list of discovery configurations, if any, or an empty Optional
     */
    public default Optional<List<String>> getConfigurations() {
        return Optional.empty();
    }

    /**
     * Returns the supported crosswalk mode.
     *
     * @return the crosswalk mode
     */
    public default CrosswalkMode getCrosswalkMode() {
        return CrosswalkMode.SINGLE;
    }

    public default boolean isAuthorized(Context context) {
        return true;
    }
}
