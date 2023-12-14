/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.authority;

import static org.dspace.authorize.ResourcePolicy.TYPE_CUSTOM;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.DSpaceObjectService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.submit.model.AccessConditionConfigurationService;
import org.dspace.submit.model.AccessConditionOption;
import org.json.JSONArray;
import org.json.JSONObject;

public class CopyToBitstreamConsumer implements Consumer {

    private static final MetadataFieldName LICENSE_CONDITION = new MetadataFieldName("oaire", "licenseCondition");
    private static final String CTB = "ctb";
    private static final String SEPARATOR = "XX";
    private static final String EPFL_CTP_LICENSE_NAME = "epflXXlicenseName";

    private static Logger log = LogManager.getLogger(CopyToBitstreamConsumer.class);

    private ItemService itemService;
    private DSpaceObjectService<Bitstream> bitstreamService;
    private MetadataFieldService metadataFieldService;
    private MetadataSchemaService metadataSchemaService;
    private AccessConditionConfigurationService accessConditionConfigurationService;
    private ResourcePolicyService resourcePolicyService;

    private Set<Item> itemsAlreadyProcessed = new HashSet<>();

    @Override
    public void initialize() throws Exception {
        ContentServiceFactory contentServiceFactory = ContentServiceFactory.getInstance();
        itemService = contentServiceFactory.getItemService();
        metadataFieldService = contentServiceFactory.getMetadataFieldService();
        metadataSchemaService = contentServiceFactory.getMetadataSchemaService();
        accessConditionConfigurationService = contentServiceFactory.getAccessConditionConfigurationService();
        resourcePolicyService = contentServiceFactory.getResourcePolicyService();
    }

    @Override
    public void consume(Context context, Event event) throws Exception {
        Item item = (Item) event.getSubject(context);

        if (itemsAlreadyProcessed.contains(item)) {
            return;
        }

        itemsAlreadyProcessed.add(item);

        List<MetadataField> ctbMetadataFields = metadataFieldService
            .findAllInSchema(context, metadataSchemaService.find(context, CTB))
            .stream()
            .filter(mf -> !itemService.getMetadata(item, CTB, mf.getElement(), Item.ANY, Item.ANY).isEmpty())
            .collect(Collectors.toList());

        Optional<Bundle> originalBundle =
            itemService.getBundles(item, "ORIGINAL")
                       .stream()
                       .findFirst();


        if (originalBundle.isPresent()) {

            List<Bitstream> bitstreams = originalBundle.get().getBitstreams();

            for (Bitstream bitstream : bitstreams) {
                consumeBitstream(context, item, bitstream, ctbMetadataFields);
            }
            // we need to be sure that datacite rights metadata are coherent with propagated default policy
            updateDataciteRightsMetadata(context, item, bitstreams);
        }
    }

    private void updateDataciteRightsMetadata(Context context, Item item, List<Bitstream> bitstreams)
        throws SQLException {
        if (bitstreams == null || bitstreams.size() == 0) {
            return;
        }
        updateItemMetadata(context, item, bitstreams, "datacite", "rights", null);
        updateItemMetadata(context, item, bitstreams, "datacite", "available", null);
    }

    private void updateItemMetadata(Context context, Item item, List<Bitstream> bitstreams, String schema,
                                    String element, String qualifier) throws SQLException {
        String mdString = metadataString(schema, element, qualifier);
        List<MetadataValue> itemMetadata =
            itemService.getMetadataByMetadataString(item, mdString);
        List<MetadataValue> metadata =
            bitstreamService.getMetadataByMetadataString(bitstreams.get(0), mdString);
        MetadataValue bitstreamMetadata = (metadata != null && !metadata.isEmpty()) ?
            metadata.get(0) : null;

        boolean valueToBeUpdated = bitstreamMetadata != null
            &&
            (itemMetadata == null
                || itemMetadata.size() == 0
                || !itemMetadata.get(0).getValue().equals(bitstreamMetadata.getValue()));

        if (valueToBeUpdated) {
            itemService.removeMetadataValues(context, item, itemMetadata);
            itemService.addMetadata(context, item, schema, element, qualifier,
                                    null, bitstreamMetadata.getValue());
        }
    }

    private void consumeBitstream(Context context, Item item, Bitstream bitstream,
                                  List<MetadataField> ctbMetadataFields) throws SQLException {
        bitstreamService = ContentServiceFactory.getInstance().getDSpaceObjectService(bitstream);

        List<MetadataField> bitstreamMetadataFields = bitstream.getMetadata()
                                                               .stream()
                                                               .map(MetadataValue::getMetadataField)
                                                               .collect(Collectors.toList());
        List<MetadataField> metadataFieldsToAdd = ctbMetadataFields
            .stream()
            .filter(ctbMF -> bitstreamMetadataFields
                .stream()
                .noneMatch(bmf -> ctbMF.getElement().split(SEPARATOR)[0].equals(bmf.getMetadataSchema().getName())
                    && ctbMF.getElement().split(SEPARATOR)[1].equals(bmf.getElement())))
            .collect(Collectors.toList());

        for (MetadataField field : metadataFieldsToAdd) {

            String ctbMetadataValue = itemService.getMetadataFirstValue(
                item,
                field.getMetadataSchema().getName(),
                field.getElement(),
                field.getQualifier(),
                Item.ANY
            );

            if (accessConditions(field)) {
                propagateAccessConditions(context, item, bitstream, ctbMetadataValue);
                continue;
            }

            // if common metadata is a custom license, but bitstream already has a standard license set,
            // value of custom license must not be set.
            if (EPFL_CTP_LICENSE_NAME.equals(field.getElement()) &&
                hasStandardLicense(bitstream)) {
                continue;
            }

            bitstreamService.setMetadataSingleValue(
                context,
                bitstream,
                field.getElement().split(SEPARATOR)[0],
                field.getElement().split(SEPARATOR)[1],
                field.getQualifier(),
                null,
                ctbMetadataValue
            );
        }
    }

    private void propagateAccessConditions(Context context, Item item, Bitstream bitstream,
                                           String conditionsJson) {

        boolean hasCustomPolicies = bitstream.getResourcePolicies().stream()
                             .anyMatch(rp -> TYPE_CUSTOM.equals(rp.getRpType()));
        if (hasCustomPolicies) {
            return;
        }
        JSONArray array = new JSONArray(conditionsJson);
        for (int i = 0; i < array.length(); i++) {

            JSONObject newAccessCondition = array.getJSONObject(i);

            String name = getString(newAccessCondition, "name");
            String description = getString(newAccessCondition, "description");

            Date startDate = getDate(getString(newAccessCondition, "startDate"));
            Date endDate = getDate(getString(newAccessCondition, "endDate"));

            List<AccessConditionOption> accessConditionOptions = accessConditionConfigurationService
                .getAccessConfigurationById(getString(newAccessCondition, "stepId")).getOptions();

            try {
                resourcePolicyService.removePolicies(context, bitstream, ResourcePolicy.TYPE_CUSTOM);
                if (item.isArchived()) {
                    resourcePolicyService.removePolicies(context, bitstream, ResourcePolicy.TYPE_INHERITED,
                                                         Constants.READ);
                }
                findApplyResourcePolicy(context, accessConditionOptions, bitstream, name, description,
                                        startDate, endDate);
                updateBitstreamMetadata(context, bitstream, "datacite", "rights", null, name);
                if (startDate != null) {
                    updateBitstreamMetadata(context, bitstream, "datacite", "available", null,
                                            getString(newAccessCondition, "startDate"));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void updateBitstreamMetadata(Context context, Bitstream bitstream, String schema, String element,
                                         String qualifier, String metadataValue) throws SQLException {
        String mdString = metadataString(schema, element, qualifier);
        List<MetadataValue> dataciteRights =
            bitstreamService.getMetadataByMetadataString(bitstream, mdString);
        bitstreamService.removeMetadataValues(context, bitstream, dataciteRights);
        bitstreamService.addMetadata(context, bitstream, schema, element, qualifier,
                                     null, metadataValue);
    }

    private static String metadataString(String schema, String element, String qualifier) {
        return Stream.of(schema, element, qualifier).filter(StringUtils::isNotBlank)
                     .collect(Collectors.joining("."));
    }

    private static String getString(JSONObject newAccessCondition, String key) {
        return newAccessCondition.has(key) ? newAccessCondition.getString(key) : null;
    }

    private void findApplyResourcePolicy(Context context,
                                         List<AccessConditionOption> accessConditionOptions,
                                         DSpaceObject obj, String name,
                                         String description, Date startDate, Date endDate)
        throws SQLException, AuthorizeException, ParseException {
        boolean found = false;
        for (AccessConditionOption accessConditionOption : accessConditionOptions) {
            if (!found && accessConditionOption.getName().equalsIgnoreCase(name)) {
                accessConditionOption.createResourcePolicy(context, obj, name, description, startDate, endDate);
                found = true;
            }
        }
    }


    private static Date getDate(String date) {
        if (StringUtils.isBlank(date)) {
            return null;
        }
        try {
            return DateUtils.parseDate(date, "yyyy-MM-dd");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean accessConditions(MetadataField field) {
        return "accessconditions".equals(field.getElement())
            && "value".equals(field.getQualifier());
    }

    @Override
    public void end(Context context) throws Exception {
        itemsAlreadyProcessed.clear();
    }

    @Override
    public void finish(Context context) throws Exception {

    }

    private boolean hasStandardLicense(Bitstream bitstream) {
        String value = bitstreamService.getMetadataFirstValue(bitstream, LICENSE_CONDITION, Item.ANY);
        return StringUtils.isNotBlank(value) && !value.startsWith("http");
    }
}
