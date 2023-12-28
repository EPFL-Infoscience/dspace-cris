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
import java.util.Optional;
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


public class PersonIdentifiersItemCompilePlugin implements XOAIExtensionItemCompilePlugin {

    @Autowired
    private ItemService itemService;

    @Override
    public Metadata additionalMetadata(Context context, Metadata metadata, Item item) {
        List<MetadataValue> metadataValues = item.getMetadata().stream().filter(metadataValue ->
                metadataValue.getMetadataField().toString('.')
                .equals("dc.contributor.author")).collect(Collectors.toList());
        Element person;
        Element identifier;
        Optional<Element> personElementFromMetadata = metadata.getElement().stream()
                .filter(element -> element.getName().equals("person")).findFirst();

        if (personElementFromMetadata.isEmpty()) {
            person = ItemUtils.create("person");
            identifier = ItemUtils.create("identifier");
        } else {
            person = personElementFromMetadata.get();
            Optional<Element> identifierElementFromMetadata = metadata.getElement().stream()
                    .filter(element -> element.getName().equals("person")).findFirst();
            identifier = identifierElementFromMetadata.orElseGet(() -> ItemUtils.create("identifier"));
        }
        person.getElement().add(identifier);
        for (MetadataValue metadataValue : metadataValues) {
            String personAuthority = metadataValue.getAuthority();
            MetadataValue scopusMetadataValue = null;
            MetadataValue ridMetadataValue = null;
            MetadataValue orcidMetadataValue = null;
            if (personAuthority != null) {
                UUID uuid;
                Item personItem;
                try {
                    uuid = UUID.fromString(personAuthority);
                    personItem = itemService.find(context, uuid);
                    if (personItem != null) {
                        scopusMetadataValue = personItem.getMetadata().stream()
                                .filter(orgUnitMetadataValue -> orgUnitMetadataValue.getMetadataField().toString('.')
                                        .equals("person.identifier.scopus-author-id")).findFirst().orElse(null);
                        ridMetadataValue = personItem.getMetadata().stream()
                                .filter(orgUnitMetadataValue -> orgUnitMetadataValue.getMetadataField().toString('.')
                                        .equals("person.identifier.rid")).findFirst().orElse(null);
                        orcidMetadataValue = personItem.getMetadata().stream()
                                .filter(orgUnitMetadataValue -> orgUnitMetadataValue.getMetadataField().toString('.')
                                        .equals("person.identifier.orcid")).findFirst().orElse(null);
                    }
                } catch (IllegalArgumentException e) {
                    scopusMetadataValue = null;
                    ridMetadataValue = null;
                    orcidMetadataValue = null;
                } catch (SQLException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            if (scopusMetadataValue != null) {
                identifier.getField().add(ItemUtils.createValue("scopus-author-id", scopusMetadataValue.getValue()));
            } else {
                identifier.getField().add(ItemUtils.createValue("scopus-author-id", PLACEHOLDER_PARENT_METADATA_VALUE));
            }
            if (ridMetadataValue != null) {
                identifier.getField().add(ItemUtils.createValue("rid", ridMetadataValue.getValue()));
            } else {
                identifier.getField().add(ItemUtils.createValue("rid", PLACEHOLDER_PARENT_METADATA_VALUE));
            }
            if (orcidMetadataValue != null) {
                identifier.getField().add(ItemUtils.createValue("orcid", orcidMetadataValue.getValue()));
            } else {
                identifier.getField().add(ItemUtils.createValue("orcid", PLACEHOLDER_PARENT_METADATA_VALUE));
            }
        }
        if (!metadataValues.isEmpty()) {
            metadata.getElement().add(person);
        }
        return metadata;
    }
}
