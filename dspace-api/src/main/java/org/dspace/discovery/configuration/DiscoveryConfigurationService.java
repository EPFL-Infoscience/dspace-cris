/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.indexobject.IndexableDSpaceObject;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * @author Kevin Van de Velde (kevin at atmire dot com)
 */
public class DiscoveryConfigurationService {

    private Map<String, DiscoveryConfiguration> map;
    private Map<Integer, List<String>> toIgnoreMetadataFields = new HashMap<>();

    public Map<String, DiscoveryConfiguration> getMap() {
        return map;
    }

    public void setMap(Map<String, DiscoveryConfiguration> map) {
        this.map = map;
        if (map != null) {
            // improve the configuration assigning the map key as id to any configuration that doesn't have one
            map.entrySet().stream()
               .filter(entry -> StringUtils.isBlank(entry.getValue().getId()))
               .forEach(entry -> entry.getValue().setId(entry.getKey()));
        }
    }

    public Map<Integer, List<String>> getToIgnoreMetadataFields() {
        return toIgnoreMetadataFields;
    }

    public void setToIgnoreMetadataFields(Map<Integer, List<String>> toIgnoreMetadataFields) {
        this.toIgnoreMetadataFields = toIgnoreMetadataFields;
    }

    @SuppressWarnings({ "rawtypes" })
    public DiscoveryConfiguration getDiscoveryConfiguration(IndexableObject dso) {
        String name = (dso == null)
            ? "default"
            : (dso instanceof IndexableDSpaceObject)
                ? ((IndexableDSpaceObject) dso).getIndexedObject().getHandle()
                : dso.getUniqueIndexID();

        return getDiscoveryConfigurationByNameOrDefault(name);
    }

    public DiscoveryConfiguration getDiscoveryConfigurationByNameOrDefault(final String name) {
        return Optional.ofNullable(getDiscoveryConfigurationByName(name)).orElse(map.get("default"));
    }

    public DiscoveryConfiguration getDiscoveryConfigurationByName(String name) {
        return StringUtils.isBlank(name) ? null : map.get(name);
    }

    @SuppressWarnings({ "rawtypes" })
    public DiscoveryConfiguration getDiscoveryConfigurationByNameOrDso(final String configurationName,
                                                                       final IndexableObject dso) {
        return (StringUtils.isNotBlank(configurationName) && map.containsKey(configurationName))
            ? map.get(configurationName)
            : getDiscoveryConfiguration(dso);
    }

    /**
     * Retrieves a list of all DiscoveryConfiguration objects where
     * {@link org.dspace.discovery.configuration.DiscoveryConfiguration#isIndexAlways()} is true
     * These configurations should always be included when indexing
     */
    public List<DiscoveryConfiguration> getIndexAlwaysConfigurations() {
        return map.values().stream()
                  .filter(DiscoveryConfiguration::isIndexAlways)
                  .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        System.out.println(DSpaceServicesFactory.getInstance().getServiceManager().getServicesNames().size());
        DiscoveryConfigurationService mainService =
            DSpaceServicesFactory.getInstance().getServiceManager().getServiceByName(
                DiscoveryConfigurationService.class.getName(),
                DiscoveryConfigurationService.class
            );

        for (String key : mainService.getMap().keySet()) {
            System.out.println(key);

            System.out.println("Facets:");
            DiscoveryConfiguration discoveryConfiguration = mainService.getMap().get(key);
            discoveryConfiguration.getSidebarFacets().forEach(sidebarFacet -> {
                System.out.println("\t" + sidebarFacet.getIndexFieldName());
                sidebarFacet.getMetadataFields().stream()
                            .map(metadataField -> "\t\t" + metadataField)
                            .forEach(System.out::println);
            });

            System.out.println("Search filters");
            discoveryConfiguration.getSearchFilters().stream()
                                  .map(DiscoverySearchFilter::getMetadataFields)
                                  .flatMap(Collection::stream)
                                  .map(metadataField -> "\t\t" + metadataField)
                                  .forEach(System.out::println);

            System.out.println("Recent submissions configuration:");
            DiscoveryRecentSubmissionsConfiguration recentSubmissionConfiguration = discoveryConfiguration
                .getRecentSubmissionConfiguration();
            System.out.println("\tMetadata sort field: " + recentSubmissionConfiguration.getMetadataSortField());
            System.out.println("\tMax recent submissions: " + recentSubmissionConfiguration.getMax());

            List<String> defaultFilterQueries = discoveryConfiguration.getDefaultFilterQueries();
            if (!defaultFilterQueries.isEmpty()) {
                System.out.println("Default filter queries");
                defaultFilterQueries.forEach(fq -> System.out.println("\t" + fq));
            }
        }
    }

    /**
     * Retrieves a list of all DiscoveryConfiguration objects where key starts with prefixConfigurationName
     * @param prefixConfigurationName string as prefix key
     */
    public List<DiscoveryConfiguration> getDiscoveryConfigurationWithPrefixName(final String prefixConfigurationName) {
        return StringUtils.isBlank(prefixConfigurationName)
            ? new ArrayList<>()
            : map.keySet().stream()
                 .filter(key -> key.startsWith(prefixConfigurationName))
                 .map(map::get)
                 .collect(Collectors.toList());
    }

}
