/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.importer.external.metadatamapping.contributor;

import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.dspace.importer.external.metadatamapping.MetadataFieldMapping;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.dspace.importer.external.service.components.dto.PlainMetadataSourceDto;
import org.dspace.util.SimpleMapConverter;

/**
 * Metadata contributor that takes an PlainMetadataSourceDto instance and turns it into a
 * collection of metadata, having returned value converted according to a mapping
 * passed to class instance
 *
 * @author Corrado Lombardi (corrado.lombardi at 4science dot com)
 */
public class SimpleMappedMetadataContributor implements MetadataContributor<PlainMetadataSourceDto> {

    private final MetadataContributor<PlainMetadataSourceDto> innerContributor;

    private final SimpleMapConverter mapConverter;

    public SimpleMappedMetadataContributor(MetadataContributor<PlainMetadataSourceDto> innerContributor,
                                           SimpleMapConverter mapConverter) {
        this.innerContributor = innerContributor;
        this.mapConverter = mapConverter;
    }


    @Override
    public void setMetadataFieldMapping(
        MetadataFieldMapping<PlainMetadataSourceDto, MetadataContributor<PlainMetadataSourceDto>> rt) {

    }

    @Override
    public Collection<MetadatumDTO> contributeMetadata(PlainMetadataSourceDto t) {
        final Collection<MetadatumDTO> metadata = innerContributor.contributeMetadata(t);
        for (final MetadatumDTO metadatum : metadata) {
            if (StringUtils.isBlank(metadatum.getValue())) {
                metadatum.setValue("");
                continue;
            }
            metadatum.setValue(mapConverter.getValue(metadatum.getValue()));
        }
        return metadata;
    }
}
