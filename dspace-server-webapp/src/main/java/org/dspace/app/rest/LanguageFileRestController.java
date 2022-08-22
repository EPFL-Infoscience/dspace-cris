/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
@RestController
@RequestMapping("/api/" + LanguageFileRestController.CATEGORY)
public class LanguageFileRestController {

    public static final String CATEGORY = "adminfile";

    public static final String ACTION = "languages";

    @Autowired
    private ConfigurationService configurationService;

    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = RequestMethod.POST, value = ACTION)
    public void upload(HttpServletResponse response, HttpServletRequest request, MultipartFile file)
           throws IllegalStateException, IOException {
        if (Objects.isNull(file) || file.isEmpty()) {
            throw new UnprocessableEntityException("");
        }
        String pathWhereToSave = configurationService.getProperty("languages.file.dir");
        file.transferTo(new File(pathWhereToSave + file.getName()));
    }

}