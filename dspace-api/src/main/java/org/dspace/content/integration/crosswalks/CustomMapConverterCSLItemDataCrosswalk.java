/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks;

import org.dspace.content.integration.crosswalks.csl.DSpaceListItemDataProvider;
import org.dspace.util.SimpleMapConverter;

/**
 * Extension of {@link CSLItemDataCrosswalk} that allows to set a custom
 * typeConverter for DSpaceListItemDataProvider other than the one
 * configured in csl-citation.xml
 */
public class CustomMapConverterCSLItemDataCrosswalk extends CSLItemDataCrosswalk {

    private SimpleMapConverter mapConverter;

    @Override
    protected DSpaceListItemDataProvider getDSpaceListItemDataProviderInstance() {
        DSpaceListItemDataProvider provider = super.getDSpaceListItemDataProviderInstance();
        provider.setTypeConverter(mapConverter);
        return provider;
    }

    public SimpleMapConverter getMapConverter() {
        return mapConverter;
    }

    public void setMapConverter(SimpleMapConverter mapConverter) {
        this.mapConverter = mapConverter;
    }
}
