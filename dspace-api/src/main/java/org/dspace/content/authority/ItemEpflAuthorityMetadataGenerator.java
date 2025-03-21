/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.authority;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrDocument;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;


public class ItemEpflAuthorityMetadataGenerator extends ItemSimpleAuthorityMetadataGenerator {

    private final ConfigurationService configurationService =
            DSpaceServicesFactory.getInstance().getConfigurationService();

    @Override
    protected void buildSingleExtraByRP(SolrDocument solrDocument, Map<String, String> extras) {
        Context context = new Context();
        List<MetadataValueDTO> parentOrgUnitMetadata =
                getMetadataValueDTOsFromSolr(getSchema(), getElement(), getQualifier(), solrDocument);
        if (!parentOrgUnitMetadata.isEmpty()) {
            String epflOrgUnitUuid = configurationService.getProperty("epfl.head-orgunit.uuid", "dummy");
            String epflOrgUnitAcronym = null;
            try {
                Item epflOrgUnit = itemService.findByIdOrLegacyId(context, epflOrgUnitUuid);
                if (epflOrgUnit != null) {
                    epflOrgUnitAcronym =
                            itemService.getMetadataFirstValue(epflOrgUnit, "oairecerif", "acronym", null, "*");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            if (StringUtils.isNotBlank(epflOrgUnitAcronym) &&
                    isPersonInternal(context, parentOrgUnitMetadata.get(0).getAuthority(), epflOrgUnitUuid)) {
                buildSingleExtraByMetadata(new MetadataValueDTO(getKeyId().replace("_", "."),
                        epflOrgUnitAcronym, epflOrgUnitUuid, 0, 0), extras);
            }
        }
    }

    private boolean isPersonInternal(Context context, String parentOrgUnitUuid, String epflOrgUnitUuid) {

        if (StringUtils.isBlank(parentOrgUnitUuid)) {
            return false;
        }

        if (parentOrgUnitUuid.equals(epflOrgUnitUuid)) {
            return true;
        }

        try {
            Iterator<Item> items = itemService.findByIds(context, List.of(parentOrgUnitUuid));
            if (items.hasNext()) {
                Optional<MetadataValue> parentOrgUnitMetadata =
                    items.next().getMetadata().stream()
                         .filter(metadataValue -> "organization_parentOrganization".equals(
                             metadataValue.getMetadataField().toString()))
                         .findFirst();
                return parentOrgUnitMetadata
                    .filter(metadataValue -> isPersonInternal(context, metadataValue.getAuthority(), epflOrgUnitUuid))
                    .isPresent();
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }


}
