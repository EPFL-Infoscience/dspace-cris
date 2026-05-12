/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.rdf.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.web.JenaHttpNotFoundException;
import org.apache.logging.log4j.Logger;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Basic implementation of the rdf storage impl suitable to generate a local
 * .ttl file of the requested content to be loaded in fuseki using external
 * tools (as the tbd2.tbdloader provided by jena). Delete/update is not
 * supported the file is cleaned/generated at each execution
 *
 * @author Andrea Bollini (andrea.bollini at 4science.com)
 */
public class RDFFileStorageImpl extends RDFStorageImpl {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(RDFFileStorageImpl.class);

    private OutputStream out;

    private ConfigurationService configurationService;

    @Autowired(required = true)
    public RDFFileStorageImpl(ConfigurationService configurationService) {
        this.configurationService = configurationService;
        String fileStorage = configurationService.getProperty("rdf.filestorage.location");
        try {
            File file = new File(fileStorage);
            Path path = Paths.get(file.getParent());
            Files.createDirectories(path);
            out = new FileOutputStream(file, true);
        } catch (IOException e) {
            log.error("Invalid file storage location", e);
        }
    }

    @Override
    public void store(String uri, Model model) {
        try {
            RDFDataMgr.write(out, model, Lang.TTL);
            out.flush();
        } catch (IOException e) {
            log.error("Fail to store the model ", e);
        }
    }

    /**
     * Load the model for the given URI from the SPARQL endpoint defined in the configuration.
     * The model is loaded by executing a CONSTRUCT query to the SPARQL endpoint.
     * The query will look for triples with the given URI as subject in the default graph and in all named graphs.
     * If credentials are defined in the configuration, they will be used to authenticate to the endpoint.
     *
     * @param uri Identifier for this DSO
     * @return Model containing all the triples with the given URI as subject, or null if no such triples are found.
     */
    @Override
    public Model load(String uri) {
        String queryString = "DESCRIBE <" + uri.replace(">", "\\>") + ">";
        try (QueryExecution qexec = executeSparqlQuery(queryString)) {
            return qexec.execDescribe();
        } catch (JenaHttpNotFoundException nf) {
            log.error("Model not found for the uri {}", uri, nf);
            return null;
        }
    }

    /**
     * Used as destroy method in the spring bean configuration
     * @throws Throwable
     */
    public void destroy() throws Throwable {
        closeStream();
    }

    @Override
    public void delete(String uri) {
        log.error("RDFFileStorageImpl#delete not implemented");
        throw new RuntimeException("RDFFileStorageImpl#delete not implemented");
    }

    @Override
    public void deleteAll() {
        try {
            closeStream();

            String fileStorage = configurationService.getProperty("rdf.filestorage.location");
            File file = new File(fileStorage);
            if (file.exists()) {
                file.delete();
            }
            Path path = Paths.get(file.getParent());
            Files.createDirectories(path);
            file = new File(fileStorage);
            out = new FileOutputStream(file);
        } catch (IOException e) {
            log.error("Invalid file storage location", e);
        }
    }

    private void closeStream() throws IOException {
        out.flush();
        out.close();
    }

    @Override
    public List<String> getAllStoredGraphs() {
        log.error("RDFFileStorageImpl#getAllStoredGraphs not implemented");
        throw new RuntimeException("RDFFileStorageImpl#getAllStoredGraphs not implemented");
    }

}
