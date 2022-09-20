/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.SubmissionRepeatableFieldsRest;
import org.dspace.app.rest.model.wrapper.SubmissionRepeatableFields;
import org.dspace.app.rest.projection.Projection;
import org.springframework.stereotype.Component;


/**
 * This converter is responsible for transforming a SubmissionRepeatableFields to the REST
 * representation SubmissionRepeatableFieldsRest and vice versa
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 */
@Component
public class SubmissionRepeatableFieldsConverter
    implements DSpaceConverter<SubmissionRepeatableFields, SubmissionRepeatableFieldsRest> {

    /**
     * Convert a {@link SubmissionRepeatableFields} to its REST representation
     * @param modelObject   - the SubmissionRepeatableFields object to convert
     * @param projection    - the projection
     * @return the corresponding SubmissionRepeatableFieldsRest object
     */
    @Override
    public SubmissionRepeatableFieldsRest convert(SubmissionRepeatableFields modelObject, Projection projection) {
        SubmissionRepeatableFieldsRest rest = new SubmissionRepeatableFieldsRest();
        rest.setItemId(modelObject.getItemId());
        rest.setRepeatableFields(modelObject.getRepeatableFields());

        return rest;
    }

    @Override
    public Class<SubmissionRepeatableFields> getModelClass() {
        return SubmissionRepeatableFields.class;
    }

}
