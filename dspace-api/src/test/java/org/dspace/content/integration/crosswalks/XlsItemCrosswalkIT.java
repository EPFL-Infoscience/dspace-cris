/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks;

import static org.dspace.builder.CollectionBuilder.createCollection;
import static org.dspace.builder.CommunityBuilder.createCommunity;
import static org.dspace.util.WorkbookUtils.getRowValues;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.CrosswalkMode;
import org.dspace.content.crosswalk.CrosswalkObjectNotSupported;
import org.dspace.utils.DSpace;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the {@link XlsItemCrosswalk}, which handles the single-item
 * full-metadata (XLS) export (item level, SINGLE).
 *
 * @author Giulia Barucca (giulia.barucca at 4science.com)
 *
 */
public class XlsItemCrosswalkIT extends AbstractIntegrationTestWithDatabase {

    private XlsItemCrosswalk xlsItemCrosswalk;

    private Community community;

    @Before
    public void setup() throws SQLException, AuthorizeException {

        StreamDisseminationCrosswalkMapper crosswalkMapper = new DSpace()
            .getSingletonService(StreamDisseminationCrosswalkMapper.class);
        assertThat(crosswalkMapper, notNullValue());

        xlsItemCrosswalk = (XlsItemCrosswalk) crosswalkMapper.getByType("publication-item-xls");
        assertThat(xlsItemCrosswalk, notNullValue());

        context.turnOffAuthorisationSystem();
        community = createCommunity(context).build();
        context.restoreAuthSystemState();
    }

    @Test
    public void testCrosswalkModeIsSingle() {
        assertThat(xlsItemCrosswalk.getCrosswalkMode(), is(CrosswalkMode.SINGLE));
    }

    @Test
    public void testCanDisseminateOnlyItem() throws Exception {
        context.turnOffAuthorisationSystem();

        Collection collection = createCollection(context, community)
            .withSubmissionDefinition("publication")
            .withAdminGroup(eperson)
            .build();

        Item item = ItemBuilder.createItem(context, collection)
            .withTitle("A Publication")
            .withEntityType("Publication")
            .withIssueDate("2020-01-01")
            .build();

        context.restoreAuthSystemState();

        assertThat(xlsItemCrosswalk.canDisseminate(context, item), is(true));
        assertThat(xlsItemCrosswalk.canDisseminate(context, collection), is(false));
    }

    @Test
    public void testSingleItemDisseminate() throws Exception {

        context.turnOffAuthorisationSystem();

        Collection collection = createCollection(context, community)
            .withSubmissionDefinition("publication")
            .withAdminGroup(eperson)
            .build();

        Item item = ItemBuilder.createItem(context, collection)
            .withTitle("Single Item Publication")
            .withEntityType("Publication")
            .withAuthor("Test, User")
            .withIssueDate("2020-01-01")
            .build();

        // create a second item in the same collection to make sure that the
        // single-item dissemination exports only the requested item.
        ItemBuilder.createItem(context, collection)
            .withTitle("Another Publication")
            .withEntityType("Publication")
            .withIssueDate("2021-01-01")
            .build();

        context.restoreAuthSystemState();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        xlsItemCrosswalk.disseminate(context, item, out);

        byte[] content = out.toByteArray();
        assertThat(content.length, Matchers.greaterThan(0));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet mainSheet = workbook.getSheetAt(0);
            // header row + exactly one data row for the single requested item
            assertThat(mainSheet.getPhysicalNumberOfRows(), is(2));
            List<String> dataRow = getRowValues(mainSheet.getRow(1), mainSheet.getRow(0).getLastCellNum());
            assertThat(dataRow, Matchers.hasItem(containsString("Single Item Publication")));
        }
    }

    @Test
    public void testSingleItemDisseminatePerson() throws Exception {
        assertSingleItemDumpContainsTitle("person", "Person", "John Smith");
    }

    @Test
    public void testSingleItemDisseminateOrgUnit() throws Exception {
        assertSingleItemDumpContainsTitle("orgunit", "OrgUnit", "Test Organization");
    }

    @Test
    public void testDisseminateIteratorIsNotSupported() throws Exception {
        context.turnOffAuthorisationSystem();

        Collection collection = createCollection(context, community)
            .withSubmissionDefinition("publication")
            .withAdminGroup(eperson)
            .build();

        Item item = ItemBuilder.createItem(context, collection)
            .withTitle("A Publication")
            .withEntityType("Publication")
            .withIssueDate("2020-01-01")
            .build();

        context.restoreAuthSystemState();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Assert.assertThrows(CrosswalkObjectNotSupported.class,
            () -> xlsItemCrosswalk.disseminate(context, IteratorUtils.singletonIterator(item), out));
    }

    /**
     * Helper that builds a single item of the given entity type in a collection with the given
     * submission definition, disseminates it as a full-metadata (XLS) dump and asserts that the
     * generated workbook is non-empty and contains the item title.
     */
    private void assertSingleItemDumpContainsTitle(String submissionDefinition, String entityType, String title)
            throws Exception {
        context.turnOffAuthorisationSystem();

        Collection collection = createCollection(context, community)
            .withSubmissionDefinition(submissionDefinition)
            .withAdminGroup(eperson)
            .build();

        Item item = ItemBuilder.createItem(context, collection)
            .withTitle(title)
            .withEntityType(entityType)
            .build();

        context.restoreAuthSystemState();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        xlsItemCrosswalk.disseminate(context, item, out);

        byte[] content = out.toByteArray();
        assertThat(content.length, Matchers.greaterThan(0));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet mainSheet = workbook.getSheetAt(0);
            // header row + exactly one data row for the single requested item
            assertThat(mainSheet.getPhysicalNumberOfRows(), is(2));
            List<String> dataRow = getRowValues(mainSheet.getRow(1), mainSheet.getRow(0).getLastCellNum());
            assertThat(dataRow, Matchers.hasItem(containsString(title)));
        }
    }

}
