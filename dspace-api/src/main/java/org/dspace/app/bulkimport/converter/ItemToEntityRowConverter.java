/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.bulkimport.converter;

import java.sql.SQLException;
import java.util.List;
import javax.annotation.PostConstruct;

import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.dspace.app.bulkimport.model.EntityRow;
import org.dspace.app.bulkimport.model.MetadataGroup;
import org.dspace.app.bulkimport.model.UploadDetails;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.content.vo.MetadataValueVO;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

public class ItemToEntityRowConverter implements EntityRowConverter<Item> {

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private ItemService itemService;

    private DCInputsReader reader;

    @PostConstruct
    private void postConstruct() throws DCInputsReaderException {
        this.reader = new DCInputsReader();
    }

    @Override
    public EntityRow convert(Context context, Collection collection, Item item) {

        if (isNotInCollection(context, item, collection)) {
            throw new IllegalArgumentException("It is not possible to export items from two different collections: "
                + "item " + item.getID() + " is not in collection " + collection.getID());
        }

        String id = item.getID().toString();
        boolean discoverable = item.isDiscoverable();

        ListValuedMap<String, MetadataValueVO> metadata = getNotNestedMetadataValues(context, collection, item);
        List<MetadataGroup> metadataGroups = getMetadataGroups(context, collection, item);
        List<UploadDetails> uploadDetails = getUploadDetails(context, collection, item);

        return new EntityRow(id, discoverable, metadata, List.of(), List.of());
    }

    private ListValuedMap<String, MetadataValueVO> getNotNestedMetadataValues(Context context,
        Collection collection, Item item) {

        ListValuedMap<String, MetadataValueVO> metadataValues = new ArrayListValuedHashMap<>();

        List<String> metadataFields = getSubmissionFormMetadata(collection);
        for (String metadataField : metadataFields) {
            getMetadataValues(item, metadataField).stream()
                .map(metadataValue -> new MetadataValueVO(metadataValue))
                .forEach(metadataValue -> metadataValues.put(metadataField, metadataValue));
        }

        return metadataValues;
    }

    private List<MetadataGroup> getMetadataGroups(Context context, Collection collection, Item item) {
        // TODO Auto-generated method stub
        return null;
    }

    private List<UploadDetails> getUploadDetails(Context context, Collection collection, Item item) {
        // TODO Auto-generated method stub
        return null;
    }

    private List<MetadataValue> getMetadataValues(Item item, String metadataField) {
        return itemService.getMetadataByMetadataString(item, metadataField);
    }

    private boolean isNotInCollection(Context context, Item item, Collection collection) {
        return !collection.equals(findCollection(context, item));
    }

    private Collection findCollection(Context context, Item item) {
        try {

            Collection collection = collectionService.findByItem(context, item);
            if (collection == null) {
                throw new IllegalArgumentException("No collection found for item with id: " + item.getID());
            }
            return collection;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getSubmissionFormMetadata(Collection collection) {
        try {
            return this.reader.getSubmissionFormMetadata(collection);
        } catch (DCInputsReaderException e) {
            throw new RuntimeException("An error occurs reading the input configuration by collection", e);
        }
    }

}
