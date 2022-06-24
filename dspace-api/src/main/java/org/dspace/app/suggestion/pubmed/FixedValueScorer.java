/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.suggestion.pubmed;

import org.dspace.app.suggestion.SuggestionEvidence;
import org.dspace.app.suggestion.oaire.EvidenceScorer;
import org.dspace.content.Item;
import org.dspace.external.model.ExternalDataObject;

/**
 * Implementation of {@see org.dspace.app.suggestion.oaire.EvidenceScorer} which evaluate ImportRecords
 * this scorer returns a fixed value
 * @author Mohamed Eskander (mohamed.eskander at 4science dot it)
 *
 */
public class FixedValueScorer implements EvidenceScorer {

    /**
     * Method which is responsible to returning a SuggestionEvidence with a score of 100.
     * 
     * @param importRecord the import record to check
     * @param researcher DSpace item
     * @return the generated evidence or null if the record must be discarded
     */
    @Override
    public SuggestionEvidence computeEvidence(Item researcher, ExternalDataObject importRecord) {
        return new SuggestionEvidence(this.getClass().getSimpleName(), 100,
                "static returning a SuggestionEvidence with a score of 100");
    }

}