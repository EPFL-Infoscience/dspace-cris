/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xoai.app;

import static org.dspace.core.CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.lyncode.xoai.dataprovider.xml.xoai.Element;
import com.lyncode.xoai.dataprovider.xml.xoai.Metadata;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.xoai.util.ItemUtils;
import org.springframework.beans.factory.annotation.Autowired;


public class RorItemCompilePlugin implements XOAIExtensionItemCompilePlugin {

    @Autowired
    private ItemService itemService;

    @Override
    public Metadata additionalMetadata(Context context, Metadata metadata, Item item) {
        List<MetadataValue> metadataValues = item.getMetadata().stream().filter(metadataValue ->
                metadataValue.getMetadataField().toString('.')
                .equals("oairecerif.author.affiliation")).collect(Collectors.toList());
        Element organization = ItemUtils.create("organization");
        Element identifier = ItemUtils.create("identifier");
        organization.getElement().add(identifier);
        for (MetadataValue metadataValue : metadataValues) {
            String orgUnitAuthority = metadataValue.getAuthority();
            MetadataValue orgUnitRorMetadata = null;
            if (orgUnitAuthority != null) {
                UUID uuid;
                Item orgUnitItem;
                try {
                    uuid = UUID.fromString(orgUnitAuthority);
                    orgUnitItem = itemService.find(context, uuid);
                    if (orgUnitItem != null) {
                        orgUnitRorMetadata = orgUnitItem.getMetadata().stream()
                            .filter(orgUnitMetadataValue -> orgUnitMetadataValue.getMetadataField().toString('.')
                                .equals("organization.identifier.ror")).findFirst().orElse(null);
                    }
                } catch (IllegalArgumentException e) {
                    orgUnitRorMetadata = null;
                } catch (SQLException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            if (orgUnitRorMetadata != null) {
                identifier.getField().add(ItemUtils.createValue("ror", orgUnitRorMetadata.getValue()));
            } else {
                identifier.getField().add(ItemUtils.createValue("ror", PLACEHOLDER_PARENT_METADATA_VALUE));
            }
        }
        if (!metadataValues.isEmpty()) {
            metadata.getElement().add(organization);
        }
        return metadata;
    }
}
