/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import static java.util.Collections.EMPTY_LIST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.apache.commons.cli.ParseException;
import org.dspace.content.MetadataValue;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.epfl.client.EpflApiClientImpl;
import org.dspace.epfl.client.model.OrgUnitDTO;
import org.dspace.epfl.service.impl.OrgUnitApiServiceImpl;
import org.dspace.utils.DSpace;

public class MockSynchronizationOfOrgUnitsScript extends SynchronizationOfOrgUnitsScript {

    @Override
    @SuppressWarnings("unchecked")
    public SynchronizationOfOrgUnitsConfiguration<SynchronizationOfOrgUnitsScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("mock-synchronization-of-orgunits",
                                                                 SynchronizationOfOrgUnitsConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        super.setup();
        OrgUnitDTO orgUnitChild = new OrgUnitDTO();
        orgUnitChild.setAcronym("CHILD");
        orgUnitChild.setName("Child OrgUnit");
        orgUnitChild.setUnitPath("PARENT CHILD");
        OrgUnitDTO orgUnitParent = new OrgUnitDTO();
        orgUnitParent.setAcronym("PARENT");
        orgUnitParent.setName("Parent OrgUnit");
        orgUnitParent.setUnitPath("PARENT");

        epflApiClient = mock(EpflApiClientImpl.class);
        when(epflApiClient.getOrgUnit(eq("CHILD"), any())).thenReturn(Optional.of(orgUnitChild));
        when(epflApiClient.getOrgUnit(eq("PARENT"), any())).thenReturn(Optional.of(orgUnitParent));

        orgUnitApiService = mock(OrgUnitApiServiceImpl.class);
        when(orgUnitApiService.getMetadataValues(any())).thenReturn(List.of(new MetadataValueDTO()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<MetadataValueDTO> metadataToUpdate(List<MetadataValue> itemMetadata,
                                                      List<MetadataValueDTO> metadataValues) {
        return EMPTY_LIST;
    }

    @Override
    protected void sendEmail() {
        // Do nothing
    }
}
