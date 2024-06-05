/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.authority;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.dspace.AbstractDSpaceTest;
import org.dspace.epfl.service.PersonApiService;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Test class for {@link PersonAuthority}
 * 
 * @author Daniele Ninfo (daniele.ninfo at 4science.com)
 *
 */
public class PersonAuthorityTest extends AbstractDSpaceTest {

    @Test
    public void testNoResponseFromEpflApiOnGetMatches() {

        // Instantiate a PersonAuthority as AuthorAuthority
        PersonAuthority personAuthority = new PersonAuthority();
        personAuthority.setPluginInstanceName("AuthorAuthority");

        // Any problem coming from the invocation of personApiService will result in a
        // runtime exception, so we force this exception mocking the service
        PersonApiService personApiServiceMock = mock(PersonApiService.class);
        when(personApiServiceMock.getPersons(Mockito.anyString()))
                .thenThrow(new RuntimeException("Generic runtime exception"));
        personAuthority.setPersonApiService(personApiServiceMock);

        // Search with a random string to avoid having results from Solr
        Choices matches = personAuthority.getMatches(UUID.randomUUID().toString(), 0, 20, "en");
        // We expect to have zero results instead of an exception
        assertTrue(matches != null);
        assertTrue(matches.total == 0);
        assertTrue(matches.confidence == Choices.CF_FAILED);

    }

}
