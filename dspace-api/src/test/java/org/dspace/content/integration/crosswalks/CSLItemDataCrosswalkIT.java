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
import static org.dspace.builder.ItemBuilder.createItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.StreamDisseminationCrosswalk;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.utils.DSpace;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Integration tests for {@link CSLItemDataCrosswalk}.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class CSLItemDataCrosswalkIT extends AbstractIntegrationTestWithDatabase {

    private static final String BASE_OUTPUT_DIR_PATH = "./target/testing/dspace/assetstore/crosswalk/";

    private ItemService itemService;

    private StreamDisseminationCrosswalkMapper crosswalkMapper;

    private CSLItemDataCrosswalk publicationHtmlCrosswalk;

    private Community community;

    private Collection collection;

    @Before
    public void setup() throws SQLException, AuthorizeException {

        this.itemService = ContentServiceFactory.getInstance().getItemService();
        this.crosswalkMapper = new DSpace().getSingletonService(StreamDisseminationCrosswalkMapper.class);
        assertThat(crosswalkMapper, notNullValue());

        this.publicationHtmlCrosswalk = new DSpace().getServiceManager()
            .getServiceByName("referCrosswalkPublicationIeeeHtml", CSLItemDataCrosswalk.class);

        context.turnOffAuthorisationSystem();
        community = createCommunity(context).build();
        collection = createCollection(context, community).withAdminGroup(eperson).build();
        context.restoreAuthSystemState();

    }

    @Test
    public void testSingleItemDisseminate() throws Exception {

        context.turnOffAuthorisationSystem();
        Item item = createItem(context, collection)
            .withTitle("Publication title")
            .withEntityType("Publication")
            .withIssueDate("2018-05-17")
            .withHandle("123456789/0004")
            .withType("text::report::technical report", "report-coar-types:c_18ws")
            .withAuthor("John Smith")
            .withAuthor("Edward Red")
            .build();
        context.restoreAuthSystemState();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        publicationHtmlCrosswalk.disseminate(context, item, out);

        try (FileInputStream fis = getFileInputStream("publication-ieee.html")) {
            String expectedHtml = IOUtils.toString(fis, Charset.defaultCharset());
            compareEachLine(out.toString(), expectedHtml, false);
        }
    }

    @Test
    public void testManyItemsDisseminate() throws Exception {

        context.turnOffAuthorisationSystem();

        Item firstItem = createItem(context, collection)
            .withTitle("Publication title")
            .withEntityType("Publication")
            .withIssueDate("2018-05-17")
            .withType("text::report::technical report")
            .withAuthor("John Smith")
            .withAuthor("Edward Red")
            .withHandle("123456789/0001")
            .build();

        Item secondItem = createItem(context, collection)
            .withTitle("Test publication")
            .withEntityType("Publication")
            .withIssueDate("2020-01-31")
            .withHandle("123456789/0003")
            .withType("text::report::technical report")
            .withAuthor("Walter White")
            .build();

        context.restoreAuthSystemState();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        publicationHtmlCrosswalk.disseminate(context, Arrays.asList(firstItem, secondItem).iterator(), out);

        try (FileInputStream fis = getFileInputStream("publications-ieee.html")) {
            String expectedHtml = IOUtils.toString(fis, Charset.defaultCharset());
            compareEachLine(out.toString(), expectedHtml, false);
        }
    }

    @Test
    public void testBibtexDisseminate() throws Exception {

        context.turnOffAuthorisationSystem();

        Item item = createItem(context, collection)
            .withEntityType("Publication")
            .withType("text::journal::journal article")
            .withLanguage("en")
            .withDoiIdentifier("10.1000/182")
            .withRelationIsbn("11-22-33")
            .withIssnIdentifier("0002")
            .withSubject("publication")
            .withPublisher("Publisher")
            .withVolume("V01")
            .withIssue("03")
            .withRelationConference("Conference")
            .withTitle("Publication title")
            .withIssueDate("2018-05-17")
            .withAuthor("Smith, John")
            .withAuthor("Red, Edward")
            .withScientificEditor("Editor", null)
            .withHandle("123456789/0001")
            .build();

        context.restoreAuthSystemState();
        Item itemMock = Mockito.spy(item);
        Mockito.when(itemMock.getID()).thenReturn(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        StreamDisseminationCrosswalk crosswalk = crosswalkMapper.getByType("bibtex");
        assertThat(crosswalk, notNullValue());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        crosswalk.disseminate(context, itemMock, out);

        try (FileInputStream fis = getFileInputStream("publication.bib")) {
            String expectedBibtex = IOUtils.toString(fis, Charset.defaultCharset());
            compareEachLine(out.toString(), expectedBibtex, false);
        }
    }

    @Ignore("To be rechecked: it seems that citeproc 3.3 ignores the journalAbbreviation")
    @Test
    public void testBibtexDisseminateWithDIfferentTypes() throws Exception {

        context.turnOffAuthorisationSystem();

        Item item = createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::journal::journal article")
                .withIsPartOf("isPartOf")
                .withRelationJournal("relationJournal", null)
                .withHandle("123456789/0002")
                .build();

        Item item2 = createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::book/monograph::book part or chapter")
                .withIsPartOf("isPartOf")
                .withRelationJournal("relationJournal", null)
                .withHandle("123456789/0003")
                .build();

        context.restoreAuthSystemState();
        Item itemMock = Mockito.spy(item);
        Mockito.when(itemMock.getID()).thenReturn(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        StreamDisseminationCrosswalk crosswalk = crosswalkMapper.getByType("bibtex");
        assertThat(crosswalk, notNullValue());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        crosswalk.disseminate(context, itemMock, out);

        try (FileInputStream fis = getFileInputStream("journal.bib")) {
            String expectedBibtex = IOUtils.toString(fis, Charset.defaultCharset());
            compareEachLine(out.toString(), expectedBibtex, false);
        }

        Item itemMock2 = Mockito.spy(item2);
        Mockito.when(itemMock2.getID()).thenReturn(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        StreamDisseminationCrosswalk crosswalk2 = crosswalkMapper.getByType("bibtex");
        assertThat(crosswalk2, notNullValue());

        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        crosswalk.disseminate(context, itemMock2, out2);

        try (FileInputStream fis = getFileInputStream("book.bib")) {
            String expectedBibtex = IOUtils.toString(fis, Charset.defaultCharset());
            compareEachLine(out2.toString(), expectedBibtex, false);
        }
    }

    @Test
    public void testSingleItemJsonDisseminate() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = createItem(context, collection)
            .withEntityType("Publication")
            .withType("text::working paper")
            .withLanguage("en")
            .withDoiIdentifier("10.1000/182")
            .withRelationIsbn("11-22-33")
            .withIssnIdentifier("0002")
            .withSubject("publication")
            .withPublisher("Publisher")
            .withVolume("V01")
            .withIssue("03")
            .withRelationConference("Conference")
            .withTitle("Publication title")
            .withIssueDate("2018-05-17")
            .withAuthor("Smith, John")
            .withAuthor("Red, Edward")
            .withScientificEditor("Editor", null)
            .withHandle("123456789/0001")
            .build();

        itemService.setMetadataSingleValue(context, item, "dc", "date", "available", null, "2018-05-17");
        itemService.update(context, item);

        context.restoreAuthSystemState();
        Item itemMock = Mockito.spy(item);
        Mockito.when(itemMock.getID()).thenReturn(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        StreamDisseminationCrosswalk crosswalk = crosswalkMapper.getByType("publication-json");
        assertThat(crosswalk, notNullValue());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        crosswalk.disseminate(context, itemMock, out);

        try (FileInputStream fis = getFileInputStream("publication.json")) {
            String expectedJson = IOUtils.toString(fis, Charset.defaultCharset());
            compareEachLine(out.toString(), expectedJson, true);
        }
    }

    @Test
    public void testMutlipleItemsJsonDisseminate() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = createItem(context, collection)
            .withEntityType("Publication")
            .withType("text::journal::journal article")
            .withLanguage("en")
            .withDoiIdentifier("10.1000/182")
            .withRelationIsbn("11-22-33")
            .withIssnIdentifier("0002")
            .withSubject("publication")
            .withPublisher("Publisher")
            .withVolume("V01")
            .withIssue("03")
            .withRelationConference("Conference")
            .withTitle("Publication title")
            .withIssueDate("2018-05-17")
            .withAuthor("Smith, John")
            .withAuthor("Red, Edward")
            .withScientificEditor("Editor", null)
            .withHandle("123456789/0001")
            .build();

        Item anotherItem = createItem(context, collection)
            .withEntityType("Publication")
            .withType("text::book/monograph")
            .withLanguage("en")
            .withDoiIdentifier("10.1000/183")
            .withTitle("Another Publication title")
            .withIssueDate("2020-01-01")
            .withAuthor("White, Walter")
            .withHandle("123456789/0002")
            .build();

        itemService.setMetadataSingleValue(context, item, "dc", "date", "available", null, "2018-05-17");
        itemService.setMetadataSingleValue(context, anotherItem, "dc", "date", "available", null, "2020-01-01");
        itemService.update(context, item);
        itemService.update(context, anotherItem);

        context.restoreAuthSystemState();

        Item itemMock = Mockito.spy(item);
        Mockito.when(itemMock.getID()).thenReturn(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        Item anotherItemMock = Mockito.spy(anotherItem);
        Mockito.when(anotherItemMock.getID()).thenReturn(UUID.fromString("550e8400-e29b-41d4-a716-44665544000a"));

        StreamDisseminationCrosswalk crosswalk = crosswalkMapper.getByType("publication-json");
        assertThat(crosswalk, notNullValue());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        crosswalk.disseminate(context, Arrays.asList(itemMock, anotherItemMock).iterator(), out);

        try (FileInputStream fis = getFileInputStream("publications.json")) {
            String expectedJson = IOUtils.toString(fis, Charset.defaultCharset());
            compareEachLine(out.toString(), expectedJson, true);
        }
    }

    @Test
    public void testSingleItemApaNoGenreDisseminate() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = createItem(context, collection)
                .withEntityType("Publication")
                .withType("text::journal::journal article::research article", "article-coar-types:c_2df8fbb1")
                .withLanguage("en")
                .withDoiIdentifier("10.1000/182")
                .withRelationIsbn("11-22-33")
                .withIssnIdentifier("0002")
                .withSubject("publication")
                .withPublisher("Publisher")
                .withVolume("V01")
                .withIssue("03")
                .withRelationConference("Conference")
                .withTitle("Publication title")
                .withIssueDate("2018-05-17")
                .withAuthor("Smith, John")
                .withAuthor("Red, Edward")
                .withScientificEditor("Editor", null)
                .withHandle("123456789/0001")
                .build();

        context.restoreAuthSystemState();

        StreamDisseminationCrosswalk crosswalk = crosswalkMapper.getByType("publication-apa");
        assertThat(crosswalk, notNullValue());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        crosswalk.disseminate(context, Collections.singletonList(item).iterator(), out);

        String citation = out.toString();

        assertThat(citation, not(containsString("c_2df8fbb1")));
    }

    @Test
    public void testMutlipleItemsApaDisseminate() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = createItem(context, collection)
            .withEntityType("Publication")
            .withType("text::journal::journal article")
            .withLanguage("en")
            .withDoiIdentifier("10.1000/182")
            .withRelationIsbn("11-22-33")
            .withIssnIdentifier("0002")
            .withSubject("publication")
            .withPublisher("Publisher")
            .withVolume("V01")
            .withIssue("03")
            .withRelationConference("Conference")
            .withTitle("Publication title")
            .withIssueDate("2018-05-17")
            .withAuthor("Smith, John")
            .withAuthor("Red, Edward")
            .withScientificEditor("Editor", null)
            .withHandle("123456789/0001")
            .build();

        Item anotherItem = createItem(context, collection)
            .withEntityType("Publication")
            .withType("text::book")
            .withLanguage("en")
            .withDoiIdentifier("10.1000/183")
            .withTitle("Another Publication title")
            .withIssueDate("2020-01-01")
            .withAuthor("White, Walter")
            .withHandle("123456789/0002")
            .build();

        context.restoreAuthSystemState();

        StreamDisseminationCrosswalk crosswalk = crosswalkMapper.getByType("publication-apa");
        assertThat(crosswalk, notNullValue());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        crosswalk.disseminate(context, Arrays.asList(item, anotherItem).iterator(), out);

        try (FileInputStream fis = getFileInputStream("apa.txt")) {
            String expectedContent = IOUtils.toString(fis, Charset.defaultCharset());
            compareEachLine(out.toString(), expectedContent, true);
        }
    }

    private void compareEachLine(String result, String expectedResult, boolean skipId) {

        String[] resultLines = result.split("\n");
        String[] expectedResultLines = expectedResult.split("\n");

        assertThat("The result should have the same lines number of the expected result",
            resultLines.length, equalTo(expectedResultLines.length));

        for (int i = 0; i < resultLines.length; i++) {
            String expectedResultLine = expectedResultLines[i];
            if (skipId && expectedResultLine.contains("id")) {
                continue;
            }
            assertThat(resultLines[i], equalTo(expectedResultLine));
        }
    }

    private FileInputStream getFileInputStream(String name) throws FileNotFoundException {
        return new FileInputStream(new File(BASE_OUTPUT_DIR_PATH, name));
    }
}
