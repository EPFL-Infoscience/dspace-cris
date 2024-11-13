/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.crossref;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.el.MethodNotFoundException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.importer.external.datamodel.ImportRecord;
import org.dspace.importer.external.datamodel.Query;
import org.dspace.importer.external.exception.MetadataSourceException;
import org.dspace.importer.external.liveimportclient.service.LiveImportClient;
import org.dspace.importer.external.service.AbstractImportMetadataSourceService;
import org.dspace.importer.external.service.DoiCheck;
import org.dspace.importer.external.service.OrcidCheck;
import org.dspace.importer.external.service.components.QuerySource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implements a data source for querying CrossRef
 * 
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class CrossRefImportMetadataSourceServiceImpl extends AbstractImportMetadataSourceService<String>
        implements QuerySource {

    private final static Logger log = LogManager.getLogger();

    private String url;

    @Autowired
    private LiveImportClient liveImportClient;

    @Override
    public String getImportSource() {
        return "crossref";
    }

    @Override
    public void init() throws Exception {}

    @Override
    public ImportRecord getRecord(String recordId) throws MetadataSourceException {
        String id = getID(recordId);
        List<ImportRecord> records = StringUtils.isNotBlank(id) ? retry(new SearchByIdCallable(id))
                                                                : retry(new SearchByIdCallable(recordId));
        return CollectionUtils.isEmpty(records) ? null : records.get(0);
    }

    @Override
    public int getRecordsCount(String query) throws MetadataSourceException {
        String id = getID(query);
        if (StringUtils.isNotBlank(id)) {
            return retry(new DoiCheckCallable(id));
        } else {
            id = getQuery(query);
            return retry(new CountByQueryCallable(id));
        }
    }

    @Override
    public int getRecordsCount(Query query) throws MetadataSourceException {
        String id = getID(query.toString());
        return StringUtils.isNotBlank(id) ? retry(new DoiCheckCallable(id)) : retry(new CountByQueryCallable(query));
    }

    @Override
    public Collection<ImportRecord> getRecords(String query, int start, int count) throws MetadataSourceException {
        String id = getID(query);
        if (StringUtils.isBlank(id)) {
            id = getQuery(query.toString());
        }
        return StringUtils.isNotBlank(id) ? retry(new SearchByIdCallable(id, count, start))
                                          : retry(new SearchByQueryCallable(query, count, start));
    }

    @Override
    public Collection<ImportRecord> getRecords(Query query) throws MetadataSourceException {
        String id = getID(query.toString());
        if (StringUtils.isNotBlank(id)) {
            id = getQuery(query.toString());
        }
        if (StringUtils.isNotBlank(id)) {
            return retry(new SearchByIdCallable(id));
        }
        return retry(new SearchByQueryCallable(query));
    }

    @Override
    public ImportRecord getRecord(Query query) throws MetadataSourceException {
        String id = getID(query.toString());
        if (StringUtils.isBlank(id)) {
            id = getQuery(query.toString());
        }
        List<ImportRecord> records = retry(new SearchByIdCallable(id));
        return CollectionUtils.isEmpty(records) ? null : records.get(0);
    }

    @Override
    public Collection<ImportRecord> findMatchingRecords(Query query) throws MetadataSourceException {
        String id = getID(query.toString());
        if (StringUtils.isNotBlank(id)) {
            return retry(new SearchByIdCallable(id));
        } else {
            id = getQuery(query.toString());
            return retry(new FindMatchingRecordCallable(query));
        }
    }

    @Override
    public Collection<ImportRecord> findMatchingRecords(Item item) throws MetadataSourceException {
        throw new MethodNotFoundException("This method is not implemented for CrossRef");
    }

    public String getID(String query) {
        // Workaround for encoded slashes.
        if (query.contains("%252F")) {
            query = query.replace("%252F", "/");
        }
        return DoiCheck.isDoi(query) ? query : StringUtils.EMPTY;
    }

    public String getQuery(String query) {
        StringBuilder idBuilder = new StringBuilder();

        query = query.trim();
        String id = query.split("\\s")[0];
        String extraQuery = query.length() > id.length()
            ? query.substring(id.length()).trim()
            : null;

        if (DoiCheck.isDoi(id)) {
            idBuilder.append("filter=doi:").append(id);
        }
        if (OrcidCheck.isOrcid(id)) {
            idBuilder.append("filter=orcid:").append(id);
        }
        if (StringUtils.isNotEmpty(extraQuery)) {
            idBuilder.append("&query=").append(extraQuery);
        }
        return idBuilder.toString();
    }

    /**
     * This class is a Callable implementation to get CrossRef entries based on query object.
     * This Callable use as query value the string queryString passed to constructor.
     * If the object will be construct through Query.class instance, a Query's map entry with key "query" will be used.
     * Pagination is supported too, using the value of the Query's map with keys "start" and "count".
     * 
     * @author Mykhaylo Boychuk (mykhaylo.boychuk@4science.com)
     */
    private class SearchByQueryCallable implements Callable<List<ImportRecord>> {

        private final Query query;

        private SearchByQueryCallable(String queryString, Integer maxResult, Integer start) {
            query = new Query();
            query.addParameter("query", queryString);
            query.addParameter("count", maxResult);
            query.addParameter("start", start);
        }

        private SearchByQueryCallable(Query query) {
            this.query = query;
        }

        @Override
        public List<ImportRecord> call() throws Exception {
            List<ImportRecord> results = new ArrayList<>();
            Integer count = query.getParameterAsClass("count", Integer.class);
            Integer start = query.getParameterAsClass("start", Integer.class);

            URIBuilder uriBuilder = new URIBuilder(url);
            uriBuilder.addParameter("query", query.getParameterAsClass("query", String.class));
            if (Objects.nonNull(count)) {
                uriBuilder.addParameter("rows", count.toString());
            }
            if (Objects.nonNull(start)) {
                uriBuilder.addParameter("offset", start.toString());
            }
            String response = liveImportClient.executeHttpGetRequest(1000, uriBuilder.toString(), new HashMap<>());
            if (StringUtils.isNotEmpty(response)) {
                convertStringJsonToJsonNode(response)
                    .at("/message/items")
                    .forEach(node -> results.add(transformSourceRecords(node.toString())));
            }
            return results;
        }

    }

    /**
     * This class is a Callable implementation to get an CrossRef entry using DOI
     * The DOI to use can be passed through the constructor as a String or as Query's map entry, with the key "id".
     *
     * @author Mykhaylo Boychuk (mykhaylo.boychuk@4science.com)
     */
    private class SearchByIdCallable implements Callable<List<ImportRecord>> {
        private final Query query;

        private SearchByIdCallable(Query query) {
            this.query = query;
        }

        private SearchByIdCallable(String id) {
            this.query = new Query();
            query.addParameter("id", id);
        }

        private SearchByIdCallable(String id, Integer maxResult, Integer start) {
            query = new Query();
            query.addParameter("id", id);
            query.addParameter("count", maxResult);
            query.addParameter("start", start);
        }

        @Override
        public List<ImportRecord> call() throws Exception {
            List<ImportRecord> results = new ArrayList<>();
            String ID = URLDecoder.decode(query.getParameterAsClass("id", String.class), UTF_8);
            String separator = ID.contains("filter=") ? "?" : "/";
            URIBuilder uriBuilder = new URIBuilder(url + separator + ID);

            // if the query is a single doi, we expect a single result and cannot use parameters
            if (!DoiCheck.isDoi(ID)) {
                Optional.ofNullable(query.getParameterAsClass("count", Integer.class))
                        .ifPresent(count -> uriBuilder.addParameter("rows", count.toString()));
                Optional.ofNullable(query.getParameterAsClass("start", Integer.class))
                        .ifPresent(start -> uriBuilder.addParameter("offset", start.toString()));
            }

            String response = liveImportClient.executeHttpGetRequest(1000, uriBuilder.toString(), new HashMap<>());
            if (StringUtils.isNotEmpty(response)) {
                JsonNode tree = convertStringJsonToJsonNode(response);
                // work is for a single result, work-list for multiple results
                if ("work".equals(tree.get("message-type").asText())) {
                    results.add(transformSourceRecords(tree.get("message").toString()));
                } else {
                    tree.at("/message/items")
                        .forEach(node -> results.add(transformSourceRecords(node.toString())));
                }
            }
            return results;
        }
    }

    /**
     * This class is a Callable implementation to search CrossRef entries using author and title.
     * There are two field in the Query map to pass, with keys "title" and "author"
     * (at least one must be used).
     * 
     * @author Mykhaylo Boychuk (mykhaylo.boychuk@4science.com)
     */
    private class FindMatchingRecordCallable implements Callable<List<ImportRecord>> {

        private final Query query;

        private FindMatchingRecordCallable(Query q) {
            query = q;
        }

        @Override
        public List<ImportRecord> call() throws Exception {
            String queryValue = query.getParameterAsClass("query", String.class);
            Integer count = query.getParameterAsClass("count", Integer.class);
            Integer start = query.getParameterAsClass("start", Integer.class);
            String author = query.getParameterAsClass("author", String.class);
            String title = query.getParameterAsClass("title", String.class);
            String bibliographics = query.getParameterAsClass("bibliographics", String.class);
            List<ImportRecord> results = new ArrayList<>();
            URIBuilder uriBuilder = new URIBuilder(url);
            if (Objects.nonNull(queryValue)) {
                uriBuilder.addParameter("query", queryValue);
            }
            if (Objects.nonNull(count)) {
                uriBuilder.addParameter("rows", count.toString());
            }
            if (Objects.nonNull(start)) {
                uriBuilder.addParameter("offset", start.toString());
            }
            if (Objects.nonNull(author)) {
                uriBuilder.addParameter("query.author", author);
            }
            if (Objects.nonNull(title )) {
                uriBuilder.addParameter("query.container-title", title);
            }
            if (Objects.nonNull(bibliographics)) {
                uriBuilder.addParameter("query.bibliographic", bibliographics);
            }
            String resp = liveImportClient.executeHttpGetRequest(1000, uriBuilder.toString(), new HashMap<>());
            if (StringUtils.isNotEmpty(resp)) {
                convertStringJsonToJsonNode(resp)
                    .at("/message/items")
                    .forEach(node -> results.add(transformSourceRecords(node.toString())));
            }
            return results;
        }

    }

    /**
     * This class is a Callable implementation to count the number of entries for an CrossRef query.
     * This Callable use as query value to CrossRef the string queryString passed to constructor.
     * If the object will be construct through Query.class instance, the value of the Query's
     * map with the key "query" will be used.
     * 
     * @author Mykhaylo Boychuk (mykhaylo.boychuk@4science.com)
     */
    private class CountByQueryCallable implements Callable<Integer> {

        private final Query query;

        private CountByQueryCallable(String queryString) {
            query = new Query();
            query.addParameter("query", queryString);
        }

        private CountByQueryCallable(Query query) {
            this.query = query;
        }

        @Override
        public Integer call() throws Exception {
            URIBuilder uriBuilder = new URIBuilder(url);
            uriBuilder.addParameter("query", query.getParameterAsClass("query", String.class));
            String responseString =
                liveImportClient.executeHttpGetRequest(1000, uriBuilder.toString(), new HashMap<>());
            return StringUtils.isNotEmpty(responseString)
                ? convertStringJsonToJsonNode(responseString).at("/message/total-results").asInt()
                : 0;
        }
    }

    /**
     * This class is a Callable implementation to check if exist an CrossRef entry using DOI.
     * The DOI to use can be passed through the constructor as a String or as Query's map entry, with the key "id".
     * return 1 if CrossRef entry exists otherwise 0
     * 
     * @author Mykhaylo Boychuk (mykhaylo.boychuk@4science.com)
     */
    private class DoiCheckCallable implements Callable<Integer> {

        private final Query query;

        private DoiCheckCallable(final String id) {
            final Query query = new Query();
            query.addParameter("id", id);
            this.query = query;
        }

        @Override
        public Integer call() throws Exception {
            String id = query.getParameterAsClass("id", String.class);
            String separator = id.contains("filter=") ? "?" : "/";
            URIBuilder uriBuilder = new URIBuilder(url + separator + id);
            String responseString =
                liveImportClient.executeHttpGetRequest(1000, uriBuilder.toString(), new HashMap<>());
            JsonNode tree = convertStringJsonToJsonNode(responseString);
            // work is for a single result, work-list for multiple results
            if ("work".equals(tree.get("message-type").asText())) {
                return tree.has("message") && tree.get("message").has("indexed") ? 1 : 0;
            } else {
                return tree.at("/message/total-results").asInt();
            }
        }
    }

    private JsonNode convertStringJsonToJsonNode(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (JsonProcessingException e) {
            log.error("Unable to process json response.", e);
        }
        return null;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}