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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

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
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for {@link PublicationLoaderRunnable}.
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science.it)
 *
 */
public class PublicationLoaderRunnableIT extends AbstractIntegrationTestWithDatabase {

    private SolrSuggestionStorageService solrSuggestionStorageService;

    private Collection collection;

    private Item item;

    private Item itemB;

    @Before
    public void setup() {

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
                          .withTitle("test of item")
                          .withOrcidIdentifier("0000-0002-9079-593X")
                          .build();

        itemB = ItemBuilder.createItem(context, collection)
                          .withAuthor("Donald, Smith")
                          .withTitle("test of itemB")
                          .withOrcidIdentifier("0000-0002-9079-593X")
                          .build();

        context.restoreAuthSystemState();
    }

    @Test
    public void testImportSuggestionsOfNotExistingLoader() throws Exception {
        String loader = "foo";

        TestDSpaceRunnableHandler handler = runScriptWithoutResearcherUUID(loader);
        assertThat(handler.getInfoMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        List<String> errorMessages = handler.getErrorMessages();
        assertThat(errorMessages, hasSize(1));
        assertThat(errorMessages.get(0), containsString("IllegalArgumentException: " +
            "Provider for: " + loader + " couldn't be found"));

    }

    @Test
    public void testImportSuggestionsOfInvalidResearcherUUID() throws Exception {
        String loader = "pubmed";

        TestDSpaceRunnableHandler handler = runScriptWithResearcherUUID(loader, "invalid_id");
        assertThat(handler.getInfoMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        List<String> errorMessages = handler.getErrorMessages();
        assertThat(errorMessages, hasSize(1));
        assertThat(errorMessages.get(0), containsString("IllegalArgumentException: Invalid UUID string:"));

    }

    @Test
    public void testImportSuggestionsOfLoader() throws Exception {
        String loader = "pubmed";
        String idPart = "18926410";

        TestDSpaceRunnableHandler handler = runScriptWithoutResearcherUUID(loader);

        assertThat(handler.getInfoMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        List<String> errorMessages = handler.getErrorMessages();
        assertThat(errorMessages, hasSize(0));

        List<Suggestion> suggestions = solrSuggestionStorageService.findAllUnprocessedSuggestionsBySource(context,
            loader,10,0,true);

        assertThat(suggestions.size(), greaterThanOrEqualTo(1));
        assertThat(suggestions.get(0).getSource(), containsString(loader));
        assertThat(suggestions.get(0).getDisplay(), containsString("Transfer of peanut allergy " +
            "from the donor to a lung transplant recipient."));
        solrSuggestionStorageService.flagAllSuggestionAsProcessed(loader, idPart);
    }

    @Test
    public void testImportSuggestionsByResearcherUUIDAndLoader() throws Exception {
        String loader = "pubmed";
        String idPart = "18926410";

        TestDSpaceRunnableHandler handler = runScriptWithResearcherUUID(loader, item.getID().toString());

        assertThat(handler.getInfoMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        List<String> errorMessages = handler.getErrorMessages();
        assertThat(errorMessages, hasSize(0));

        List<Suggestion> suggestions = solrSuggestionStorageService.findAllUnprocessedSuggestionsBySource(context,
            loader,10,0,true);

        assertThat(suggestions.size(), greaterThanOrEqualTo(1));
        assertThat(suggestions.get(0).getID(), containsString(item.getID().toString()));
        assertThat(suggestions.get(0).getSource(), containsString(loader));
        assertThat(suggestions.get(0).getDisplay(), containsString("Transfer of peanut allergy " +
            "from the donor to a lung transplant recipient."));

        solrSuggestionStorageService.flagAllSuggestionAsProcessed(loader, idPart);

        TestDSpaceRunnableHandler handlerB = runScriptWithResearcherUUID(loader, itemB.getID().toString());

        assertThat(handlerB.getInfoMessages(), empty());
        assertThat(handlerB.getWarningMessages(), empty());

        List<String> errorMessagesB = handlerB.getErrorMessages();
        assertThat(errorMessagesB, hasSize(0));

        List<Suggestion> suggestionsB = solrSuggestionStorageService.findAllUnprocessedSuggestionsBySource(context,
            loader,10,0,true);

        assertThat(suggestionsB.size(), greaterThanOrEqualTo(1));
        assertThat(suggestionsB.get(0).getID(), containsString(itemB.getID().toString()));
        assertThat(suggestionsB.get(0).getSource(), containsString(loader));
        assertThat(suggestionsB.get(0).getDisplay(), containsString("Transfer of peanut allergy " +
            "from the donor to a lung transplant recipient."));

        solrSuggestionStorageService.flagAllSuggestionAsProcessed(loader, idPart);
    }

    private TestDSpaceRunnableHandler runScriptWithResearcherUUID(String loader, String researcherId) throws Exception {

        String[] args = new String[] {"import-loader-suggestions" ,
                "-l", loader, "-s", researcherId};

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        return handler;
    }

    private TestDSpaceRunnableHandler runScriptWithoutResearcherUUID(String loader) throws Exception {

        String[] args = new String[] {"import-loader-suggestions" ,
            "-l", loader};

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        return handler;
    }
}
