/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery.configuration;

import static org.apache.commons.collections4.iterators.EmptyIterator.emptyIterator;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResultIterator;
import org.dspace.discovery.indexobject.IndexableItem;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 */
public class DiscoveryConfigurationUtilsService {

    private static Logger log = LogManager.getLogger(DiscoveryConfigurationUtilsService.class);

    @Autowired
    private ItemService itemService;
    @Autowired
    private DiscoveryConfigurationService searchConfigurationService;

    public Iterator<Item> findByRelation(Context context, Item item, String relationName) {
        String entityType = itemService.getMetadataFirstValue(item, "dspace", "entity", "type", Item.ANY);
        if (entityType == null) {
            log.warn("The item with id " + item.getID() + " has no dspace.entity.type. No related items is found.");
            return emptyIterator();
        }

        DiscoveryConfiguration discoveryConfiguration = findDiscoveryConfiguration(entityType, relationName);
        if (discoveryConfiguration == null) {
            log.warn("No discovery configuration found for relation " + relationName + " for item with id "
                + item.getID() + " and type " + entityType + ". No related items is found.");
            return emptyIterator();
        }

        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.setDiscoveryConfigurationName(discoveryConfiguration.getId());
        processAndAddDefaultQueries(discoveryConfiguration.getDefaultFilterQueries(), item, discoverQuery);

        return new DiscoverResultIterator<Item, UUID>(context, discoverQuery);
    }

    private DiscoveryConfiguration findDiscoveryConfiguration(String entityType, String relationName) {
        String configurationName = "RELATION." + entityType + "." + relationName;
        return searchConfigurationService.getDiscoveryConfigurationByName(configurationName);
    }

    public void processAndAddDefaultQueries(List<String> queries, Item item, DiscoverQuery discoverQuery) {
        for (String query : queries) {
            Matcher matcher = Pattern.compile(".*?(\\S*_query)").matcher(query);

            if (matcher.find()) {
                // If default query contains "*_query" then replace it with corresponding metadata value
                String matchedSubString = matcher.group(1);
                String queryMetadataValue = itemService.getMetadata(item, matchedSubString.split("_")[0]);
                query = query.replace(matchedSubString, queryMetadataValue);
            }

            discoverQuery.addFilterQueries(MessageFormat.format(query, item.getID()));
        }
    }

}