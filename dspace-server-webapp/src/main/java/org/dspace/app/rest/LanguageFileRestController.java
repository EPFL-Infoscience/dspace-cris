/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.TemplateVariable;
import org.springframework.hateoas.TemplateVariable.VariableType;
import org.springframework.hateoas.TemplateVariables;
import org.springframework.hateoas.UriTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * This controller has role to manage language files.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
@RestController
@RequestMapping("/api/" + LanguageFileRestController.CATEGORY)
public class LanguageFileRestController {

    public static final String ACTION = "languages";
    public static final String CATEGORY = "adminfile";
    public static final String PARAM = "lang";
    public static final String FILE_EXT = ".json";

    @Autowired
    private ConfigurationService configurationService;
    @Autowired
    private DiscoverableEndpointsService discoverableEndpointsService;

    @PostConstruct
    public void afterPropertiesSet() {
        discoverableEndpointsService.register(this,
                    Arrays.asList(Link.of(UriTemplate.of(
                        "/api/" + CATEGORY + "/" + ACTION,
                        new TemplateVariables(new TemplateVariable(PARAM, VariableType.REQUEST_PARAM))
                    ), CATEGORY)));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = RequestMethod.POST, value = ACTION)
    public void upload(HttpServletResponse response, HttpServletRequest request, MultipartFile file,
            @RequestParam(PARAM) String lang)
           throws IllegalStateException, IOException {
        if (Objects.isNull(file) || file.isEmpty()) {
            throw new UnprocessableEntityException("The language file must be provided!");
        }
        String pathWhereToSave = configurationService.getProperty("languages.file.dir");
        File languageFile = new File(pathWhereToSave, lang + FILE_EXT);
        if (!languageFile.exists()) {
            new File(pathWhereToSave).mkdirs();
        }
        convertToJson(file, languageFile);
        file.transferTo(new File(pathWhereToSave, lang + FILE_EXT + "5"));
    }

    private void convertToJson(MultipartFile file, File languageFile) throws IOException {
        JsonReader jsonReader = new JsonReader(new InputStreamReader(file.getInputStream()));
        jsonReader.setLenient(true);
        Gson gson = new Gson();
        String json = gson.toJson(gson.<Object>fromJson(jsonReader, Object.class));
        try (FileWriter fw = new FileWriter(languageFile)) {
            fw.write(json);
        }
    }

}