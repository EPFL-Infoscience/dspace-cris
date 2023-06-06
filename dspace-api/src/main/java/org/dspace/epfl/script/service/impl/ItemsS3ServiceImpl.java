/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service.impl;

import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.stream.Stream;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.dspace.epfl.client.EpflItemsClient;
import org.dspace.epfl.script.service.ItemsS3Service;
import org.springframework.beans.factory.annotation.Autowired;

public class ItemsS3ServiceImpl implements ItemsS3Service {

    @Autowired
    private EpflItemsClient itemsClient;

    @Override
    public Stream<String> getAllItemsKeys() {
        return streamOf(itemsClient.iterateObjects())
            .map(S3ObjectSummary::getKey);
    }

    @Override
    public InputStream getObject(String key) {
        return itemsClient.get(key);
    }

    private <T> Stream<T> streamOf(Iterator<T> iterator) {
        return stream(spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

}
