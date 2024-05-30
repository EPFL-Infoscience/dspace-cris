/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.dataquality.utils.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.dataquality.utils.service.DCInputUtils;
import org.dspace.app.util.DCInput;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DCInputUtilsImpl implements DCInputUtils {

    private static final Logger log = LoggerFactory.getLogger(DCInputUtilsImpl.class);

    protected static DCInputUtilsImpl instance;

    public static final DCInputUtilsImpl getInstance() {
        if (instance == null) {
            instance = new DCInputUtilsImpl();
        }
        return instance;
    }

    private DCInputUtilsImpl() {}

    public Optional<DCInput> findParent(DCInputSet inputSet, String fieldName) {
        DCInput[][] inputs = inputSet.getFields();
        for (int i = 0; i < inputs.length; i++) {
            for (int j = 0; j < inputs[i].length; j++) {
                DCInput field = inputs[i][j];
                if (StringUtils.equalsAny(field.getInputType(), "group", "inline-group")) {
                    if (hasChildField(inputSet, fieldName, field)) {
                        return Optional.of(field);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean hasChildField(DCInputSet inputSet, String fieldName, DCInput field) {
        return getChildField(inputSet, field, fieldName).isPresent();
    }

    @Override
    public boolean hasParent(DCInputSet inputSet, String fieldName) {
        return findParent(inputSet, fieldName).isPresent();
    }

    @Override
    public Optional<DCInput> getChildField(DCInputSet inputSet, DCInput field, String fieldName) {
        String formName =
            inputSet.getFormName() + "-" +
                Utils.standardize(field.getSchema(), field.getElement(), field.getQualifier(), "-");
        try {
            DCInputSet inputConfig = new DCInputsReader().getInputsByFormName(formName);
            return inputConfig.getField(fieldName);
        } catch (DCInputsReaderException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<String> getMetadataFields(DCInputSet inputSet) {
        List<String> metadataFields = new ArrayList<>();
        DCInput[][] inputs = inputSet.getFields();
        for (int i = 0; i < inputs.length; i++) {
            for (int j = 0; j < inputs[i].length; j++) {
                DCInput field = inputs[i][j];
                if (StringUtils.equals(field.getInputType(), "qualdrop_value")) {
                    List<String> pairs = field.getPairs();
                    for (int k = 0; k < pairs.size(); k += 2) {
                        String qualifier = pairs.get(k + 1);
                        metadataFields.add(
                            Utils.standardize(field.getSchema(), field.getElement(), qualifier, ".")
                        );
                    }
                } else if (StringUtils.equalsAny(field.getInputType(), "group", "inline-group")) {
                    appendNestedMetadataFields(inputSet, metadataFields, field);
                } else {
                    metadataFields.add(field.getFieldName());
                }
            }
        }
        return metadataFields;
    }

    private void appendNestedMetadataFields(DCInputSet inputSet, List<String> fields, DCInput field) {
        String formName = inputSet.getFormName() + "-" + Utils.standardize(field.getSchema(),
                                                                           field.getElement(), field.getQualifier(),
                                                                           "-");
        try {
            DCInputSet inputConfig = new DCInputsReader().getInputsByFormName(formName);
            Arrays.stream(inputConfig.getFields())
                  .forEach(dcInputs ->
                               Arrays.stream(dcInputs)
                                     .forEach(dcInput -> {
                                         if (StringUtils.equalsAny(dcInput.getInputType(), "group",
                                                                   "inline-group")) {
                                             appendNestedMetadataFields(inputSet, fields, dcInput);
                                         } else {
                                             fields.add(dcInput.getFieldName());
                                         }
                                     }));
        } catch (DCInputsReaderException e) {
            log.error(e.getMessage(), e);
        }
    }

}
