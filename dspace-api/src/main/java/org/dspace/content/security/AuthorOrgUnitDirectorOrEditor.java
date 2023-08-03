/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.security;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.logic.Filter;
import org.dspace.content.logic.LogicalStatementException;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.CrisConstants;
import org.dspace.eperson.service.EPersonService;
import org.dspace.util.UUIDUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class AuthorOrgUnitDirectorOrEditor implements Filter {

    @Autowired
    private ItemService itemService;

    @Autowired
    private EPersonService ePersonService;

    private String name;

    @Override
    public Boolean getResult(Context context, Item item) throws LogicalStatementException {
        if (context.getCurrentUser() == null) {
            return false;
        }
        List<Item> involvedOrgUnits = findOrgUnits(context, item);
        return involvedOrgUnits.stream()
            .anyMatch(ou -> directorOrDelegateOf(context, ou));
    }

    private boolean directorOrDelegateOf(Context context, Item orgUnit) {
        List<MetadataValue> director = itemService.getMetadataByMetadataString(orgUnit,"crisou.director");
        List<MetadataValue> editors = itemService.getMetadataByMetadataString(orgUnit,"epfl.orgUnit.editor");
        return Stream.concat(director.stream(), editors.stream())
            .anyMatch(person -> ownerOf(context, person));
    }

    private boolean ownerOf(Context context, MetadataValue person) {
        Item item = findItem(context, person.getAuthority());
        return item != null && ePersonService.isOwnerOfItem(context.getCurrentUser(), item);
    }

    private List<Item> findOrgUnits(Context context, Item item) {
        Map<Integer, MetadataValue> affiliationsMap =
            itemService.getMetadataByMetadataString(item, "oairecerif.author.affiliation")
                       .stream().collect(Collectors.toMap(mv -> mv.getPlace(), Function.identity()));

        List<MetadataValue> authors =
            itemService.getMetadataByMetadataString(item, "dc.contributor.author");

        return authors.stream()
                      .map(author -> findItem(context, author.getAuthority()))
                      .filter(Objects::nonNull)
                      .flatMap(author -> affiliations(context, author).stream())
                      .collect(Collectors.toList());

    }

    private List<Item> affiliations(Context context, Item author) {

        List<MetadataValue> mainAffiliation =
            itemService.getMetadataByMetadataString(author, "person.affiliation.name");

        List<Integer> closedAffiliations =
            itemService.getMetadataByMetadataString(author, "oairecerif.affiliation.endDate")
                       .stream()
                       .filter(mv -> StringUtils.isNotBlank(mv.getValue()))
                       .filter(mv -> !CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE.equals(
                           mv.getValue()))
                       .map(mv -> mv.getPlace())
                       .collect(Collectors.toList());



        return Stream.concat(mainAffiliation.stream(),
                             itemService
                                 .getMetadataByMetadataString(author, "oairecerif.person.affiliation")
                                 .stream()
                                 .filter(mv -> !closedAffiliations.contains(mv.getPlace())))
                     .map(mv -> findItem(context, mv.getAuthority()))
                     .filter(Objects::nonNull)
                     .collect(Collectors.toList());

    }

    private Item findItem(Context context, String id) {
        UUID uuid = UUIDUtils.fromString(id);
        try {
            return uuid != null ? itemService.find(context, uuid) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setBeanName(String name) {
        this.name = name;
    }
}
