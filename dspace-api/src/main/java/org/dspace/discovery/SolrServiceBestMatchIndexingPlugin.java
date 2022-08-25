/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import static org.dspace.content.Item.ANY;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.iterators.PermutationIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of {@link SolrServiceIndexPlugin} that creates an index for
 * the best match.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 *
 */
public class SolrServiceBestMatchIndexingPlugin implements SolrServiceIndexPlugin {

    public static final String BEST_MATCH_INDEX = "bestmatch_s";

    private static final String FIRSTNAME_FIELD = "person.givenName";

    private static final String LASTNAME_FIELD = "person.familyName";

    private static final List<String> FULLNAME_FIELDS = List.of("dc.title", "crisrp.name", "crisrp.name.variant");

    @Autowired
    private ItemService itemService;

    @Override
    @SuppressWarnings("rawtypes")
    public void additionalIndex(Context context, IndexableObject dso, SolrInputDocument document) {

        if (!(dso.getIndexedObject() instanceof Item)) {
            return;
        }

        Item item = (Item) dso.getIndexedObject();

        if (isPersonItem(item)) {
            addIndexValueForPersonItem(item, document);
        } else {
            addIndexValueForGenericItem(item, document);
        }

    }

    private void addIndexValueForPersonItem(Item item, SolrInputDocument document) {
        addIndexValueForPersonGivenAndFamilyName(item, document);
        addIndexValueForPersonFullName(item, document);
    }

    private void addIndexValueForPersonGivenAndFamilyName(Item item, SolrInputDocument document) {

        String firstName = getMetadataValue(item, FIRSTNAME_FIELD);
        String lastName = getMetadataValue(item, LASTNAME_FIELD);
        if (StringUtils.isAnyBlank(firstName, lastName)) {
            return;
        }

        addIndexValue(document, firstName + " " + lastName);
        addIndexValue(document, lastName + " " + firstName);

        String[] firstNames = firstName.split(" ");
        if (firstNames.length > 1) {
            addIndexValueForPersonGivenNames(document, lastName, firstNames);
        } else {
            String firstNameFirstCharacter = truncateFirstname(firstName);
            addIndexValue(document, firstNameFirstCharacter + " " + lastName);
            addIndexValue(document, lastName + " " + firstNameFirstCharacter);
        }

    }

    /**
     * This method add many values to the bestmatch index for each couple of first
     * names. Example: Claudio Paolo Giovanni --> Claudio, Paolo, Giovanni, Claudio
     * Paolo, Claudio Giovanni, Paolo Giovanni
     */
    private void addIndexValueForPersonGivenNames(SolrInputDocument document, String lastName, String[] firstNames) {
        for (int i = 0; i < firstNames.length; i++) {
            addIndexValue(document, firstNames[i] + " " + lastName);
            addIndexValue(document, lastName + " " + firstNames[i]);
            for (int j = i + 1; j < firstNames.length; j++) {
                addIndexValue(document, firstNames[i] + " " + firstNames[j] + " " + lastName);
                addIndexValue(document, lastName + " " + firstNames[i] + " " + firstNames[j]);
            }
        }
    }

    private void addIndexValueForPersonFullName(Item item, SolrInputDocument document) {
        getPersonFullNames(item)
            .flatMap(name -> getAllNamePermutations(name).stream())
            .distinct()
            .forEach(name -> addIndexValue(document, name));
    }

    private Stream<String> getPersonFullNames(Item item) {
        return getMetadataValues(item, FULLNAME_FIELDS).stream()
            .filter(Objects::nonNull)
            .map(name -> removeComma(name))
            .distinct();
    }

    private List<String> getAllNamePermutations(String name) {

        List<String> namePermutations = new ArrayList<String>();

        PermutationIterator<String> permutationIterator = new PermutationIterator<String>(List.of(name.split(" ")));

        while (permutationIterator.hasNext()) {
            namePermutations.add(String.join(" ", permutationIterator.next()));
        }

        return namePermutations;
    }

    private String removeComma(String name) {
        return StringUtils.normalizeSpace(name.replaceAll(",", " "));
    }

    private void addIndexValueForGenericItem(Item item, SolrInputDocument document) {
        addIndexValue(document, itemService.getMetadataFirstValue(item, "dc", "title", null, ANY));
    }

    private void addIndexValue(SolrInputDocument document, String value) {
        document.addField(BEST_MATCH_INDEX, value);
    }

    private List<String> getMetadataValues(Item item, List<String> metadataFields) {
        return metadataFields.stream()
            .flatMap(metadataField -> getMetadataValues(item, metadataField).stream())
            .collect(Collectors.toList());
    }

    private List<String> getMetadataValues(Item item, String metadataField) {
        return itemService.getMetadataByMetadataString(item, metadataField).stream()
            .map(MetadataValue::getValue)
            .collect(Collectors.toList());
    }

    private String getMetadataValue(Item item, String metadataField) {
        return itemService.getMetadataFirstValue(item, new MetadataFieldName(metadataField), ANY);
    }

    private String truncateFirstname(String name) {
        return StringUtils.substring(name, 0, 1) + ".";
    }

    private boolean isPersonItem(Item item) {
        return "Person".equals(itemService.getEntityType(item));
    }

}
