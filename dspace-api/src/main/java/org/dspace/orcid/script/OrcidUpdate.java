/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.orcid.script;

import static org.dspace.app.nbevent.service.impl.NBEventServiceImpl.RESOURCE_UUID;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.ParseException;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchUtils;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.importer.external.service.OrcidCheck;
import org.dspace.orcid.factory.OrcidServiceFactory;
import org.dspace.orcid.service.OrcidTokenService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;

public class OrcidUpdate extends DSpaceRunnable<OrcidUpdateScriptConfiguration<OrcidUpdate>> {

    private static final MetadataFieldName METADATA_PERSON_ORCID =
        new MetadataFieldName("person", "identifier", "orcid");

    private static final MetadataFieldName METADATA_EPFL_SCIPERID =
        new MetadataFieldName("epfl", "sciperId", null);

    private EPersonService ePersonService;

    private SearchService searchService;

    private OrcidTokenService orcidTokenService;

    private ItemService itemService;

    private Context context;

    private String filename;

    @Override
    public void setup() throws ParseException {
        ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        searchService = SearchUtils.getSearchService();
        orcidTokenService = OrcidServiceFactory.getInstance().getOrcidTokenService();
        itemService = ContentServiceFactory.getInstance().getItemService();

        filename = commandLine.getOptionValue("f");
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        try {
            context.turnOffAuthorisationSystem();

            InputStream inputStream =
                handler.getFileStream(context, filename)
                       .orElseThrow(() -> new IllegalArgumentException("Error reading file, the file couldn't be "
                                                                           + "found for filename: " + filename));
            performOrcidUpdate(inputStream);

            context.complete();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private void performOrcidUpdate(InputStream is) throws Exception {
        Map<String, String> orcidBySciperId = parseJson(is);
        Iterator<Item> items = findPersonsWithSciperId();

        while (items.hasNext()) {
            Item item = items.next();
            String orcidJsonValue = getOrcidSuffix(orcidBySciperId.get(
                itemService.getMetadataFirstValue(item, METADATA_EPFL_SCIPERID, Item.ANY)));
            String orcidMetadataValue = getOrcidMetadata(item);

            if (orcidJsonValue == null) {
                removeAccessToken(item);
                clearOrcidMetadata(item);
                continue;
            }

            if (orcidMetadataValue == null) {
                setOrcidMetadata(item, orcidJsonValue);
                continue;
            }

            if (!orcidJsonValue.equals(orcidMetadataValue)) {
                removeAccessToken(item);
                setOrcidMetadata(item, orcidJsonValue);
            }
        }
    }

    private Map<String, String> parseJson(InputStream is) {
        try {
            Map<String, Map<String, String>> rawMap = new ObjectMapper().readValue(is, new TypeReference<>() {});

            return rawMap.entrySet()
                         .stream()
                         .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get("orcid")));
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse json file", e);
        }
    }

    private Iterator<Item> findPersonsWithSciperId() throws Exception {
        SolrDocumentList documents = query(new SolrQuery("epfl.sciperId:* AND entityType:Peron"));
        List<String> uuids = documents.stream()
                                      .map(doc -> (String) doc.get(RESOURCE_UUID))
                                      .collect(Collectors.toList());

        return itemService.findByIds(context, uuids);
    }

    private SolrDocumentList query(SolrQuery solrParams) {
        try {
            solrParams.addSort("item.id", ORDER.asc);
            QueryResponse response = searchService.getSolrSearchCore().getSolr().query(solrParams);
            return response.getResults();
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }

    private String getOrcidMetadata(Item profile) {
        return itemService.getMetadataFirstValue(profile, METADATA_PERSON_ORCID, Item.ANY);
    }

    private void setOrcidMetadata(Item profile, String value) throws SQLException {
        if (value != null) {
            itemService.setMetadataSingleValue(context, profile, METADATA_PERSON_ORCID, Item.ANY, value);
        }
    }

    private void clearOrcidMetadata(Item profile) throws SQLException {
        itemService.clearMetadata(context, profile, "person", "identifier", "orcid", Item.ANY);
    }

    private void removeAccessToken(Item profile) throws SQLException {
        EPerson owner = ePersonService.findByProfileItem(context, profile);
        orcidTokenService.deleteByEPerson(context, owner);
        ePersonService.clearMetadata(context, owner, "dspace", "orcid", "authenticated", Item.ANY);
    }

    private String getOrcidSuffix(String orcidUrl) {
        return orcidUrl != null && OrcidCheck.isOrcid(orcidUrl)
            ? orcidUrl.trim().substring(orcidUrl.length() - 19)
            : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OrcidUpdateScriptConfiguration<OrcidUpdate> getScriptConfiguration() {
        return new DSpace().getServiceManager()
                           .getServiceByName("orcid-update", OrcidUpdateScriptConfiguration.class);
    }
}
