/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.epfl.script;

import static org.dspace.util.FunctionalUtils.throwingMapperWrapper;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResultItemIterator;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;

public class SubmitterFixScript
    extends DSpaceRunnable<SubmitterFixScriptConfiguration<SubmitterFixScript>> {
    private String collectionId;

    private String email;

    private String defaultEmail;

    private CollectionService collectionService;

    private ItemService itemService;

    private EPersonService ePersonService;

    private Context context;

    @Override
    public SubmitterFixScriptConfiguration<SubmitterFixScript> getScriptConfiguration() {
        return new DSpace().getServiceManager()
                           .getServiceByName("epfl-update-submitter", SubmitterFixScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        collectionService = ContentServiceFactory.getInstance().getCollectionService();
        itemService = ContentServiceFactory.getInstance().getItemService();
        ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        collectionId = commandLine.getOptionValue('c');
        email = commandLine.getOptionValue('e');
        defaultEmail = DSpaceServicesFactory.getInstance().getConfigurationService()
                                            .getProperty("epfl.default-submitter.email");
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        Collection collection = collectionService.find(context, UUIDUtils.fromString(collectionId));
        if (Objects.isNull(collection)) {
            throw new IllegalArgumentException("Collection specified in input has not been found");
        }

        context.turnOffAuthorisationSystem();
        Iterator<Item> itemIterator = findItems();

        try {

            int count = 0;

            while (itemIterator.hasNext()) {
                Item item = context.reloadEntity(itemIterator.next());

                try {
                    updateSubmitter(item);
                    count++;
                } catch (Exception ex) {
                    handler.logError("An error occurs updating item " + item.getID(), ex);
                }

                if (count % 10 == 0) {
                    context.commit();
                    handler.logInfo("Processed " + count + " items");
                }

            }

            context.complete();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
        }

    }

    private Iterator<Item> findItems() {
        DiscoverQuery query = new DiscoverQuery();
        query.setDSpaceObjectFilter(IndexableItem.TYPE);
        query.addFilterQueries("location.coll:(" + collectionId + ")");
        return new DiscoverResultItemIterator(context, query);
    }

    private void updateSubmitter(Item item) {
        if (hasSciper(item.getSubmitter())) {
            handler.logInfo("Item " + item.getID() + " already has a submitter with sciper, not changed.");
            return;
        }

        EPerson newSubmitter = getEPersonFromMetadata(item, "epfl.lastmodified.email");
        if (hasSciper(newSubmitter)) {
            updateSubmitter(item, newSubmitter);
            return;
        }

        newSubmitter = getEPersonFromMetadata(item, "epfl.curator.email");
        if (hasSciper(newSubmitter)) {
            updateSubmitter(item, newSubmitter);
            return;
        }

        firstAuthorWithSciper(item)
            .ifPresentOrElse(
                ePerson -> updateSubmitter(item, ePerson),
                () -> updateSubmitter(item, StringUtils.isNotBlank(email) ? email : defaultEmail));
    }

    private void updateSubmitter(Item item, String email) {
        try {
            EPerson submitter = ePersonService.findByEmail(context, email);
            if (submitter == null) {
                handler.logInfo("Item " + item.getID() +
                                ". No person found for email " + email + ", submitter not changed");
                return;
            }
            updateSubmitter(item, submitter);
        } catch (SQLException e) {
            handler.handleException(e);
        }
    }

    private void updateSubmitter(Item item, EPerson submitter) {
        if (!StringUtils.equalsIgnoreCase(item.getSubmitter().getEmail(), submitter.getEmail())) {
            handler.logInfo("Item " + item.getID() + " submitter updated from " + item.getSubmitter().getEmail() +
                            " to " + submitter.getEmail());
            item.setSubmitter(submitter);

            try {
                itemService.setMetadataSingleValue(context, item, "dc", "provenance", null, null, submitter.getEmail());
                itemService.update(context, item);
            } catch (SQLException | AuthorizeException e) {
                handler.handleException(e);
            }
        }
    }

    private Optional<EPerson> firstAuthorWithSciper(Item item) {
        return itemService
            .getMetadataByMetadataString(item, "dc.contributor.author")
            .stream()
            .filter(mv -> StringUtils.isNotBlank(mv.getAuthority()))
            .map(throwingMapperWrapper(mv -> itemService.find(context, UUIDUtils.fromString(mv.getAuthority()))))
            .map(throwingMapperWrapper(this::owner))
            .filter(Objects::nonNull)
            .filter(this::hasSciper)
            .findFirst();
    }

    private EPerson owner(Item author) {

        if (author == null) {
            return null;
        }

        List<MetadataValue> metadataByMetadataString = itemService
            .getMetadataByMetadataString(author, "dspace.object.owner");
        if (metadataByMetadataString.isEmpty()) {
            return null;
        }

        MetadataValue metadataValue = metadataByMetadataString.get(0);
        if (StringUtils.isBlank(metadataValue.getAuthority())) {
            return null;
        }

        try {
            return ePersonService.find(context, UUID.fromString(metadataValue.getAuthority()));
        } catch (SQLException e) {
            handler.handleException(e);
            throw new RuntimeException(e);
        }

    }

    private EPerson getEPersonFromMetadata(Item item, String metadata) {
        String email = itemService.getMetadata(item, metadata);

        try {
            return ePersonService.findByEmail(context, email);
        } catch (SQLException e) {
            return null;
        }
    }

    private boolean hasSciper(EPerson ePerson) {
        return ePerson != null && StringUtils.isNotBlank(ePerson.getNetid());
    }

    private void assignCurrentUserInContext() {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = null;
            try {
                ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            } catch (SQLException e) {
                handler.handleException(e);
            }
            context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() {
        for (UUID uuid : handler.getSpecialGroups()) {
            context.setSpecialGroup(uuid);
        }
    }
}
