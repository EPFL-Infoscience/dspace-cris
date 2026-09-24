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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Every dc.type value configured in mapConverter-dcTypesToClsTypes.properties mapped to the
     * CSL type it is expected to produce. Kept in sync with that file: any entry that is not
     * mapped as expected (e.g. because of a malformed key in the properties file) makes
     * {@link #testEveryDcTypeResolvesToExpectedCslType()} fail listing the offending values.
     */
    private static final Map<String, String> DC_TYPE_TO_CSL_TYPE = buildDcTypeToCslTypeMap();

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
            .withMetadata("oaire", "citation", "startPage", "3")
            .withMetadata("oaire", "citation", "endPage", "5")
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
    public void testEditorialDirectorIsPresentWhenTypeResolvesToThesis() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = createItem(context, collection)
            .withEntityType("Publication")
            .withType("text::thesis")
            .withTitle("Thesis title")
            .withIssueDate("2021-07-01")
            .withMetadata("dc", "contributor", "advisor", "Doe, Jane")
            .withHandle("123456789/0100")
            .build();

        context.restoreAuthSystemState();

        StreamDisseminationCrosswalk crosswalk = crosswalkMapper.getByType("publication-json");
        assertThat(crosswalk, notNullValue());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        crosswalk.disseminate(context, item, out);

        String citation = out.toString();

        assertThat(citation, containsString("\"editorial-director\""));
        assertThat(citation, containsString("\"family\": \"Doe\""));
        assertThat(citation, containsString("\"given\": \"Jane\""));
    }

    @Test
    public void testEditorialDirectorIsNotPresentWhenTypeDoesNotResolveToThesis() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = createItem(context, collection)
            .withEntityType("Publication")
            .withType("text::report")
            .withTitle("Report title")
            .withIssueDate("2021-07-01")
            .withMetadata("dc", "contributor", "advisor", "Doe, Jane")
            .withHandle("123456789/0101")
            .build();

        context.restoreAuthSystemState();

        StreamDisseminationCrosswalk crosswalk = crosswalkMapper.getByType("publication-json");
        assertThat(crosswalk, notNullValue());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        crosswalk.disseminate(context, item, out);

        String citation = out.toString();

        assertThat(citation, not(containsString("\"editorial-director\"")));
    }

    @Test
    public void testContainerTitleForReviewUsesRelationJournal() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = createItem(context, collection)
            .withEntityType("Publication")
            .withType("text::review")
            .withTitle("Review title")
            .withIssueDate("2021-07-01")
            .withRelationJournal("Journal for Reviews", null)
            .withIsPartOf("IsPartOf fallback value")
            .withHandle("123456789/0102")
            .build();

        context.restoreAuthSystemState();

        StreamDisseminationCrosswalk crosswalk = crosswalkMapper.getByType("publication-json");
        assertThat(crosswalk, notNullValue());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        crosswalk.disseminate(context, item, out);

        String citation = out.toString();

        assertThat(citation, containsString("\"container-title\": \"Journal for Reviews\""));
        assertThat(citation, not(containsString("\"container-title\": \"IsPartOf fallback value\"")));
    }

    @Test
    public void testEveryDcTypeResolvesToExpectedCslType() throws Exception {

        context.turnOffAuthorisationSystem();
        Item item = createItem(context, collection)
            .withEntityType("Publication")
            .withTitle("Type mapping title")
            .withIssueDate("2021-07-01")
            .withHandle("123456789/1000")
            .build();
        context.restoreAuthSystemState();

        StreamDisseminationCrosswalk crosswalk = crosswalkMapper.getByType("publication-json");
        assertThat(crosswalk, notNullValue());

        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, String> entry : DC_TYPE_TO_CSL_TYPE.entrySet()) {
            String dcType = entry.getKey();
            String expectedCslType = entry.getValue();

            // Reuse the same item, only swapping its dc.type, to avoid the cost of creating
            // one item per mapping entry. Clear any existing dc.type (regardless of language)
            // before setting the new one, so values do not accumulate across iterations.
            context.turnOffAuthorisationSystem();
            itemService.clearMetadata(context, item, "dc", "type", null, Item.ANY);
            itemService.addMetadata(context, item, "dc", "type", null, null, dcType);
            itemService.update(context, item);
            context.restoreAuthSystemState();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            crosswalk.disseminate(context, item, out);

            if (!out.toString().contains("\"type\": \"" + expectedCslType + "\"")) {
                failures.add(dcType + " -> expected " + expectedCslType);
            }
        }

        assertThat("dc.type values not mapped to the expected CSL type: " + failures,
            failures, empty());
    }

    @Test
    public void testISSNRulesPreferRelationIssnOverRelationSerieIssn() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = createItem(context, collection)
            .withEntityType("Publication")
            .withType("text::report")
            .withTitle("ISSN precedence title")
            .withIssueDate("2021-07-01")
            .withRelationIssn("1111-2222")
            .withMetadata("dc", "relation", "serieissn", "3333-4444")
            .withHandle("123456789/0103")
            .build();

        context.restoreAuthSystemState();

        StreamDisseminationCrosswalk crosswalk = crosswalkMapper.getByType("publication-json");
        assertThat(crosswalk, notNullValue());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        crosswalk.disseminate(context, item, out);

        String citation = out.toString();

        assertThat(citation, containsString("\"ISSN\": \"1111-2222\""));
        assertThat(citation, not(containsString("\"ISSN\": \"3333-4444\"")));
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

    private static Map<String, String> buildDcTypeToCslTypeMap() {
        Map<String, String> map = new LinkedHashMap<>();
        String[][] entries = {
            {"text", "document"},
            {"texte", "document"},
            {"text::annotation", "document"},
            {"texte::annotation", "document"},
            {"text::bibliography", "document"},
            {"texte::bibliographie", "document"},
            {"text::blog post", "post-weblog"},
            {"texte::article de blog", "post-weblog"},
            {"text::book/monograph", "book"},
            {"texte::ouvrage/monographie", "book"},
            {"text::book/monograph::book part or chapter", "chapter"},
            {"texte::ouvrage/monographie::chapitre de livre", "chapter"},
            {"texte::ouvrage/monographie::chapitre de livre/partie d'ouvrage", "chapter"},
            {"text::conference output", "paper-conference"},
            {"texte::objet présenté à une conférence", "paper-conference"},
            {"text::conference output::conference paper not in proceedings", "paper-conference"},
            {"texte::objet présenté à une conférence::article dans une conférence non publié dans les actes",
                "paper-conference"},
            {"text::conference output::conference poster not in proceedings", "paper-conference"},
            {"texte::objet présenté à une conférence::poster dans une conférence non publié dans les actes",
                "paper-conference"},
            {"texte::objet présenté à une conférence::poster de conférence hors actes", "paper-conference"},
            {"text::conference output::conference presentation", "speech"},
            {"texte::objet présenté à une conférence::support de présentation à une conférence", "speech"},
            {"texte::objet présenté à une conférence::support de présentation", "speech"},
            {"text::conference output::conference proceedings", "book"},
            {"texte::objet présenté à une conférence::actes de conférence", "book"},
            {"text::conference output::conference proceedings::conference paper", "paper-conference"},
            {"texte::objet présenté à une conférence::actes de conférence::article dans une conférence/papier "
                + "de conférence", "paper-conference"},
            {"texte::objet présenté à une conférence::actes de conférence::article de conférence", "paper-conference"},
            {"text::conference output::conference proceedings::conference poster", "paper-conference"},
            {"texte::objet présenté à une conférence::actes de conférence::poster dans une conférence",
                "paper-conference"},
            {"texte::objet présenté à une conférence::actes de conférence::poster de conférence", "paper-conference"},
            {"text::journal", "periodical"},
            {"texte::revue", "periodical"},
            {"text::journal::editorial", "article-journal"},
            {"texte::revue::éditorial", "article-journal"},
            {"text::journal::journal article", "article-journal"},
            {"texte::revue::article", "article-journal"},
            {"texte::revue::article de revue", "article-journal"},
            {"texte::revue::article de revue::article de revue scientifique", "article-journal"},
            {"texte::revue::article de revue::article de synthèse", "review"},
            {"texte::revue::article de revue::data paper", "article-journal"},
            {"texte::revue::article de revue::software paper", "article-journal"},
            {"text::journal::journal article::corrigendum", "article-journal"},
            {"texte::revue::article::erratum", "article-journal"},
            {"text::journal::journal article::data paper", "article-journal"},
            {"texte::revue::article::data paper", "article-journal"},
            {"text::journal::journal article::research article", "article-journal"},
            {"texte::revue::article::article scientifique", "article-journal"},
            {"text::journal::journal article::review article", "review"},
            {"texte::revue::article::article de synthèse", "review"},
            {"text::journal::journal article::software paper", "article-journal"},
            {"texte::revue::article::article sur un logiciel", "article-journal"},
            {"text::journal::letter to the editor", "article-journal"},
            {"texte::revue::lettre à l'éditeur", "article-journal"},
            {"text::lecture/talk", "speech"},
            {"texte::cours", "speech"},
            {"text::letter", "personal_communication"},
            {"texte::lettre", "personal_communication"},
            {"text::magazine", "article-magazine"},
            {"texte::magazine", "article-magazine"},
            {"text::manuscript", "manuscript"},
            {"texte::manuscrit", "manuscript"},
            {"text::musical notation", "musical_score"},
            {"texte::partition", "musical_score"},
            {"text::newspaper", "periodical"},
            {"texte::journal", "periodical"},
            {"text::newspaper::newspaper article", "article-newspaper"},
            {"texte::journal::article de journal", "article-newspaper"},
            {"text::other periodical", "periodical"},
            {"texte::autre périodique", "periodical"},
            {"text::preprint", "article"},
            {"texte::preprint", "article"},
            {"text::report", "report"},
            {"texte::rapport", "report"},
            {"text::report::clinical study", "report"},
            {"texte::rapport::étude clinique", "report"},
            {"text::report::data management plan", "report"},
            {"texte::rapport::plan de gestion de données", "report"},
            {"text::report::memorandum", "report"},
            {"texte::rapport::mémo", "report"},
            {"text::report::policy report", "report"},
            {"texte::rapport::rapport stratégique", "report"},
            {"text::report::project deliverable", "report"},
            {"texte::rapport::projet de semestre", "report"},
            {"text::report::research protocol", "report"},
            {"texte::rapport::protocole de recherche", "report"},
            {"text::report::research report", "report"},
            {"texte::rapport::rapport de recherche", "report"},
            {"text::report::technical report", "report"},
            {"texte::rapport::rapport technique", "report"},
            {"text::research proposal", "report"},
            {"texte::projet de recherche", "report"},
            {"text::review", "review"},
            {"texte::synthèse", "review"},
            {"text::review::book review", "review-book"},
            {"texte::synthèse::note de lecture", "review-book"},
            {"text::review::commentary", "review"},
            {"texte::synthèse::commentaire", "review"},
            {"text::review::peer review", "review"},
            {"texte::synthèse::évaluation par les pairs", "review"},
            {"text::technical documentation or standard", "standard"},
            {"texte::manuel technique/documentation technique", "standard"},
            {"text::thesis", "thesis"},
            {"texte::thèse", "thesis"},
            {"text::thesis::bachelor thesis", "thesis"},
            {"texte::thèse::mémoire de stage", "thesis"},
            {"text::thesis::doctoral thesis", "thesis"},
            {"texte::thèse::thèse de doctorat", "thesis"},
            {"text::thesis::master thesis", "thesis"},
            {"texte::thèse::mémoire de master", "thesis"},
            {"text::transcription", "document"},
            {"texte::transcription", "document"},
            {"text::working paper", "article"},
            {"texte::working paper", "article"},
            {"other", "document"},
            {"autre", "document"},
            {"patent", "patent"},
            {"brevet", "patent"},
            {"patent::PCT application", "patent"},
            {"brevet::demande PCT", "patent"},
            {"patent::design patent", "patent"},
            {"brevet::brevet de conception", "patent"},
            {"patent::plant patent", "patent"},
            {"brevet::brevet de plante", "patent"},
            {"patent::plant variety protection", "patent"},
            {"brevet::certificat d'obtention végétale", "patent"},
            {"patent::software patent", "patent"},
            {"brevet::brevet de logiciel", "patent"},
            {"patent::utility model", "patent"},
            {"brevet::modèle d'utilité", "patent"},
            {"cartographic material", "map"},
            {"matériel cartographique", "map"},
            {"cartographic material::map", "map"},
            {"matériel cartographique::carte géographique", "map"},
            {"dataset", "dataset"},
            {"jeu de données", "dataset"},
            {"dataset::aggregated data", "dataset"},
            {"jeu de données::données agrégées", "dataset"},
            {"dataset::clinical trial data", "dataset"},
            {"jeu de données::données d'essai clinique", "dataset"},
            {"dataset::compiled data", "dataset"},
            {"jeu de données::données compilées", "dataset"},
            {"dataset::encoded data", "dataset"},
            {"jeu de données::données encodées", "dataset"},
            {"dataset::experimental data", "dataset"},
            {"jeu de données::données expérimentales", "dataset"},
            {"dataset::genomic data", "dataset"},
            {"jeu de données::données génomiques", "dataset"},
            {"dataset::geospatial data", "dataset"},
            {"jeu de données::données géospatiales", "dataset"},
            {"dataset::laboratory notebook", "dataset"},
            {"jeu de données::carnet de laboratoire", "dataset"},
            {"dataset::measurement and test data", "dataset"},
            {"jeu de données::données de mesure et d'essai", "dataset"},
            {"dataset::observational data", "dataset"},
            {"jeu de données::données d'observation", "dataset"},
            {"dataset::recorded data", "dataset"},
            {"jeu de données::données enregistrées", "dataset"},
            {"dataset::simulation data", "dataset"},
            {"jeu de données::données de simulation", "dataset"},
            {"dataset::survey data", "dataset"},
            {"jeu de données::données d'enquête", "dataset"},
            {"design", "document"},
            {"schéma", "document"},
            {"design::industrial design", "document"},
            {"schéma::schéma industriel", "document"},
            {"design::layout design", "document"},
            {"schéma::schéma de configuration", "document"},
            {"image", "graphic"},
            {"image::moving image", "motion_picture"},
            {"image::image animée", "motion_picture"},
            {"image::moving image::video", "motion_picture"},
            {"image::image animée::vidéo", "motion_picture"},
            {"image::still image", "graphic"},
            {"image::image fixe", "graphic"},
            {"interactive resource", "webpage"},
            {"ressource interactive", "webpage"},
            {"interactive resource::website", "webpage"},
            {"ressource interactive::site web", "webpage"},
            {"Teaching material", "document"},
            {"ressource pédagogique ou d'enseignement", "document"},
            {"software", "software"},
            {"logiciel", "software"},
            {"software::research software", "software"},
            {"logiciel::logiciel de recherche", "software"},
            {"software::source code", "software"},
            {"logiciel::code source", "software"},
            {"sound", "song"},
            {"son", "song"},
            {"sound::musical composition", "song"},
            {"son::composition musicale", "song"},
            {"trademark", "patent"},
            {"marque déposée", "patent"},
            {"workflow", "document"},
            {"thesis", "thesis"},
            {"thèses", "thesis"},
            {"thesis::doctoral thesis", "thesis"},
            {"thèses::thèse de doctorat", "thesis"},
            {"student work", "thesis"},
            {"projet étudiant", "thesis"},
            {"student work::doctoral thesis", "thesis"},
            {"projet étudiant::thèse de doctorat", "thesis"},
            {"student work::bachelor thesis", "thesis"},
            {"projet étudiant::mémoire de bachelor", "thesis"},
            {"student work::master thesis", "thesis"},
            {"projet étudiant::mémoire de master", "thesis"},
            {"student work::semester or other student projects", "report"},
            {"projet étudiant::projet de semestre ou autres projets d'étudiants", "report"},
            {"text::newspaper article", "article-newspaper"},
            {"texte::article de presse", "article-newspaper"},
            {"texte::article de revue spécialisée ou de vulgarisation", "article-magazine"},
            {"texte::documentation technique ou norme", "standard"},
            {"dataset::données agrégées", "dataset"},
            {"dataset::données d'essai clinique", "dataset"},
            {"dataset::données compilées", "dataset"},
            {"dataset::données encodées", "dataset"},
            {"dataset::données expérimentales", "dataset"},
            {"dataset::données génomiques", "dataset"},
            {"dataset::données géospatiales", "dataset"},
            {"dataset::carnet de laboratoire", "dataset"},
            {"dataset::données de mesure et d'essai", "dataset"},
            {"dataset::données d'observation", "dataset"},
            {"dataset::données enregistrées", "dataset"},
            {"dataset::données de simulation", "dataset"},
            {"dataset::données d'enquête", "dataset"},
            {"design::design industriel", "document"},
            {"design::design de configuration", "document"},
            {"image::image animée::video", "motion_picture"},
            {"document sonore", "song"},
            {"texte::Preprint", "article"},
            {"texte::document de travail", "article"},
            {"rapport", "report"},
            {"article", "article-journal"},
            {"présentation de conférence", "speech"},
            {"poster de conférence", "paper-conference"},
            {"article de conférence", "paper-conference"},
            {"texte::conférence::article de conférence", "paper-conference"},
            {"report", "report"},
            {"journal article", "article-journal"},
            {"conference presentation", "speech"},
            {"Conference poster", "paper-conference"},
            {"conference paper", "paper-conference"},
            {"Controlled Vocabulary for Resource Type Genres::other", "document"},
            {"text::conference::conference paper", "paper-conference"},
            {"text::lecture", "speech"},
            {"texte::objet présenté à une conférence::article de conférence hors actes", "paper-conference"},
            {"book part or chapter", "chapter"},
            {"conference paper not in proceedings", "paper-conference"},
            {"master thesis", "thesis"},
            {"preprint", "article"},
            {"research article", "article-journal"},
            {"resource pédagogique ou d'enseignement", "document"},
            {"teaching material", "document"},
            {"working paper", "article"},
            {"mémoire ou projet étudiant", "thesis"},
            {"mémoire ou projet étudiant::mémoire ou projet de bachelor", "thesis"},
            {"mémoire ou projet étudiant::mémoire ou projet de master", "thesis"},
            {"mémoire ou projet étudiant::projet de semestre ou autres projets d'étudiants", "report"},
            {"texte::livre/monographie", "book"},
            {"texte::livre/monographie::chapitre de livre/partie d'ouvrage", "chapter"},
            {"texte::rapport::research protocol", "report"},
        };
        for (String[] entry : entries) {
            map.putIfAbsent(entry[0], entry[1]);
        }
        return map;
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
