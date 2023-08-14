/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.cli.ParseException;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.ItemServiceImpl;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;


public class ItemsReportScript
        extends DSpaceRunnable<ItemsReportScriptConfiguration<ItemsReportScript>> {

    private Context context;
    private ItemService itemService;

    @Override
    @SuppressWarnings("unchecked")
    public ItemsReportScriptConfiguration<ItemsReportScript> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("items-report",
                ItemsReportScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        itemService = new DSpace().getSingletonService(ItemServiceImpl.class);
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        context.turnOffAuthorisationSystem();

        createItemReport();

        context.complete();
        context.restoreAuthSystemState();
    }



    private void createItemReport() {
        Iterator<Item> allItems = getAllItems();
        List<BitstreamsWithItems> bitstreamsWithItemsList = Arrays.asList(
            new BitstreamsWithItems("open access", "openaccess"),
            new BitstreamsWithItems("reserved access", "reserved"),
            new BitstreamsWithItems("restricted access", "restricted")
        );

        List<BitstreamsWithItemsEmbargo>  bitstreamsWithItemsEmbargoList = Arrays.asList(
            new BitstreamsWithItemsEmbargo("active embargo access", "embargo", true),
            new BitstreamsWithItemsEmbargo("expired embargo access", "embargo", false)
        );

        while (allItems.hasNext()) {
            Item item = allItems.next();
            if (isItemABooleanSuitableForReport(item)) {
                Iterator<Bitstream> bitstreams = getOriginalBitstreams(item);
                while (bitstreams.hasNext()) {
                    Bitstream bitstream = bitstreams.next();
                    for (BitstreamsWithItems bitstreamsWithItems : bitstreamsWithItemsList) {
                        addBitstreamsToReport(bitstreamsWithItems, bitstream);
                        addItemToBitstreamsWithItems(bitstreamsWithItems, item);
                    }
                    for (BitstreamsWithItemsEmbargo bitstreamsWithItemsEmbargo : bitstreamsWithItemsEmbargoList) {
                        addBitstreamsToReportWithEmbargo(bitstreamsWithItemsEmbargo, bitstream);
                        addItemToBitstreamsWithItems(bitstreamsWithItemsEmbargo, item);
                    }
                }
            }
        }
        Stream.concat(bitstreamsWithItemsEmbargoList.stream(), bitstreamsWithItemsList.stream())
                .forEach(this::logBitstreamsWithItems);
    }

    private void addBitstreamsToReport(BitstreamsWithItems bitstreamsWithItems, Bitstream bitstream) {
        Optional<MetadataValue> metadataValue = getBitstreamMetadataValue(bitstream, "datacite_rights");

        if (metadataValue.isEmpty()) {
            return;
        }
        if (metadataValue.get().getValue().equals(bitstreamsWithItems.getCorrectValue())) {
            bitstreamsWithItems.addBitstream(bitstream);
        }
    }

    private void addBitstreamsToReportWithEmbargo(BitstreamsWithItemsEmbargo bitstreamsWithItemsEmbargo,
                                                  Bitstream bitstream) {
        Optional<MetadataValue> metadataValueRights = getBitstreamMetadataValue(bitstream, "datacite_rights");

        Optional<MetadataValue> metadataValueAvailable = getBitstreamMetadataValue(bitstream, "datacite_available");

        if (metadataValueRights.isEmpty() || metadataValueAvailable.isEmpty()) {
            return;
        }
        if (metadataValueRights.get().getValue().equals(bitstreamsWithItemsEmbargo.getCorrectValue())
                && isMetadataAvailableHasCorrectValue(metadataValueAvailable.get().getValue(),
                                                      bitstreamsWithItemsEmbargo.isActive())) {
            bitstreamsWithItemsEmbargo.addBitstream(bitstream);
        }
    }

    private boolean isMetadataAvailableHasCorrectValue(String value, boolean isActive) {
        if (isActive) {
            return isDateTodayOrInTheFuture(value);
        } else {
            return isDatePast(value);
        }
    }

    private Optional<MetadataValue> getBitstreamMetadataValue(Bitstream bitstream, String metadataField) {
        return bitstream.getMetadata().stream()
                .filter(metadataValue -> metadataValue.getMetadataField().toString().equals(metadataField))
                .findFirst();
    }

    private void addItemToBitstreamsWithItems(BitstreamsWithItems bitstreamsWithItems, Item item) {
        if (bitstreamsWithItems.getNeedToAddItem()) {
            bitstreamsWithItems.addItem(item);
        }
    }

    private void logBitstreamsWithItems(BitstreamsWithItems bitstreamsWithItems) {
        handler.logInfo("--------------------------------------------------------");
        handler.logInfo("--------------------------------------------------------");
        handler.logInfo("Number of bitstreams which have " + bitstreamsWithItems.getLogMessage()
                + " policy: " + bitstreamsWithItems.getBitstreams().size());
        handler.logInfo("List of bitstreams matching above conditions:");
        bitstreamsWithItems.getBitstreams().forEach(bitstream ->
                handler.logInfo("Bitstream title: " + bitstream.getName() + ", uuid: " + bitstream.getID()));

        handler.logInfo("--------------------------------------------------------");
        handler.logInfo("Number of items having at least one attachment in their “original” bundle which have "
                + bitstreamsWithItems.getLogMessage() + " policy: " + bitstreamsWithItems.getBitstreams().size());
        handler.logInfo("List of items matching above conditions:");
        bitstreamsWithItems.getItems().forEach(item ->
                handler.logInfo("Items title: " + item.getName() + ", uuid: " + item.getID()));
    }



    private Iterator<Bitstream> getOriginalBitstreams (Item item) {
        try {
            return  itemService.getBundles(item, "ORIGINAL")
                    .stream().flatMap(bundle -> bundle.getBitstreams().stream()).iterator();
        } catch (SQLException e) {
            handler.logError("Error during getting original bitstreams of item with uuid: " + item.getID());
            throw new RuntimeException(e);
        }
    }

    private Iterator<Item> getAllItems() {
        try {
            return itemService.findAll(context);
        } catch (SQLException e) {
            handler.logError("Error during getting all items");
            throw new RuntimeException(e);
        }
    }

    private boolean isItemABooleanSuitableForReport(Item item) {
        return item.isDiscoverable() && !item.isWithdrawn();
    }


    private boolean isDatePast(String date) {
        LocalDate localDate = LocalDate.now();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate inputDate = LocalDate.parse(date, dtf);

        return inputDate.isBefore(localDate);
    }

    private boolean isDateTodayOrInTheFuture(String date) {
        LocalDate localDate = LocalDate.now();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate inputDate = LocalDate.parse(date, dtf);

        return inputDate.isEqual(localDate) || inputDate.isAfter(localDate);
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

    private class BitstreamsWithItems {
        private final List<Bitstream> bitstreams;
        private final List<Item> items;
        private final String logMessage;
        private final String correctValue;

        private boolean needToAddItem = false;

        public BitstreamsWithItems(String logMessage, String correctValue) {
            this.logMessage = logMessage;
            this.correctValue = correctValue;
            bitstreams = new ArrayList<>();
            items = new ArrayList<>();
        }

        public String getLogMessage() {
            return logMessage;
        }

        public List<Bitstream> getBitstreams() {
            return bitstreams;
        }

        public void addBitstream(Bitstream bitstream) {
            bitstreams.add(bitstream);
            needToAddItem = true;
        }

        public void addItem(Item item) {
            items.add(item);
            needToAddItem = false;
        }

        public boolean getNeedToAddItem() {
            return needToAddItem;
        }

        public List<Item> getItems() {
            return items;
        }

        public String getCorrectValue() {
            return correctValue;
        }
    }

    private class BitstreamsWithItemsEmbargo extends BitstreamsWithItems {
        private final boolean isActive;
        public BitstreamsWithItemsEmbargo(String logMessage, String correctValue, boolean isActive) {
            super(logMessage, correctValue);
            this.isActive = isActive;
        }

        public boolean isActive() {
            return isActive;
        }
    }

}
