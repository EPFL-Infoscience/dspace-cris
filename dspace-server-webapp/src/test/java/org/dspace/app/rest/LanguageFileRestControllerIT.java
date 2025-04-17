/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.services.ConfigurationService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Integration test for the identifier resolver
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class LanguageFileRestControllerIT extends AbstractControllerIntegrationTest {

    @Autowired
    private ConfigurationService configurationService;

    @Test
    public void uploadLanguageFileUnauthorizedTest() throws Exception {
        context.turnOffAuthorisationSystem();
        InputStream pdf = getClass().getResourceAsStream("simple-article.pdf");
        final MockMultipartFile pdfFile = new MockMultipartFile("file", "/local/path/simple-article.pdf",
                "application/pdf", pdf);

        context.restoreAuthSystemState();
        getClient().perform(multipart("/api/adminfile/languages")
                   .file(pdfFile)
                   .param("lang", "en"))
                   .andExpect(status().isUnauthorized());
    }

    @Test
    public void uploadLanguageFileForbiddenTest() throws Exception {
        context.turnOffAuthorisationSystem();
        InputStream pdf = getClass().getResourceAsStream("simple-article.pdf");
        final MockMultipartFile pdfFile = new MockMultipartFile("file", "/local/path/simple-article.pdf",
                "application/pdf", pdf);

        context.restoreAuthSystemState();

        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(multipart("/api/adminfile/languages")
                               .file(pdfFile)
                               .param("lang", "en"))
                               .andExpect(status().isForbidden());
    }

    @Test
    public void uploadLanguageFileUnprocessableEntityTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(multipart("/api/adminfile/languages")
                             .param("lang", "en"))
                             .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void uploadLanguageFileTest() throws Exception {
        context.turnOffAuthorisationSystem();
        String pathWhereToSave = configurationService.getProperty("languages.file.dir");
        InputStream json5 = getClass().getResourceAsStream("test-lang.json5");
        final MockMultipartFile pdfFile = new MockMultipartFile("file", "/local/path/test-lang.json5",
                "text/json", json5);

        Path path = Paths.get(pathWhereToSave + "en.json");
        Path pathJson5 = Paths.get(pathWhereToSave + "en.json5");
        // delet file
        Files.deleteIfExists(path);
        Files.deleteIfExists(pathJson5);
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(multipart("/api/adminfile/languages")
                             .file(pdfFile)
                             .param("lang", "en"))
                             .andExpect(status().isOk());

        assertTrue(Files.exists(path));
        assertTrue(Files.exists(pathJson5));

        // delet file
        Files.deleteIfExists(path);
        Files.deleteIfExists(pathJson5);
    }

    @Test
    public void uploadInvalidLanguageFileTest() throws Exception {
        context.turnOffAuthorisationSystem();
        String pathWhereToSave = configurationService.getProperty("languages.file.dir");
        InputStream pdf = getClass().getResourceAsStream("simple-article.pdf");
        final MockMultipartFile pdfFile = new MockMultipartFile("file", "/local/path/simple-article.pdf",
                "application/pdf", pdf);

        Path path = Paths.get(pathWhereToSave + "en.json");
        Path pathJson5 = Paths.get(pathWhereToSave + "en.json5");
        // delet file
        Files.deleteIfExists(path);
        Files.deleteIfExists(pathJson5);
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(multipart("/api/adminfile/languages")
                             .file(pdfFile)
                             .param("lang", "en"))
                             .andExpect(status().isInternalServerError());

        assertFalse(Files.exists(path));
        assertFalse(Files.exists(pathJson5));

    }

}