/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.authority;

import static org.dspace.authority.service.AuthorityValueService.GENERATE;
import static org.dspace.authority.service.AuthorityValueService.SPLIT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dspace.content.authority.factory.ItemAuthorityServiceFactory;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.epfl.client.model.PersonDTO.Accred;
import org.dspace.epfl.service.PersonApiService;
import org.dspace.epfl.service.impl.PersonApiServiceImpl;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

public class PersonAuthority extends ItemAuthority {

    private static final String AUTHOR_AFFILIATION = "oairecerif_author_affiliation";

    private static final String DATA_AUTHOR_AFFILIATION = "data-oairecerif_author_affiliation";

    private static final String AUTHOR_ORGUNIT = "oairecerif_affiliation_orgunit";

    private static final String DATA_AUTHOR_ORGUNIT = "data-oairecerif_affiliation_orgunit";

    private static final String EDITOR_AFFILIATION = "oairecerif_editor_affiliation";

    private static final String DATA_EDITOR_AFFILIATION = "data-oairecerif_editor_affiliation";

    private final PersonApiService personApiService = dspace.getSingletonService(PersonApiServiceImpl.class);
    private final ItemAuthorityServiceFactory itemAuthorityServiceFactory = dspace.getServiceManager()
            .getServiceByName("itemAuthorityServiceFactory", ItemAuthorityServiceFactory.class);
    private final ConfigurationService configurationService =
        DSpaceServicesFactory.getInstance().getConfigurationService();

    private String authorityName;

    @Override
    public Choices getMatches(String text, int start, int limit, String locale) {
        super.setPluginInstanceName(authorityName);
        Choices solrChoices = super.getMatches(text, start, limit, locale);

        return solrChoices.values.length == 0 ? getEpflApiMatches(text, start, limit) : solrChoices;
    }

    private Choices getEpflApiMatches(String text, int start, int limit) {
        Choice[] epflApiChoices =
            getChoiceFromEpflQueryResults(personApiService.getPersons(text)).toArray(new Choice[0]);

        int confidenceValue = itemAuthorityServiceFactory.getInstance(authorityName)
                                                         .getConfidenceForChoices(epflApiChoices);

        return new Choices(epflApiChoices, start, epflApiChoices.length, confidenceValue,
                           epflApiChoices.length > (start + limit), 0);
    }

    private List<Choice> getChoiceFromEpflQueryResults(List<PersonDTO> persons) {
        return persons
                .stream()
                .map(person -> {
                    Map<String, String> extras = buildPersonAffiliationExtras(
                        person.getMainAffiliation().orElse(new PersonDTO.Accred())
                    );
                    return new Choice(composeAuthorityValue(person.getSciper()), person.getFullName(),
                                      person.getFullName(), extras);
                })
                .collect(Collectors.toList());
    }

    private Map<String, String> buildPersonAffiliationExtras(Accred accred) {
        Map<String, String> extras = new HashMap<>();

        if (authorityName.equals("AuthorAuthority")) {
            buildAuthorExtras(extras, accred);
        }

        if (authorityName.equals("EditorAuthority")) {
            buildEditorExtras(extras, accred);
        }

        return extras;
    }

    private void buildAuthorExtras(Map<String, String> extras, Accred accred) {
        extras.put(DATA_AUTHOR_ORGUNIT, composePersonAffiliationValue(accred));
        extras.put(AUTHOR_ORGUNIT, accred.getName());

        extras.put(DATA_AUTHOR_AFFILIATION, "EPFL" + "::" + configurationService.getProperty("epfl.head-orgunit.uuid"));
        extras.put(AUTHOR_AFFILIATION, "EPFL");
    }

    private void buildEditorExtras(Map<String, String> extras, Accred accred) {
        extras.put(DATA_EDITOR_AFFILIATION, composePersonAffiliationValue(accred));
        extras.put(EDITOR_AFFILIATION, accred.getName());
    }

    private String composeAuthorityValue(String sciper) {
        String prefix = configurationService.getProperty("epfl.authority.prefix",
                GENERATE + "SCIPER-ID" + SPLIT);
        return prefix.endsWith(SPLIT) ? prefix + sciper : prefix + SPLIT + sciper;
    }

    private String composePersonAffiliationValue(Accred accred) {
        String prefix = accred.getName() + configurationService.getProperty("epfl.acronym.prefix",
                SPLIT + GENERATE + "ACRONYM" + SPLIT);
        return prefix.endsWith(SPLIT) ? prefix + accred.getAcronym() : prefix + SPLIT + accred.getAcronym();
    }

    @Override
    public String getLinkedEntityType() {
        return configurationService.getProperty("researcher-profile.type", "Person");
    }

    @Override
    public void setPluginInstanceName(String name) {
        authorityName = name;
    }

    @Override
    public String getPluginInstanceName() {
        return authorityName;
    }

}
