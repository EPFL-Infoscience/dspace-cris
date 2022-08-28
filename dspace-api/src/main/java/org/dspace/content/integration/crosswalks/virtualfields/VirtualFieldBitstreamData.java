/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.integration.crosswalks.virtualfields;

import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.util.UUIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Virtual field mapper used to return bitstream's metadata value, or a single attribute
 * of the bitstream (so far just size and id are supported)
 *
 * syntax is virtual.bitstreamdata.#bundle-name#.#attribute-type#.#attribute-value#
 * attribute-type could be set with metadata, to recover bitstream's metadata (just first value), or
 * data to recover a bitstream attribute.
 * Examples:
 *          virtual.bitstreamdata.original.metadata.dc-type
 *          virtual.bitstreamdata.original.data.sizeByte
 *
 */
public class VirtualFieldBitstreamData
    implements VirtualField {

    private final static Logger LOGGER = LoggerFactory.getLogger(VirtualFieldBitstream.class);

    private final ItemService itemService;
    private final BitstreamService bitstreamService;

    public VirtualFieldBitstreamData(ItemService itemService, BitstreamService bitstreamService) {
        this.itemService = itemService;
        this.bitstreamService = bitstreamService;
    }

    @Override
    public String[] getMetadata(Context context, Item item, String fieldName) {


        String[] virtualFieldName = fieldName.split("\\.");
        if (virtualFieldName.length != 5) {
            LOGGER.warn("Invalid bitstream data virtual field: " + fieldName);
            return new String[] {};
        }

        List<Bitstream> bitstreams;
        String bundleName = virtualFieldName[2];
        try {
            bitstreams = findBitstreams(item, bundleName);
        } catch (Exception e) {
            LOGGER.warn("No bitstream found for item {} and bundle name {}",
                        item.getID(), bundleName);
            return new String[0];
        }

        return dataFromBitstreams(virtualFieldName, bitstreams, virtualFieldName[3]);
    }

    private String[] dataFromBitstreams(String[] virtualFieldName, List<Bitstream> bitstreamList,
                                        String attributeType) {


        Optional<BitstreamAttribute> bitstreamAttribute = BitstreamAttribute.of(attributeType);

        if (bitstreamAttribute.isEmpty()) {
            LOGGER.warn("Invalid attribute type for bitstream. Specified {}, supported values are {}",
                        attributeType, BitstreamAttribute.attributeTypeValues());
            return new String[0];
        }

        return bitstreamList
            .stream()
            .map(bitstream -> bistreamAttributeValue(virtualFieldName[4],
                                                     bitstream,
                                                     bitstreamAttribute.get()))
            .toArray(String[]::new);

    }

    private String bistreamAttributeValue(String attribute, Bitstream bitstream,
                                          BitstreamAttribute bitstreamAttribute) {

        if (StringUtils.isBlank(attribute)) {
            return PLACEHOLDER_PARENT_METADATA_VALUE;
        }
        String value = bitstreamAttribute.value(bitstreamService, bitstream, attribute);

        return StringUtils.defaultIfBlank(value, PLACEHOLDER_PARENT_METADATA_VALUE);
    }

    private enum BitstreamAttribute {

        DATA("data") {

            @Override
            public String value(BitstreamService itemService, Bitstream bitstream, String bitstreamAttribute) {
                // FIXME: could be improved allowing retrieval of all, or a subset, of allowed bitstream attribute,
                // without hardcoding attributes
                switch (bitstreamAttribute) {
                    case "id" :
                        return UUIDUtils.toString(bitstream.getID());
                    case "sizeBytes" :
                        return String.valueOf(bitstream.getSizeBytes());
                    default:
                        return "";
                }
            }
        },
        METADATA("metadata") {

            @Override
            public String value(BitstreamService itemService, Bitstream bitstream, String metadata) {
                // FIXME: now only first metadata value is returned, if needed for repeatable metadata,
                //  values could be concatenated
                //  or a positional indicator can be added to attribute value
                List<MetadataValue> metadataValueList = itemService
                    .getMetadataByMetadataString(bitstream, metadata
                        .replaceAll("-", "."));
                if (metadataValueList.isEmpty()) {
                    return "";
                }
                return metadataValueList.get(0).getValue();
            }
        };

        private final String attributeType;

        BitstreamAttribute(String attributeType) {
            this.attributeType = attributeType;
        }

        public static Optional<BitstreamAttribute> of(String attributeType) {
            return Arrays.stream(BitstreamAttribute.values())
                .filter(v -> v.attributeType.equals(attributeType))
                .findFirst();
        }

        public static Object attributeTypeValues() {
            return Arrays.stream(values())
                .map(v -> v.attributeType)
                .collect(Collectors.joining(", "));
        }

        public abstract String value(BitstreamService itemService, Bitstream bitstream, String attributeValue);
    }


    private List<Bitstream> findBitstreams(Item item, String bundleName) throws Exception {
        List<Bundle> bundles = itemService.getBundles(item, bundleName.toUpperCase());
        if (CollectionUtils.isEmpty(bundles)) {
            return null;
        }

        return bundles.stream()
                      .flatMap(b -> b.getBitstreams().stream())
                      .collect(Collectors.toList());


    }

    private boolean hasTypeEqualsTo(Bitstream bitstream, String type) {
        return type.equals(bitstreamService.getMetadataFirstValue(bitstream, "dc", "type", null, Item.ANY));
    }
}
