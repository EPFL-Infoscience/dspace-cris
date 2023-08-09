/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.orcid.script;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.ParseException;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResultItemIterator;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
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

    private OrcidTokenService orcidTokenService;

    private ItemService itemService;

    private Context context;

    private String filename;

    @Override
    public void setup() throws ParseException {
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

            Item item = context.reloadEntity(items.next());

            performOrcidUpdate(item, orcidBySciperId);

            context.commit();
        }

    }

    private void performOrcidUpdate(Item item, Map<String, String> orcidBySciperId) throws SQLException {

        String sciperId = itemService.getMetadataFirstValue(item, METADATA_EPFL_SCIPERID, Item.ANY);

        String orcidJsonValue = orcidBySciperId.get(sciperId);

        String orcidMetadataValue = getOrcidMetadata(item);

        String logInfoPrefix = "Item with ID " + item.getID() + " and sciperId " + sciperId + " ";

        if (orcidJsonValue == null) {

            removeAccessToken(item);
            clearOrcidMetadata(item);

            handler.logInfo(logInfoPrefix + "does not have an ORCID ID in the provided json. "
                + "Removed access token and ORCID ID from the system");

        } else if (orcidMetadataValue == null) {

            setOrcidMetadata(item, orcidJsonValue);

            handler.logInfo(logInfoPrefix + "updated with the ORCID ID present in the provided json.");

        } else if (!orcidJsonValue.equals(orcidMetadataValue)) {
            removeAccessToken(item);
            setOrcidMetadata(item, orcidJsonValue);

            handler.logInfo(logInfoPrefix + "has an ORCID ID different from the one present in the provided json. "
                + "Removed access token and ORCID ID replaced.");
        }

    }

    private Map<String, String> parseJson(InputStream is) {
        try {
            Map<String, Map<String, String>> rawMap = new ObjectMapper().readValue(is, new TypeReference<>() {});

            return rawMap.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> getOrcidSuffix(e.getValue().get("orcid"))));

        } catch (IOException e) {
            throw new RuntimeException("Failed to parse json file", e);
        }
    }

    private Iterator<Item> findPersonsWithSciperId() throws Exception {

        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setQuery("epfl.sciperId: [* TO *] AND entityType:Person");

        return new DiscoverResultItemIterator(context, discoverQuery);

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
        orcidTokenService.deleteByProfileItem(context, profile);
        itemService.clearMetadata(context, profile, "dspace", "orcid", "authenticated", Item.ANY);
    }

    private String getOrcidSuffix(String orcidUrl) {
        return orcidUrl != null && OrcidCheck.isOrcid(orcidUrl)
            ? orcidUrl.trim().substring(orcidUrl.length() - 19)
            : orcidUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OrcidUpdateScriptConfiguration<OrcidUpdate> getScriptConfiguration() {
        return new DSpace().getServiceManager()
                           .getServiceByName("orcid-update", OrcidUpdateScriptConfiguration.class);
    }
}
