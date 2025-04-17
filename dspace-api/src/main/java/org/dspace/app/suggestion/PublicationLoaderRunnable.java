/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.suggestion;

import static org.apache.commons.collections4.IteratorUtils.chainedIterator;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.suggestion.oaire.OAIREPublicationLoader;
import org.dspace.app.suggestion.orcid.OrcidPublicationLoader;
import org.dspace.app.suggestion.pubmed.PubmedPublicationLoader;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverQuery.SORT_ORDER;
import org.dspace.discovery.DiscoverResultIterator;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.external.provider.impl.LiveImportDataProvider;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runner responsible to import metadata about authors from loader to Solr.
 * This runner works in two ways:
 * If -s parameter with a valid UUID is received, then the specific researcher
 * with this UUID will be used.
 * Invocation without any parameter results in massive import, processing all
 * authors registered in DSpace.
 */

public class PublicationLoaderRunnable
    extends DSpaceRunnable<PublicationLoaderScriptConfiguration<PublicationLoaderRunnable>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationLoaderRunnable.class);

    private SolrSuggestionProvider publicationLoader = null;

    private ConfigurationService configurationService;

    private ItemService itemService;

    protected Context context;

    protected String profile;

    protected String loader;

    private Integer itemLimit;

    private String extraQuery;

    private Map<String, LiveImportDataProvider> nameToProvider = new HashMap<String, LiveImportDataProvider>();

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public PublicationLoaderScriptConfiguration<PublicationLoaderRunnable> getScriptConfiguration() {
        PublicationLoaderScriptConfiguration configuration = new DSpace().getServiceManager()
                .getServiceByName("import-loader-suggestions", PublicationLoaderScriptConfiguration.class);
        return configuration;
    }

    @Override
    public void setup() throws ParseException {

        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        itemService = ContentServiceFactory.getInstance().getItemService();

        loader = commandLine.getOptionValue("l");

        profile = commandLine.getOptionValue("s");
        if (profile == null) {
            LOGGER.info("No argument for -s, process all profile");
        } else {
            LOGGER.info("Process eperson item with UUID " + profile);
        }

        if (commandLine.hasOption("il")) {
            this.itemLimit = Integer.valueOf(commandLine.getOptionValue("il"));
        } else {
            this.itemLimit = getDefaultLimit();
        }

        if (commandLine.hasOption("q")) {
            extraQuery = commandLine.getOptionValue("q");
        }

    }

    @Override
    public void internalRun() throws Exception {

        context = new Context();
        context.turnOffAuthorisationSystem();

        if (loader == null) {
            throw new NullPointerException("loader can't be null");
        }

        if (profile != null && UUIDUtils.fromString(profile) == null) {
            throw new IllegalArgumentException("The provided argument -s is not a valid uuid");
        }

        publicationLoader = getPublicationLoader(loader);

        try {

            Iterator<Item> researchers = findResearchers();
            while (researchers.hasNext()) {
                Item researcher = researchers.next();
                handler.logInfo("Querying external system for author " + researcher.getName() + " id: "
                    + researcher.getID());
                handler.logInfo("Extra query: " + extraQuery);
                int createdSuggestions = 0;
                createdSuggestions = publicationLoader.importRecords(context, researcher, extraQuery);
                handler.logInfo(createdSuggestions + " suggestions created for author " + researcher.getName() +
                                    " id: " + researcher.getID());
                setLastImportMetadataValue(researcher);
            }

        } finally {
            context.restoreAuthSystemState();
            context.complete();
        }


    }

    private SolrSuggestionProvider getPublicationLoader(String loader) {
        SolrSuggestionProvider publicationLoader = null;
        switch (loader) {
            case "oaire":
                publicationLoader = new DSpace().getServiceManager().getServiceByName(
                    "OAIREPublicationLoader", OAIREPublicationLoader.class);
                break;
            case "pubmed":
                publicationLoader = new DSpace().getServiceManager().getServiceByName(
                    "pubmedPublicationLoader", PubmedPublicationLoader.class);
                break;
            case "orcid" :
                publicationLoader = new DSpace().getServiceManager().getServiceByName(
                    "orcidPublicationLoader", OrcidPublicationLoader.class);
                break;
            default:
                throw new IllegalArgumentException("IllegalArgumentException: " +
                    "Provider for: " + loader + " couldn't be found");
        }
        return publicationLoader;
    }

    /**
     * Get the Item(s) which map a researcher from Solr. If the uuid is specified,
     * the researcher with this UUID will be chosen. If the uuid doesn't match any
     * researcher, the method returns an empty array list. If uuid is null, all
     * research will be return.
     * 
     * @return the researcher with specified UUID or all researchers
     */
    private Iterator<Item> findResearchers() {

        if (profile != null) {
            return findResearcherByUuid();
        }

        Iterator<Item> itemsWithoutLastImport = findResearchersWithoutLastImport();

        Iterator<Item> itemsSortedByLastImport = findResearchersSortedByLastImport();

        Iterator<Item> chainedIterator = chainedIterator(itemsWithoutLastImport, itemsSortedByLastImport);
        return IteratorUtils.boundedIterator(chainedIterator, itemLimit);

    }

    private Iterator<Item> findResearcherByUuid() {
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setQuery("search.resourceid:" + profile);
        discoverQuery.setDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.setMaxResults(20);
        discoverQuery.addFilterQueries("dspace.entity.type:Person");
        return new DiscoverResultIterator<Item, UUID>(context, discoverQuery);
    }

    private Iterator<Item> findResearchersWithoutLastImport() {
        return findResearchers(false);
    }

    private Iterator<Item> findResearchersSortedByLastImport() {
        return findResearchers(true);
    }

    private Iterator<Item> findResearchers(boolean withLastImport) {

        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setDSpaceObjectFilter(IndexableItem.TYPE);
        discoverQuery.setMaxResults(20);
        discoverQuery.addFilterQueries("dspace.entity.type:Person");
        Optional.of(loaderFilterQueries()).filter(StringUtils::isNotBlank)
                    .ifPresent(discoverQuery::addFilterQueries);

        String lastImportMetadataField = getLastImportMetadataField();
        if (withLastImport) {
            // set an upper limit to prevent items updated in the same run from being pulled out again.
            discoverQuery.setQuery(lastImportMetadataField + ": [* TO " + currentDateMinusOneSecond() + "]");
            discoverQuery.setSortField(lastImportMetadataField, SORT_ORDER.asc);
        } else {
            discoverQuery.setQuery("-" + lastImportMetadataField + ": [* TO *]");
        }

        return new DiscoverResultIterator<Item, UUID>(context, discoverQuery);
    }

    private String loaderFilterQueries() {
        if (loader.equals("orcid")) {
            return "person.identifier.orcid:*";
        }
        return "";
    }

    private void setLastImportMetadataValue(Item item) {
        try {
            item = context.reloadEntity(item);
            String metadataField = "cris.lastimport.loader-" + loader;
            String currentDate = DCDate.getCurrent().toString();
            itemService.setMetadataSingleValue(context, item, new MetadataFieldName(metadataField), null, currentDate);
            itemService.update(context, item);
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException(e);
        }
    }

    private String currentDateMinusOneSecond() {
        return DCDate.getCurrent().toString() + "-1SECONDS";
    }

    private String getLastImportMetadataField() {
        return "cris.lastimport.loader-" + loader + "_dt";
    }

    private Integer getDefaultLimit() {
        return configurationService.getIntProperty("publication-loader.limit", 1000);
    }
}
