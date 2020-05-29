/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import org.dspace.app.deduplication.utils.DeduplicationSignature;
import org.dspace.app.rest.model.DeduplicationSignatureRest;
import org.dspace.app.rest.projection.Projection;
import org.springframework.stereotype.Component;

/**
 * This class provides the method to convert a DeduplicationSignature to its REST representation,
 * the DeduplicationSignatureRest
 * 
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */
@Component
public class DeduplicationSignatureConverter
        implements DSpaceConverter<DeduplicationSignature, DeduplicationSignatureRest> {

    @Override
    public DeduplicationSignatureRest convert(DeduplicationSignature signature, Projection projection) {
        DeduplicationSignatureRest signatureRest = new DeduplicationSignatureRest();
        signatureRest.setProjection(projection);
        if (signature != null) {
            signatureRest.setId(signature.getId());
            signatureRest.setSignatureType(signature.getSignatureType());
            signatureRest.setGroupReviewerCheck(signature.getGroupReviewerCheck());
            signatureRest.setGroupSubmitterCheck(signature.getGroupSubmitterCheck());
            signatureRest.setGroupAdminstratorCheck(signature.getGroupAdminstratorCheck());
        }
        return signatureRest;
    }

    @Override
    public Class<DeduplicationSignature> getModelClass() {
        return DeduplicationSignature.class;
    }
}
