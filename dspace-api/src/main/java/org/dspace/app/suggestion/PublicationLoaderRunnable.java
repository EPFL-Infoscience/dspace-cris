/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.suggestion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.dspace.app.suggestion.oaire.OAIREPublicationLoader;
import org.dspace.app.suggestion.pubmed.PubmedPublicationLoader;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.scripts.DSpaceRunnable;
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

    protected Context context;

    protected String profile;

    protected String loader;

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public PublicationLoaderScriptConfiguration<PublicationLoaderRunnable> getScriptConfiguration() {
        PublicationLoaderScriptConfiguration configuration = new DSpace().getServiceManager()
                .getServiceByName("import-loader-suggestions", PublicationLoaderScriptConfiguration.class);
        return configuration;
    }

    @Override
    public void setup() throws ParseException {

        loader = commandLine.getOptionValue("l");

        if (loader == null) {
            throw new NullPointerException("loader can't be null");
        }

        publicationLoader = getPublicationLoader(loader);

        profile = commandLine.getOptionValue("s");
        if (profile == null) {
            LOGGER.info("No argument for -s, process all profile");
        } else {
            LOGGER.info("Process eperson item with UUID " + profile);
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
            default:
                throw new IllegalArgumentException("incorrect loader");
        }
        return publicationLoader;
    }

    @Override
    public void internalRun() throws Exception {

        context = new Context();

        List<Item> researchers = getResearchers(profile);

        for (Item researcher : researchers) {

            publicationLoader.importAuthorRecords(context, researcher);
        }

    }

    /**
     * Get the Item(s) which map a researcher from Solr. If the uuid is specified,
     * the researcher with this UUID will be chosen. If the uuid doesn't match any
     * researcher, the method returns an empty array list. If uuid is null, all
     * research will be return.
     * 
     * @param profile uuid of the researcher. If null, all researcher will be
     *                returned.
     * @return the researcher with specified UUID or all researchers
     */
    @SuppressWarnings("rawtypes")
    private List<Item> getResearchers(String profileUUID) {
        final UUID uuid = profileUUID != null ? UUID.fromString(profileUUID) : null;
        SearchService searchService = new DSpace().getSingletonService(SearchService.class);
        List<IndexableObject> objects = null;
        if (uuid != null) {
            objects = searchService.search(context, "search.resourceid:" + uuid.toString(),
                "lastModified", false, 0, 1000, "search.resourcetype:Item", "dspace.entity.type:Person");
        } else {
            objects = searchService.search(context, "*:*", "lastModified", false, 0, 1000, "search.resourcetype:Item",
                    "dspace.entity.type:Person");
        }
        List<Item> items = new ArrayList<Item>();
        if (objects != null) {
            for (IndexableObject o : objects) {
                items.add((Item) o.getIndexedObject());
            }
        }
        LOGGER.info("Found " + items.size() + " researcher(s)");
        return items;
    }
}
