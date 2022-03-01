/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.DeduplicationSetRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

/**
 * DeduplicationSet Rest HAL Resource. The HAL Resource wraps the REST Resource
 * adding support for the links and embedded resources
 *
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */
@RelNameDSpaceResource(DeduplicationSetRest.NAME)
public class DeduplicationSetResource extends DSpaceResource<DeduplicationSetRest> {
    public DeduplicationSetResource(DeduplicationSetRest signatureRest, Utils utils) {
        super(signatureRest, utils);
    }
}
