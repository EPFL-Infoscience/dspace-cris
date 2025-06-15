/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.validation;

import static org.dspace.core.Constants.DEFAULT_BUNDLE_NAME;
import static org.dspace.validation.service.ValidationService.OPERATION_PATH_SECTIONS;
import static org.dspace.validation.util.ValidationUtils.addError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.util.DCInput;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.MetadataValue;
import org.dspace.content.authority.service.MetadataAuthorityService;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.submit.model.UploadConfiguration;
import org.dspace.submit.model.UploadConfigurationService;
import org.dspace.validation.model.ValidationError;

/**
 * Execute file required check validation
 *
 * @author Luigi Andrea Pascarelli (luigiandrea.pascarelli at 4science.it)
 * @author Luca Giamminonni (luca.giamminonni at 4sciente.it)
 */
public class UploadValidator implements SubmissionStepValidator {

    private static final String ERROR_VALIDATION_FILE_REQUIRED = "error.validation.filerequired";
    private static final String ERROR_VALIDATION_REQUIRED = "error.validation.required";
    private static final String ERROR_VALIDATION_REGEX = "error.validation.regex";
    private static final String ERROR_VALIDATION_NOT_REPEATABLE = "error.validation.notrepeatable";

    private ItemService itemService;

    private UploadConfigurationService uploadConfigurationService;

    private MetadataAuthorityService metadataAuthorityService;

    private BitstreamService bitstreamService;

    private String name;

    @Override
    public List<ValidationError> validate(Context context, InProgressSubmission<?> obj, SubmissionStepConfig config) {
        List<ValidationError> errors = new ArrayList<>();
        UploadConfiguration uploadConfig = uploadConfigurationService.getMap().get(config.getId());
        List<Bundle> bundles = obj.getItem().getBundles(DEFAULT_BUNDLE_NAME);
        List<Bitstream> bitstreams = bundles.isEmpty()
            ? new ArrayList<>()
            : bundles.get(0).getBitstreams();

        if (uploadConfig.isRequired() && bitstreams.isEmpty()) {
            addError(errors, ERROR_VALIDATION_FILE_REQUIRED, "/" + OPERATION_PATH_SECTIONS + "/" + config.getId());
        }

        bitstreams.forEach(bitstream -> {
            validateMetadata(bitstream, config.getId(), uploadConfig, errors);
            validateAccessConditions(bitstream, config.getId(), uploadConfig, errors);
        });
        return errors;
    }

    private void validateMetadata(Bitstream bitstream, String configId,
                                  UploadConfiguration uploadConfig, List<ValidationError> errors) {
        DCInputSet inputByFormName = getDCInputSet(uploadConfig.getMetadata());
        Arrays.stream(inputByFormName.getFields())
            .flatMap(Arrays::stream)
            .filter(input -> !input.isQualdropValue())
            .forEach(
                input -> {
                    List<MetadataValue> metadataValues =
                        bitstreamService.getMetadataByMetadataString(bitstream, input.getFieldName());
                    validateMetadataValues(metadataValues, input, configId, errors);
                }
            );
    }

    private void validateAccessConditions(Bitstream bitstream, String configId,
                                          UploadConfiguration uploadConfig, List<ValidationError> errors) {
        if(uploadConfig.isAccessConditionsRequired()) {
            boolean foundAccessCondition = false;
            for (ResourcePolicy rp : bitstream.getResourcePolicies()) {
                if (StringUtils.isNotBlank(rp.getRpName()) && ResourcePolicy.TYPE_CUSTOM.equals(rp.getRpType())) {
                    foundAccessCondition = true;
                    break;
                }
            }

            if (!foundAccessCondition) {
                addError(
                        errors, ERROR_VALIDATION_REQUIRED,
                        "/" + OPERATION_PATH_SECTIONS + "/" + configId + "/accessConditions"
                );
            }
        }
    }

    private void validateMetadataValues(List<MetadataValue> metadataValues, DCInput input,
                                        String configId, List<ValidationError> errors) {
        if ((input.isRequired() && metadataValues.isEmpty()) && input.isVisible(DCInput.SUBMISSION_SCOPE)) {
            addError(
                errors, ERROR_VALIDATION_REQUIRED,
                "/" + OPERATION_PATH_SECTIONS + "/" + configId + "/" + input.getFieldName()
            );
        }

        if (!input.isRepeatable() && metadataValues.size() > 1) {
            for (int i = 0; i < metadataValues.size(); i++) {
                addError(
                    errors, ERROR_VALIDATION_NOT_REPEATABLE,
                    "/" + OPERATION_PATH_SECTIONS + "/" + configId + "/" + input.getFieldName() + "/" + i
                );
            }
        }

        metadataValues.stream()
            .filter(md -> !input.validate(md.getValue()))
            .forEach(
                md -> addError(
                    errors, ERROR_VALIDATION_REGEX,
                    "/" + OPERATION_PATH_SECTIONS + "/" + configId + "/"
                        + input.getFieldName() + "/" + md.getPlace()
                )
            );
    }

    private DCInputSet getDCInputSet(String formName) {
        try {
            return new DCInputsReader().getInputsByFormName(formName);
        } catch (DCInputsReaderException e) {
            throw new RuntimeException(e);
        }
    }

    public ItemService getItemService() {
        return itemService;
    }

    public void setItemService(ItemService itemService) {
        this.itemService = itemService;
    }

    public UploadConfigurationService getUploadConfigurationService() {
        return uploadConfigurationService;
    }

    public void setUploadConfigurationService(UploadConfigurationService uploadConfigurationService) {
        this.uploadConfigurationService = uploadConfigurationService;
    }

    public MetadataAuthorityService getMetadataAuthorityService() {
        return metadataAuthorityService;
    }

    public void setMetadataAuthorityService(MetadataAuthorityService metadataAuthorityService) {
        this.metadataAuthorityService = metadataAuthorityService;
    }

    public BitstreamService getBitstreamService() {
        return bitstreamService;
    }

    public void setBitstreamService(BitstreamService bitstreamService) {
        this.bitstreamService = bitstreamService;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
