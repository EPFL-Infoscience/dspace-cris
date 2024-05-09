/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.dataquality.utils.service;

import java.util.List;
import java.util.Optional;

import org.dspace.app.util.DCInput;
import org.dspace.app.util.DCInputSet;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public interface DCInputUtils {

    static DCInputUtils getInstance() {
        return DSpaceServicesFactory.getInstance().getServiceManager().getServicesByType(DCInputUtils.class).get(0);
    }

    public Optional<DCInput> findParent(DCInputSet inputSet, String fieldName);

    boolean hasParent(DCInputSet inputSet, String fieldName);

    Optional<DCInput> getChildField(DCInputSet inputSet, DCInput field, String fieldName);

    List<String> getMetadataFields(DCInputSet inputSet);
}
