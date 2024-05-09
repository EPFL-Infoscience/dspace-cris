/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataValue;
import org.dspace.core.Context;
import org.dspace.discovery.indexobject.IndexableItem;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class SolrServiceFileInfoPluginTest {

    private SolrServiceFileInfoPlugin solrServiceFileInfoPlugin;
    private Context context = mock(Context.class);


    @Before
    public void setUp() {
        solrServiceFileInfoPlugin = new SolrServiceFileInfoPlugin();
    }

    @Test
    @Ignore
    public void shouldHandleNPE() {
        IndexableItem indexableItem = mock(IndexableItem.class);

        Item item = mock(Item.class);
        Bitstream bitstream1 = mock(Bitstream.class);
        Bitstream bitstream2 = mock(Bitstream.class);
        Bundle bundle1 = mock(Bundle.class);
        Bundle bundle2 = mock(Bundle.class);

        when(indexableItem.getIndexedObject()).thenReturn(item);
        when(bitstream1.getName()).thenReturn("bitstream1");
        when(bitstream2.getName()).thenReturn("bitstream2");
        when(bundle1.getBitstreams()).thenReturn(List.of(bitstream1));
        when(bundle2.getBitstreams()).thenReturn(List.of(bitstream2));
        when(bundle1.getName()).thenReturn("ORIGINAL");
        when(bundle2.getName()).thenReturn("ORIGINAL");

        when(item.getBundles()).thenReturn(List.of(bundle1, bundle2));

        SolrInputDocument solrInputDocument = new SolrInputDocument();

        SolrInputDocument document = spy(solrInputDocument);

        doThrow(new NullPointerException())
            .when(document)
            .addField("original_bundle_filenames", "bitstream1");

        solrServiceFileInfoPlugin.additionalIndex(context, indexableItem, document);

        verify(document, times(2)).addField(any(), any());
        assertThat(document.getFieldNames(), not(empty()));
    }

    @Test
    @Ignore
    public void shouldAddLicenseWithOutURL() {
        IndexableItem indexableItem = mock(IndexableItem.class);
        Bundle bundle = mock(Bundle.class);
        Bitstream bitstream = mock(Bitstream.class);
        Item item = mock(Item.class);
        MetadataValue metadataValue = mock(MetadataValue.class);
        MetadataField metadataField = mock(MetadataField.class);

        when(indexableItem.getIndexedObject()).thenReturn(item);

        when(metadataValue.getSchema()).thenReturn("epfl");
        when(metadataValue.getElement()).thenReturn("licenseName");
        when(metadataValue.getValue()).thenReturn("123");
        when(metadataValue.getMetadataField()).thenReturn(metadataField);
        when(metadataField.toString('.')).thenReturn("epfl.licenseName");

        when(bitstream.getName()).thenReturn("bitstream");
        when(bitstream.getChecksumAlgorithm()).thenReturn("MD5");
        when(bitstream.getChecksum()).thenReturn("test");
        when(bundle.getName()).thenReturn("ORIGINAL");
        when(bitstream.getMetadata()).thenReturn(List.of(metadataValue));
        when(bundle.getBitstreams()).thenReturn(List.of(bitstream));
        when(item.getBundles()).thenReturn(List.of(bundle));

        SolrInputDocument solrInputDocument = new SolrInputDocument();

        SolrInputDocument document = spy(solrInputDocument);
        solrServiceFileInfoPlugin.additionalIndex(context, indexableItem, document);

        assertNotNull(document.getField("original_bundle_oaire_licenseCondition"));
        assertEquals(document.getField("original_bundle_oaire_licenseCondition").getValue().toString(), "123");
    }

    @Test
    @Ignore
    public void shouldNotAddLicenseWithURL() {
        IndexableItem indexableItem = mock(IndexableItem.class);
        Bundle bundle = mock(Bundle.class);
        Bitstream bitstream = mock(Bitstream.class);
        Item item = mock(Item.class);
        MetadataValue metadataValue = mock(MetadataValue.class);
        MetadataField metadataField = mock(MetadataField.class);

        when(indexableItem.getIndexedObject()).thenReturn(item);

        when(metadataValue.getSchema()).thenReturn("epfl");
        when(metadataValue.getElement()).thenReturn("licenseName");
        when(metadataValue.getValue()).thenReturn("https://www.test.com");
        when(metadataValue.getMetadataField()).thenReturn(metadataField);
        when(metadataField.toString('.')).thenReturn("epfl.licenseName");

        when(bitstream.getName()).thenReturn("bitstream");
        when(bitstream.getChecksumAlgorithm()).thenReturn("MD5");
        when(bitstream.getChecksum()).thenReturn("test");
        when(bundle.getName()).thenReturn("ORIGINAL");
        when(bitstream.getMetadata()).thenReturn(List.of(metadataValue));
        when(bundle.getBitstreams()).thenReturn(List.of(bitstream));
        when(item.getBundles()).thenReturn(List.of(bundle));

        SolrInputDocument solrInputDocument = new SolrInputDocument();

        SolrInputDocument document = spy(solrInputDocument);
        solrServiceFileInfoPlugin.additionalIndex(context, indexableItem, document);

        assertNull(document.getField("original_bundle_oaire_licenseCondition"));
    }

}