/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.metadatamapping.contributor;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.dspace.importer.external.metadatamapping.MetadataFieldMapping;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;

public class ReplaceStringXPathMetadataContributor<T> implements MetadataContributor<T> {

    private final MetadataContributor<T> innerContributor;

    private String stringToBeReplaced;

    private String stringToReplaceWith;

    public ReplaceStringXPathMetadataContributor(MetadataContributor<T> innerContributor) {
        this.innerContributor = innerContributor;
    }

    @Override
    public void setMetadataFieldMapping(MetadataFieldMapping<T, MetadataContributor<T>> rt) {

    }

    @Override
    public Collection<MetadatumDTO> contributeMetadata(T t) {
        final Collection<MetadatumDTO> metadata = innerContributor.contributeMetadata(t);
        return metadata.stream()
                       .filter(Objects::nonNull)
                       .filter(metadatum -> StringUtils.isNotBlank(metadatum.getValue()))
                       .map(this::replaceValue)
                       .collect(Collectors.toList());
    }

    private MetadatumDTO replaceValue(MetadatumDTO metadatum) {
        String replacedValue = metadatum.getValue().replaceAll(stringToBeReplaced, stringToReplaceWith);
        metadatum.setValue(replacedValue);
        return metadatum;
    }

    public void setStringToBeReplaced(String stringToBeReplaced) {
        this.stringToBeReplaced = stringToBeReplaced;
    }

    public void setStringToReplaceWith(String stringToReplaceWith) {
        this.stringToReplaceWith = stringToReplaceWith;
    }
}
