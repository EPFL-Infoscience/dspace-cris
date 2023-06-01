/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.suggestion.pubmed;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
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
        List<String> author = new LinkedList<>();
        for (String name : getNames()) {
            String value = itemService.getMetadata(researcher, name);
            if (value != null) {
                author.add(createAuthorValue(value, name));
            }
        }

        if (author.size() > 0) {
            return Collections.singletonList(StringUtils.join(author, " OR "));
        }

        return Collections.emptyList();
    }

    private String createAuthorValue(String value, String name) {
        String author = "";
        if ("person.identifier.orcid".equals(name)) {
            author = "(" + value + "[Author - Identifier])";
        } else {
            author = "(" + value + "[Author])";
        }

        return author;
    }

}
