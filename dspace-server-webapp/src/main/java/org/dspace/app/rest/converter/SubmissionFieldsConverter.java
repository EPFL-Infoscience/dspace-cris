/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.SubmissionFieldsRest;
import org.dspace.app.rest.model.wrapper.SubmissionFields;
import org.dspace.app.rest.projection.Projection;
import org.springframework.stereotype.Component;

/**
 * This converter is responsible for transforming a SubmissionFields to the REST
 * representation SubmissionFieldsRest and vice versa
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
@Component
public class SubmissionFieldsConverter
    implements DSpaceConverter<SubmissionFields, SubmissionFieldsRest> {

    /**
     * Convert a {@link SubmissionFields} to its REST representation
     * @param modelObject   - the SubmissionFields object to convert
     * @param projection    - the projection
     * @return the corresponding SubmissionFieldsRest object
     */
    @Override
    public SubmissionFieldsRest convert(SubmissionFields modelObject, Projection projection) {
        SubmissionFieldsRest rest = new SubmissionFieldsRest();
        rest.setItemId(modelObject.getItemId());
        rest.setRepeatableFields(modelObject.getRepeatableFields());
        rest.setNestedFields(modelObject.getNestedFields());

        return rest;
    }

    @Override
    public Class<SubmissionFields> getModelClass() {
        return SubmissionFields.class;
    }

}
