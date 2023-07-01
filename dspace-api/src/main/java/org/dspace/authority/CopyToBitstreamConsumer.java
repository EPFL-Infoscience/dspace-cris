/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.authority;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.DSpaceObjectService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

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

    @Override
    public void initialize() throws Exception {
        ContentServiceFactory contentServiceFactory = ContentServiceFactory.getInstance();
        itemService = contentServiceFactory.getItemService();
        metadataFieldService = contentServiceFactory.getMetadataFieldService();
        metadataSchemaService = contentServiceFactory.getMetadataSchemaService();
    }

    @Override
    public void consume(Context context, Event event) throws Exception {
        Item item = (Item) event.getSubject(context);

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

    @Override
    public void end(Context context) throws Exception {

    }

    @Override
    public void finish(Context context) throws Exception {

    }

    private boolean hasStandardLicense(Bitstream bitstream) {
        String value = bitstreamService.getMetadataFirstValue(bitstream, LICENSE_CONDITION, Item.ANY);
        return StringUtils.isNotBlank(value) && !value.startsWith("http");
    }
}
