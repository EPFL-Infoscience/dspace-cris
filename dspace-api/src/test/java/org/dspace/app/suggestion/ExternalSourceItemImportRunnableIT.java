/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.suggestion;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.LinkedList;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.Before;
import org.junit.Test;


/**
 * Integration tests for {@link ExternalSourceItemImportRunnable}.
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 *
 */
public class ExternalSourceItemImportRunnableIT extends AbstractIntegrationTestWithDatabase {

    private ConfigurationService configurationService;

    private SolrSuggestionStorageService solrSuggestionStorageService;

    private Collection collection;

    private Item item;

    @Before
    public void setup() {

        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        solrSuggestionStorageService = ContentServiceFactory.getInstance().getSolrSuggestionStorageService();

        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent community")
                                          .build();

        collection = CollectionBuilder.createCollection(context, parentCommunity)
                                      .withName("Persons")
                                      .withEntityType("Person")
                                      .build();

        item = ItemBuilder.createItem(context, collection)
                          .withAuthor("Donald, Smith")
                          .build();

        context.restoreAuthSystemState();

        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    }

    @Test
    public void testImportItemsFromExternalSourceIfNotExistingSource() throws Exception {
        String source = "foo";
        createSuggestion(source, "35444744");

        TestDSpaceRunnableHandler handler = runImportItemsFromExternalSource(source,
                "100", collection.getID().toString());
        assertThat(handler.getInfoMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        List<String> errorMessages = handler.getErrorMessages();
        assertThat(errorMessages, hasSize(1));
        assertThat(errorMessages.get(0), containsString("IllegalArgumentException: " +
                "Provider for: " + source + " couldn't be found"));
    }

    @Test
    public void testImportItemsFromExternalSourceForInvalidCollectionId() throws Exception {
        String invalidId = "invalid_id";
        createSuggestion("pubmed", "35444744");

        TestDSpaceRunnableHandler handler = runImportItemsFromExternalSource("pubmed",
                "100", invalidId);
        assertThat(handler.getInfoMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        List<String> errorMessages = handler.getErrorMessages();
        assertThat(errorMessages, hasSize(1));
        assertThat(errorMessages.get(0), containsString("IllegalArgumentException: Invalid UUID string:"));
    }

    @Test
    public void testImportItemsIfAllScoresLessThanInput() throws Exception {
        String source = "pubmed";
        Suggestion suggestion = createSuggestion(source, "35444744");

        TestDSpaceRunnableHandler handler = runImportItemsFromExternalSource(source,
            "9999", collection.getID().toString());
        assertThat(handler.getInfoMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
        assertThat(solrSuggestionStorageService.exist(suggestion) , is(false));

        List<String> errorMessages = handler.getErrorMessages();
        assertThat(errorMessages, hasSize(0));
    }

    @Test
    public void testImportItemsFromExternalSource() throws Exception {
        String source = "pubmed";
        Suggestion suggestion = createSuggestion(source, "35444744");

        TestDSpaceRunnableHandler handler = runImportItemsFromExternalSource(source,
                "100", collection.getID().toString());
        assertThat(handler.getInfoMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());
        assertThat(solrSuggestionStorageService.exist(suggestion) , is(true));

        List<String> errorMessages = handler.getErrorMessages();
        assertThat(errorMessages, hasSize(0));
    }

    private Suggestion createSuggestion(String source, String idPart) throws Exception {

        Suggestion suggestion = new Suggestion(source, item , idPart);
        suggestion.setExternalSourceUri(expectedExternalSourceUri(source, idPart));

        List<SuggestionEvidence> evidences = new LinkedList<>();
        evidences.add(new SuggestionEvidence("AuthorNamesScorer", 100,
                "The author Rossi, Antonio at position 1 in the authors " +
                        "list matches the name Rossi, Antonio in the researcher profile"));

        suggestion.getEvidences().addAll(evidences);

        solrSuggestionStorageService.addSuggestion(suggestion, false, true);

        return suggestion;
    }

    private TestDSpaceRunnableHandler runImportItemsFromExternalSource(String source, String score,
          String targetId) throws Exception {

        String[] args = new String[] {"import-external-source-item" ,
                "-p", source, "-s", score, "-u", targetId};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        return handler;
    }

    private String expectedExternalSourceUri(String sourceIdentifier, String recordId) {
        String serverUrl = configurationService.getProperty("dspace.server.url");
        return serverUrl + "/api/integration/externalsources/" + sourceIdentifier + "/entryValues/" + recordId;
    }

}
