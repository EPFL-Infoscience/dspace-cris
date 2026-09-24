/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import org.dspace.epfl.client.EpflApiClient.Language;
import org.dspace.epfl.client.model.PersonDTO;
import org.dspace.epfl.client.model.PersonDTO.Accred;

/**
 * Factory class to create a mock {@link EpflApiClient} configured with test data.
 *
 * This avoids depending on the real EPFL API in integration tests.
 */
public class MockEpflApiClientFactory {

    private MockEpflApiClientFactory() {
    }

    /**
     * Creates a fully configured mock of {@link EpflApiClient} with test data.
     */
    public static EpflApiClient createMockApiClient() {
        EpflApiClient mockClient = mock(EpflApiClient.class);

        // Default: unknown scipers return empty
        when(mockClient.getPerson(anyString(), any(Language.class))).thenReturn(Optional.empty());
        when(mockClient.getPersonalPicture(anyString())).thenReturn(Optional.empty());
        when(mockClient.isOrgUnitActive(anyString())).thenReturn(false);

        // Person 352234 - Haitham Al Hassanieh
        configurePerson352234(mockClient);

        // Person 352235 - Test person for conflict scenario
        configurePerson352235(mockClient);

        // Person 196358 - Florence Graezer Bideau
        configurePerson196358(mockClient);

        // Person 375968 - Iris Arianna Dorschel
        configurePerson375968(mockClient);

        // Person 251859 - Louis Vina
        configurePerson251859(mockClient);

        // Configure org units as active
        when(mockClient.isOrgUnitActive(eq("SENS"))).thenReturn(true);
        when(mockClient.isOrgUnitActive(eq("SSC-ENS"))).thenReturn(true);
        when(mockClient.isOrgUnitActive(eq("SIN-ENS"))).thenReturn(true);
        when(mockClient.isOrgUnitActive(eq("UPABLASSER"))).thenReturn(true);
        when(mockClient.isOrgUnitActive(eq("LVG"))).thenReturn(true);
        when(mockClient.isOrgUnitActive(eq("COSEC-STI"))).thenReturn(true);
        when(mockClient.isOrgUnitActive(eq("PTMH-GE"))).thenReturn(true);
        when(mockClient.isOrgUnitActive(eq("SCI-CDH-FGB"))).thenReturn(true);
        when(mockClient.isOrgUnitActive(eq("SHS-ENS"))).thenReturn(true);

        return mockClient;
    }

    private static void configurePerson352234(EpflApiClient mockClient) {
        PersonDTO person = new PersonDTO();
        person.setSciper("352234");
        person.setEmail("haitham.alhassanieh@epfl.ch");
        person.setProfile("haitham.alhassanieh");
        person.setFirstname("Haitham");
        person.setName("Al Hassanieh");
        person.setAccreds(new Accred[] {
            createAccred("SENS", "EPFL SENS", "Associate Professor", 0),
            createAccred("SSC-ENS", "EPFL SSC-ENS", "Associate Professor", 1),
            createAccred("SIN-ENS", "EPFL SIN-ENS", "Associate Professor", 2)
        });

        when(mockClient.getPerson(eq("352234"), any(Language.class))).thenReturn(Optional.of(person));
        when(mockClient.getPersonalPicture(eq("haitham.alhassanieh")))
            .thenAnswer(invocation -> Optional.of(createFakeImageStream()));
    }

    private static void configurePerson352235(EpflApiClient mockClient) {
        PersonDTO person = new PersonDTO();
        person.setSciper("352235");
        person.setEmail("test.person@epfl.ch");
        person.setProfile("test.person");
        person.setFirstname("Test");
        person.setName("Person");
        person.setAccreds(new Accred[] {
            createAccred("SENS", "EPFL SENS", "Researcher", 0)
        });

        when(mockClient.getPerson(eq("352235"), any(Language.class))).thenReturn(Optional.of(person));
        when(mockClient.getPersonalPicture(eq("test.person")))
            .thenAnswer(invocation -> Optional.of(createFakeImageStream()));
    }

    private static void configurePerson196358(EpflApiClient mockClient) {
        PersonDTO person = new PersonDTO();
        person.setSciper("196358");
        person.setEmail("florence.graezerbideau@epfl.ch");
        person.setProfile("florence.graezerbideau");
        person.setFirstname("Florence");
        person.setName("Graezer Bideau");
        person.setAccreds(new Accred[] {
            createAccred("SENS", "EPFL SENS", "MER", 0),
            createAccred("SCI-CDH-FGB", "EPFL SCI-CDH-FGB", "MER", 1),
            createAccred("SHS-ENS", "EPFL SHS-ENS", "MER", 2)
        });

        when(mockClient.getPerson(eq("196358"), any(Language.class))).thenReturn(Optional.of(person));
        when(mockClient.getPersonalPicture(eq("florence.graezerbideau")))
            .thenAnswer(invocation -> Optional.of(createFakeImageStream()));
    }

    private static void configurePerson375968(EpflApiClient mockClient) {
        PersonDTO person = new PersonDTO();
        person.setSciper("375968");
        person.setEmail("arianna.dorschel@epfl.ch");
        person.setProfile("arianna.dorschel");
        person.setFirstname("Iris Arianna");
        person.setName("Dorschel");
        // No rank 0 (no main affiliation)
        person.setAccreds(new Accred[] {
            createAccred("UPABLASSER", "EPFL UPABLASSER", "Doctoral Assistant", 1),
            createAccred("LVG", "EPFL LVG", "Doctoral Assistant", 2)
        });

        when(mockClient.getPerson(eq("375968"), any(Language.class))).thenReturn(Optional.of(person));
        when(mockClient.getPersonalPicture(eq("arianna.dorschel")))
            .thenAnswer(invocation -> Optional.of(createFakeImageStream()));
    }

    private static void configurePerson251859(EpflApiClient mockClient) {
        PersonDTO person = new PersonDTO();
        person.setSciper("251859");
        person.setEmail("louis.vina@epfl.ch");
        person.setProfile("louis.vina");
        person.setFirstname("Louis");
        person.setName("Vina");
        person.setAccreds(new Accred[] {
            createAccred("COSEC-STI", "EPFL COSEC-STI", "Safety Delegate", 0),
            createAccred("PTMH-GE", "EPFL PTMH-GE", "Scientific Collaborator", 1)
        });

        when(mockClient.getPerson(eq("251859"), any(Language.class))).thenReturn(Optional.of(person));
        when(mockClient.getPersonalPicture(eq("louis.vina")))
            .thenAnswer(invocation -> Optional.of(createFakeImageStream()));
    }

    private static Accred createAccred(String acronym, String path, String position, int rank) {
        Accred accred = new Accred();
        accred.setAcronym(acronym);
        accred.setPath(path);
        accred.setName(acronym);
        accred.setPosition(position);
        accred.setRank(rank);
        return accred;
    }

    private static InputStream createFakeImageStream() {
        // A minimal valid JPEG header (SOI marker) followed by dummy data
        byte[] fakeJpeg = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
        };
        return new ByteArrayInputStream(fakeJpeg);
    }
}
