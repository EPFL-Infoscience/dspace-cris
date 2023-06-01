/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import org.dspace.app.deduplication.model.DeduplicationMergeTarget;
import org.dspace.app.rest.model.DeduplicationMergeTargetRest;
import org.dspace.app.rest.projection.Projection;
import org.springframework.stereotype.Component;

/**
 * This class provides the method to convert a DeduplicationMergeTarget to its REST representation,
 * the DeduplicationMergeTargetRest
 * 
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
@Component
public class DeduplicationMergeTargetConverter
        implements DSpaceConverter<DeduplicationMergeTarget, DeduplicationMergeTargetRest> {

    @Override
    public DeduplicationMergeTargetRest convert(DeduplicationMergeTarget dedupMergeTarget, Projection projection) {

        DeduplicationMergeTargetRest rest = new DeduplicationMergeTargetRest();
        rest.setProjection(projection);

        if (dedupMergeTarget != null) {
            rest.setAllowedTargets(dedupMergeTarget.getAllowedTargets());
        }

        return rest;
    }

    @Override
    public Class<DeduplicationMergeTarget> getModelClass() {
        return DeduplicationMergeTarget.class;
    }
}
