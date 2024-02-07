/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.metadatamapping.contributor;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dspace.importer.external.metadatamapping.MetadataFieldMapping;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.junit.Test;

/**
 * Unit tests for {@link ReplaceStringXPathMetadataContributor}
 */
public class ReplaceStringXPathMetadataContributorTest {

    @Test
    public void valuesAreMapped() {
        MetadataContributor<String> innerContributor = metadataContributorReturning(Arrays.asList(
            metadatum("dc", "identifier", "isi1", "WOS:123456789"),
            metadatum("dc", "identifier", "isi2", "123456789"),
            metadatum("dc", "identifier", "isi3", "WOS:123456WOS:789"),
            metadatum("dc", "identifier", "isi4", ""),
            metadatum("dc", "identifier", "isi5", null)
        ));

        ReplaceStringXPathMetadataContributor contributor = new ReplaceStringXPathMetadataContributor(innerContributor);
        contributor.setStringToBeReplaced("WOS:");
        contributor.setStringToReplaceWith("");

        final Collection<MetadatumDTO> contributedMetadata = contributor.contributeMetadata("foo");

        Map<String, String> metadata = contributedMetadata
            .stream()
            .collect(Collectors.toMap(
                dto -> dto.getSchema() + "." + dto.getElement() + "." + dto.getQualifier(), MetadatumDTO::getValue)
            );

        assertEquals(3, metadata.size());
        assertEquals("123456789", metadata.get("dc.identifier.isi1"));
        assertEquals("123456789", metadata.get("dc.identifier.isi2"));
        assertEquals("123456789", metadata.get("dc.identifier.isi3"));
    }

    private MetadatumDTO metadatum(final String schema, final String element, final String qualifier,
                                   final String value) {
        final MetadatumDTO metadatumDTO = new MetadatumDTO();
        metadatumDTO.setSchema(schema);
        metadatumDTO.setElement(element);
        metadatumDTO.setQualifier(qualifier);
        metadatumDTO.setValue(value);
        return metadatumDTO;
    }

    private MetadataContributor<String> metadataContributorReturning(final List<MetadatumDTO> contributedMetadata) {
        return new MetadataContributor<>() {
            @Override
            public void setMetadataFieldMapping(final MetadataFieldMapping<String, MetadataContributor<String>> rt) {}

            @Override
            public Collection<MetadatumDTO> contributeMetadata(final String t) {
                return contributedMetadata;
            }
        };
    }
}
