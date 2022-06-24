/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.suggestion.pubmed;

import java.util.ArrayList;
import java.util.List;

import org.dspace.app.suggestion.oaire.OAIREPublicationLoader;
import org.dspace.content.Item;

/**
 * Class responsible to load and manage ImportRecords from Pubmed
 *
 * @author Mohamed Eskander (mohamed.eskander at 4science dot it)
 *
 */
public class PubmedPublicationLoader extends OAIREPublicationLoader {

    @Override
    public List<String> searchMetadataValues(Item researcher) {
        List<String> authors = new ArrayList<String>();
        String author = "";
        for (String name : getNames()) {
            String value = itemService.getMetadata(researcher, name);
            if (value != null) {
                author += createAuthorValue(value , name);
            }
        }

        if (!author.isEmpty()) {
            authors.add(author.substring(0 , (author.length() - 4)));
        }

        return authors;
    }

    private String createAuthorValue(String value, String name) {
        String author = "";
        if (name.equals("dc.identifier.orcid")) {
            author = "(" + value + "[Author - Identifier]) OR ";
        } else {
            author = "(" + value + "[Author]) OR ";
        }

        return author;
    }

}
