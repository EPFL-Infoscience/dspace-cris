/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.DeduplicationSignatureRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

/**
 * DeduplicationSignature Rest HAL Resource. The HAL Resource wraps the REST Resource
 * adding support for the links and embedded resources
 *
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */
@RelNameDSpaceResource(DeduplicationSignatureRest.NAME)
public class DeduplicationSignatureResource extends DSpaceResource<DeduplicationSignatureRest> {
    public DeduplicationSignatureResource(DeduplicationSignatureRest signatureRest, Utils utils) {
        super(signatureRest, utils);
    }
}
