/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.core.Context;
import org.dspace.discovery.indexobject.IndexableItem;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * <p>
 * Adds filenames and file descriptions of all files in the ORIGINAL bundle
 * to the Solr search index.
 *
 * <p>
 * To activate the plugin, add the following line to discovery.xml
 * <pre>
 * {@code <bean id="solrServiceFileInfoPlugin" class="org.dspace.discovery.SolrServiceFileInfoPlugin"/>}
 * </pre>
 *
 * <p>
 * After activating the plugin, rebuild the discovery index by executing:
 * <pre>
 * [dspace]/bin/dspace index-discovery -b
 * </pre>
 *
 * @author Martin Walk
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 *
 */
public class SolrServiceFileInfoPlugin implements SolrServiceIndexPlugin {

    /**
     * Class used to map a target metadata into a solr index using {@code SolrInputDocument}
     * 
     * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
     *
     * @param <T>
     */
    private static class SolrFieldMetadataMapper<T> {
        private final MetadataFieldName metadata;
        private final BiFunction<SolrInputDocument, String, Consumer<T>> fieldAdder;

        public SolrFieldMetadataMapper(MetadataFieldName metadata,
                BiFunction<SolrInputDocument, String, Consumer<T>> fieldAdder) {
            super();
            this.metadata = metadata;
            this.fieldAdder = fieldAdder;
        }

        public void map(SolrInputDocument document, String field, T value) {
            this.fieldAdder.apply(document, field).accept(value);
        }

        public MetadataFieldName getMetadata() {
            return metadata;
        }

    }

    private static final DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd");
    private static final String BUNDLE_NAME = "ORIGINAL";
    private static final String SOLR_FIELD_NAME_FOR_FILENAMES = "original_bundle_filenames";
    private static final String SOLR_FIELD_NAME_FOR_DESCRIPTIONS = "original_bundle_descriptions";
    private static final String SOLR_FIELD_NAME_FOR_OAIRE_LICENSE_CONDITION = "original_bundle_oaire_licenseCondition";
    private static final String SOLR_FIELD_NAME_FOR_DATACITE_RIGHTS = "original_bundle_datacite_rights";
    private static final String SOLR_FIELD_NAME_FOR_DATACITE_AVAILABLE = "original_bundle_datacite_available";
    private static final String SOLR_POSTFIX_FILTER = "_filter";
    private static final String SOLR_POSTFIX_KEYWORD = "_keyword";
    // used for facets and filters of type Date to correctly search them and visualize in facets.
    private static final String SOLR_POSTFIX_YEAR = ".year";
    private static final MetadataFieldName METADATA_DATACITE_RIGHTS = new MetadataFieldName("datacite", "rights");
    private static final MetadataFieldName METADATA_DATACITE_AVAILABLE = new MetadataFieldName("datacite", "available");

    private static final MetadataFieldName METADATA_LICENSE_CONDITION =
            new MetadataFieldName("oaire", "licenseCondition");

    private static final SolrFieldMetadataMapper<?> OAIRE_LICENSE_MAPPER =
            new SolrFieldMetadataMapper<String>(
                    METADATA_LICENSE_CONDITION,
                    (document, fieldName) -> (value -> {
                        addField(document, fieldName, value);
                        addField(document, fieldName.concat(SOLR_POSTFIX_KEYWORD), value);
                        addField(document, fieldName.concat(SOLR_POSTFIX_FILTER), value);
                    })
            );

    private static final SolrFieldMetadataMapper<?> DATACITE_RIGHTS_MAPPER =
            new SolrFieldMetadataMapper<String>(
                    METADATA_DATACITE_RIGHTS,
                    (document, fieldName) -> (value -> {
                        addField(document, fieldName, value);
                        addField(document, fieldName.concat(SOLR_POSTFIX_KEYWORD), value);
                        addField(document, fieldName.concat(SOLR_POSTFIX_FILTER), value);
                    })
            );

    private static final SolrFieldMetadataMapper<?> DATACITE_AVAILABLE_MAPPER =
            new SolrFieldMetadataMapper<String>(
                    METADATA_DATACITE_AVAILABLE,
                    (document, fieldName) -> (value -> {
                        addField(document, fieldName, value);
                        addField(document, fieldName.concat(SOLR_POSTFIX_KEYWORD), value);
                        addField(document, fieldName.concat(SOLR_POSTFIX_FILTER), value);
                        addField(document, fieldName.concat(SOLR_POSTFIX_YEAR), dtf.parseLocalDate(value).getYear());
                    })
            );

    private static final Map<String, SolrFieldMetadataMapper<?>> mappableMetadatas = Stream.of(
                Map.entry(SOLR_FIELD_NAME_FOR_OAIRE_LICENSE_CONDITION, OAIRE_LICENSE_MAPPER),
                Map.entry(SOLR_FIELD_NAME_FOR_DATACITE_RIGHTS, DATACITE_RIGHTS_MAPPER),
                Map.entry(SOLR_FIELD_NAME_FOR_DATACITE_AVAILABLE, DATACITE_AVAILABLE_MAPPER)
            )
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


    private static void addField(SolrInputDocument document, String name, Object value) {
        document.addField(name, value);
    }

    @Override
    public void additionalIndex(Context context, IndexableObject indexableObject, SolrInputDocument document) {
        if (indexableObject instanceof IndexableItem) {
            generateBundleIndex(document, ((IndexableItem) indexableObject).getIndexedObject().getBundles());
        }
    }

    private void generateBundleIndex(SolrInputDocument document, List<Bundle> bundles) {
        if (bundles != null) {
            for (Bundle bundle : bundles) {
                String bundleName = bundle.getName();
                if (bundleName != null && bundleName.equals(BUNDLE_NAME)) {
                    generateBitstreamIndex(document, bundle.getBitstreams());
                }
            }
        }
    }

    /**
     * Method that adds index to {@link SolrInputDocument}, iterates between {@code bitstreams} and {@code mappableMetadatas}
     * then applies the corresponding mapping function to the bitstream
     * 
     * @param document solr document
     * @param bitstreams list of bitstreams to analyze
     */
    private void generateBitstreamIndex(SolrInputDocument document, List<Bitstream> bitstreams) {
        if (document != null && bitstreams != null) {
            for (Bitstream bitstream : bitstreams) {
                addField(document, SOLR_FIELD_NAME_FOR_FILENAMES, bitstream.getName());

                Optional.ofNullable(bitstream.getDescription())
                    .filter(StringUtils::isNotEmpty)
                    .ifPresent(
                        (description) ->
                            addField(document, SOLR_FIELD_NAME_FOR_DESCRIPTIONS,description)
                    );

                mappableMetadatas
                    .entrySet()
                    .stream()
                    .forEach(
                        entry ->
                            this.addNonNullMetadataValueField(bitstream, entry.getValue(), document, entry.getKey())
                    );
            }
        }
    }

    /**
     * Method that iterates bitstream's metadatas, verifies if is mappable and then maps the ones configured
     * using the {@link SolrFieldMetadataMapper} function.
     * 
     * @param bitstream that contains metadatas to verify
     * @param metadataMapper the mapper that will be applied to the metadatas
     * @param document solrdocument
     * @param fieldName solr index name
     */
    private <T> void addNonNullMetadataValueField(Bitstream bitstream, SolrFieldMetadataMapper<T> metadataMapper,
            SolrInputDocument document, String fieldName) {
        bitstream.getMetadata()
                .stream()
                .filter(metadata ->
                    StringUtils.equals(metadataMapper.getMetadata().schema, metadata.getSchema()) &&
                    StringUtils.equals(metadataMapper.getMetadata().element, metadata.getElement()) &&
                    StringUtils.equals(metadataMapper.getMetadata().qualifier, metadata.getQualifier())
                )
                .map(MetadataValue::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(value -> metadataMapper.map(document, fieldName, (T) value));
    }
}