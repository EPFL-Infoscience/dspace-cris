/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.xoai.app;

import java.util.List;

import com.lyncode.xoai.dataprovider.xml.xoai.Element;
import com.lyncode.xoai.dataprovider.xml.xoai.Metadata;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.CrisConstants;
import org.dspace.importer.external.service.DoiCheck;
import org.dspace.xoai.util.ItemUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class LinkedItemsCompilePlugin implements XOAIExtensionItemCompilePlugin {

    @Autowired
    private ItemService itemService;
    @Override
    public Metadata additionalMetadata(Context context, Metadata metadata, Item item) {


        List<MetadataValue> relationIdentifiers = itemService
            .getMetadata(item, "dc", "relation", "identifier",
                         Item.ANY);
        List<MetadataValue> relationType = itemService
            .getMetadata(item, "epfl", "relationpublication", "type",
                         Item.ANY);

        Element linkedItems = ItemUtils.create("linkedItems");
        for (int i = 0; i < relationIdentifiers.size(); i++) {
            String link = relationIdentifiers.get(i).getValue();
            if (CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE.equals(link)) {
                continue;
            }

            Element.Field linkField = ItemUtils.createValue("link", link);
            Element.Field identifierTypeField = ItemUtils.createValue("identifierType",
                                                                      DoiCheck.isDoi(link) ? "DOI" : "URL");

            Element linkedItem = ItemUtils.create("linkedItem");
            linkedItem.getField().add(linkField);
            linkedItem.getField().add(identifierTypeField);

            if (relationType.size() > i && StringUtils.isNotBlank(relationType.get(i).getValue())
                && !CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE.equals(relationType.get(i).getValue())) {
                linkedItem.getField().add(ItemUtils.createValue("type", relationType.get(i).getValue()));
            }
            linkedItems.getElement().add(linkedItem);
        }

        Element other = otherElement(metadata);
        other.getElement().add(linkedItems);
        return metadata;
    }

    private static Element otherElement(Metadata metadata) {
        Element other;
        List<Element> elements = metadata.getElement();
        if (ItemUtils.getElement(elements, "others") != null) {
            other = ItemUtils.getElement(elements, "others");
        } else {
            other = ItemUtils.create("others");
        }
        return other;
    }
}
