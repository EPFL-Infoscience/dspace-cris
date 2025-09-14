/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.test;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.dspace.app.policy.PolicyMetadataUtils.dataciteAvailableMetadata;
import static org.dspace.app.policy.PolicyMetadataUtils.dataciteRightsMetadata;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;

import com.ibm.icu.text.SimpleDateFormat;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.access.status.AccessStatusHelper;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.policy.PolicyMetadataUtils;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.ResourcePolicyBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.junit.Assert;
import org.junit.Test;


public class PolicyMetadataScriptIT extends AbstractIntegrationTestWithDatabase {

    private EPerson submitter;

    private Collection publicationCollection;

    private Community subCommunity;

    private ItemService itemService;

    private BitstreamService bitstreamService;

    private Group anonymousGroup;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        context.turnOffAuthorisationSystem();
        itemService = ContentServiceFactory.getInstance().getItemService();
        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        anonymousGroup = EPersonServiceFactory.getInstance().getGroupService().findByName(context, Group.ANONYMOUS);

        submitter = EPersonBuilder.createEPerson(context)
                .withEmail("submitter@example.com")
                .withPassword(password)
                .build();

        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();

        subCommunity = CommunityBuilder.createCommunity(context)
                .withName("Sub Community")
                .addParentCommunity(context, parentCommunity)
                .build();

        publicationCollection = createCollection("Collection of publications", "Publication", subCommunity);

        context.setCurrentUser(submitter);

        context.restoreAuthSystemState();

    }

    private Collection createCollection(String name, String entityType, Community community) throws Exception {
        return CollectionBuilder.createCollection(context, community)
                .withName(name)
                .withEntityType(entityType)
                .withSubmissionDefinition("traditional")
                .withSubmitterGroup(submitter)
                .build();
    }

    @Test
    public void testMetadataOnly() throws Exception {

        String[] args = new String[] { "access-status-metadata" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        context.turnOffAuthorisationSystem();

        publicationCollection = context.reloadEntity(publicationCollection);

        Item metadataOnlyItem = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test Publication 2")
                .build();

        context.commit();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        metadataOnlyItem = context.reloadEntity(metadataOnlyItem);
        context.restoreAuthSystemState();
        assertThat(itemService.getMetadata(metadataOnlyItem, dataciteRightsMetadata.toString()),
                is(PolicyMetadataUtils.METADATA_ONLY));
    }

    @Test
    public void testOpenAccess() throws Exception {

        String[] args = new String[] { "access-status-metadata" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        context.turnOffAuthorisationSystem();

        publicationCollection = context.reloadEntity(publicationCollection);

        Item openAccessItem = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test Publication 2")
                .build();

        // Add a bitstream to an item
        Bitstream bitstream = null;
        try (InputStream is = IOUtils.toInputStream("content", CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, openAccessItem, is)
                    .withName("Bitstream")
                    .withDescription("description")
                    .withMimeType("text/plain")
                    .build();
        }
        context.commit();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        openAccessItem = context.reloadEntity(openAccessItem);
        bitstream = context.reloadEntity(bitstream);
        context.restoreAuthSystemState();
        assertThat(itemService.getMetadata(openAccessItem, dataciteRightsMetadata.toString()),
                is(PolicyMetadataUtils.ACCESS_OPEN));
        assertThat(bitstreamService.getMetadata(bitstream, dataciteRightsMetadata.toString()),
                is(PolicyMetadataUtils.ACCESS_OPEN));
    }

    @Test
    public void testRestricted() throws Exception {

        String[] args = new String[] { "access-status-metadata" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        context.turnOffAuthorisationSystem();

        publicationCollection = context.reloadEntity(publicationCollection);

        Item restrictedItem = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test Publication 2")
                .build();

        // Add a bitstream to an item
        Bitstream bitstream = null;
        try (InputStream is = IOUtils.toInputStream("content", CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, restrictedItem, is)
                    .withName("Bitstream")
                    .withDescription("description")
//                    .withReaderGroup(
//                            EPersonServiceFactory.getInstance().getGroupService().findByName(context, Group.ADMIN))
                    .withMimeType("text/plain")
                    .build();
        }

        ResourcePolicyBuilder
                .createResourcePolicy(context, admin, null)
                .withDspaceObject(bitstream)
                .withAction(Constants.READ)
                .withPolicyType("TYPE_CUSTOM")
                .withName(PolicyMetadataUtils.ACCESS_RESTRICTED)
                .build();


        context.commit();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        restrictedItem = context.reloadEntity(restrictedItem);
        bitstream = context.reloadEntity(bitstream);
        context.restoreAuthSystemState();
        assertThat(itemService.getMetadata(restrictedItem, dataciteRightsMetadata.toString()),
                is(PolicyMetadataUtils.ACCESS_RESTRICTED));
        assertThat(bitstreamService.getMetadata(bitstream, dataciteRightsMetadata.toString()),
                is(PolicyMetadataUtils.ACCESS_RESTRICTED));
    }

    @Test
    public void testEmbargo() throws Exception {

        String[] args = new String[] { "access-status-metadata" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        context.turnOffAuthorisationSystem();

        publicationCollection = context.reloadEntity(publicationCollection);

        Item embargoItem = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test Publication 2")
                .build();

        // Add a bitstream to an item
        Bitstream bitstream = null;
        try (InputStream is = IOUtils.toInputStream("content", CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, embargoItem, is)
                    .withName("Bitstream")
                    .withDescription("description")
                    //.withEmbargoPeriod("3 months")
                    .withMimeType("text/plain")
                    .build();
        }

        String embargoDateAsString = "2050-01-01";
        ResourcePolicyBuilder
                .createResourcePolicy(context, admin, null)
                .withDspaceObject(bitstream)
                .withAction(Constants.READ)
                .withPolicyType("TYPE_CUSTOM")
                .withName("embargo")
                .withStartDate((new java.text.SimpleDateFormat("yyyy-MM-dd")).parse(embargoDateAsString))
                .build();



        context.restoreAuthSystemState();

        context.commit();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        embargoItem = context.reloadEntity(embargoItem);
        bitstream = context.reloadEntity(bitstream);
        assertThat(itemService.getMetadata(embargoItem, dataciteRightsMetadata.toString()),
                is(AccessStatusHelper.EMBARGO));
        assertThat(bitstreamService.getMetadata(bitstream, dataciteRightsMetadata.toString()),
                is(AccessStatusHelper.EMBARGO));
        String embargoDate = itemService.getMetadata(embargoItem, dataciteAvailableMetadata.toString());
        String embargoDateBitstream = bitstreamService.getMetadata(bitstream, dataciteAvailableMetadata.toString());

        //Date threeMonthsFromNowNoTime = getCurrentDatePlus3Months();
        Date embargoDateParsed = parseEmbargoDate(embargoDateAsString);

        Date embargoParsed = parseEmbargoDate(embargoDate);
        Date embargoBitstreamParsed = parseEmbargoDate(embargoDateBitstream);
        assertThat(embargoDateParsed, is(embargoParsed));
        assertThat(embargoDateParsed, is(embargoBitstreamParsed));
    }

    @Test
    public void testExpiredEmbargo() throws Exception {
        String[] args = new String[] { "access-status-metadata" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        context.turnOffAuthorisationSystem();

        publicationCollection = context.reloadEntity(publicationCollection);

        Item embargoItem = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test Publication expired embargo")
                .build();

        // Add a bitstream to an item
        Bitstream bitstream = null;
        try (InputStream is = IOUtils.toInputStream("content", CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, embargoItem, is)
                    .withName("Bitstream")
                    .withDescription("description")
                    .withMimeType("text/plain")
                    .build();
        }

        String embargoDateAsString = "2020-01-01";
        ResourcePolicyBuilder
                .createResourcePolicy(context, null, anonymousGroup)
                .withDspaceObject(bitstream)
                .withAction(Constants.READ)
                .withPolicyType("TYPE_CUSTOM")
                .withName("embargo")
                .withStartDate((new java.text.SimpleDateFormat("yyyy-MM-dd")).parse(embargoDateAsString))
                .build();

        context.restoreAuthSystemState();
        context.commit();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        embargoItem = context.reloadEntity(embargoItem);
        bitstream = context.reloadEntity(bitstream);

        assertThat(itemService.getMetadata(embargoItem, dataciteRightsMetadata.toString()),
                is(PolicyMetadataUtils.ACCESS_OPEN));
        assertThat(bitstreamService.getMetadata(bitstream, dataciteRightsMetadata.toString()),
                is(PolicyMetadataUtils.ACCESS_OPEN));

        String embargoDate = itemService.getMetadata(embargoItem, dataciteAvailableMetadata.toString());
        String embargoDateBitstream = bitstreamService.getMetadata(bitstream, dataciteAvailableMetadata.toString());

        Assert.assertNull(embargoDate);
        Assert.assertNull(embargoDateBitstream);
    }

    private Date getCurrentDatePlus3Months() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 3);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date parseEmbargoDate(String embargoDate) throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        // Parse the input date
        Date inputDate = formatter.parse(embargoDate);

        // Calculate today's date + 3 months with time cleared
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(inputDate);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTime();
    }

    @Test
    public void testScript() throws Exception {

        context.turnOffAuthorisationSystem();
        Item metadataOnlyItem = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test Publication 2")
                .build();

        Item openAccessItem = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test Publication 2")
                .build();

        // Add a bitstream to an item
        Bitstream openAccessBitstream = null;
        try (InputStream is = IOUtils.toInputStream("content", CharEncoding.UTF_8)) {
            openAccessBitstream = BitstreamBuilder.createBitstream(context, openAccessItem, is)
                    .withName("Bitstream")
                    .withDescription("description")
                    .withMimeType("text/plain")
                    .build();
        }

        Item embargoItem = ItemBuilder.createItem(context, publicationCollection)
                .withTitle("Test Publication 2")
                .build();

        // Add a bitstream to an item
        Bitstream bitstreamEmb = null;
        try (InputStream is = IOUtils.toInputStream("content", CharEncoding.UTF_8)) {
            bitstreamEmb = BitstreamBuilder.createBitstream(context, embargoItem, is)
                    .withName("Bitstream")
                    .withDescription("description")
                    //.withEmbargoPeriod("3 months")
                    .withMimeType("text/plain")
                    .build();
        }

        String embargoDateAsString = "2050-01-01";
        ResourcePolicyBuilder
                .createResourcePolicy(context, admin, null)
                .withDspaceObject(bitstreamEmb)
                .withAction(Constants.READ)
                .withPolicyType("TYPE_CUSTOM")
                .withName("embargo")
                .withStartDate((new java.text.SimpleDateFormat("yyyy-MM-dd")).parse(embargoDateAsString))
                .build();

        String[] args = new String[] { "access-status-metadata" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        context.commit();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        openAccessItem = context.reloadEntity(openAccessItem);
        openAccessBitstream = context.reloadEntity(openAccessBitstream);
        bitstreamEmb = context.reloadEntity(bitstreamEmb);
        metadataOnlyItem = context.reloadEntity(metadataOnlyItem);
        embargoItem = context.reloadEntity(embargoItem);
        context.restoreAuthSystemState();
        assertThat(itemService.getMetadata(embargoItem, dataciteRightsMetadata.toString()),
                is(AccessStatusHelper.EMBARGO));
        assertThat(bitstreamService.getMetadata(bitstreamEmb, dataciteRightsMetadata.toString()),
                is(AccessStatusHelper.EMBARGO));
        String embargoDate = itemService.getMetadata(embargoItem, dataciteAvailableMetadata.toString());
        String embargoDateBitstream = bitstreamService.getMetadata(bitstreamEmb,
                dataciteAvailableMetadata.toString());

        //Date threeMonthsFromNowNoTime = getCurrentDatePlus3Months();
        Date embargoDateParsed = parseEmbargoDate(embargoDateAsString);

        Date embargoParsed = parseEmbargoDate(embargoDate);
        Date embargoBitstreamParsed = parseEmbargoDate(embargoDateBitstream);
        assertThat(embargoDateParsed, is(embargoParsed));
        assertThat(embargoDateParsed, is(embargoBitstreamParsed));

        assertThat(itemService.getMetadata(metadataOnlyItem, dataciteRightsMetadata.toString()),
                is(PolicyMetadataUtils.METADATA_ONLY));

        assertThat(itemService.getMetadata(openAccessItem, dataciteRightsMetadata.toString()),
                is(PolicyMetadataUtils.ACCESS_OPEN));
        assertThat(bitstreamService.getMetadata(openAccessBitstream, dataciteRightsMetadata.toString()),
                is(PolicyMetadataUtils.ACCESS_OPEN));
    }

    @Override
    public void destroy() throws Exception {
        super.destroy();
    }
}
