/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import org.dspace.app.deduplication.utils.DuplicateInfo;
import org.dspace.app.rest.model.DeduplicationSetRest;
import org.dspace.app.rest.projection.Projection;
import org.springframework.stereotype.Component;

/**
 * This class provides the method to convert a DuplicateInfo to its REST representation,
 * the DeduplicationSetRest
 * 
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */
@Component
public class DeduplicationSetConverter
        implements DSpaceConverter<DuplicateInfo, DeduplicationSetRest> {

    @Override
    public DeduplicationSetRest convert(DuplicateInfo group, Projection projection) {
        DeduplicationSetRest groupRest = new DeduplicationSetRest();
        groupRest.setProjection(projection);
        if (group != null) {
            groupRest.setId(group.getSignatureId() + ":" + group.getGroupChecksum());
            groupRest.setSignatureId(group.getSignatureId());
            groupRest.setSetChecksum(group.getGroupChecksum());
            groupRest.setOtherSetIds(group.getOtherGroupChecksums());
        }
        return groupRest;
    }

    @Override
    public Class<DuplicateInfo> getModelClass() {
        return DuplicateInfo.class;
    }
}
