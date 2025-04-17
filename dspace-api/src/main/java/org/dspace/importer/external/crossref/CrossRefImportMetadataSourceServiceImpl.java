/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.crossref;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
        final String id = getSelector(recordId);
        List<ImportRecord> records = retry(new SearchByIdCallable(id));
        return CollectionUtils.isEmpty(records) ? null : records.get(0);
    }

    @Override
    public int getRecordsCount(String query) throws MetadataSourceException {
        final String selector = getSelector(query);
        return retry(new DoiCheckCallable(selector));
    }

    @Override
    public int getRecordsCount(Query query) throws MetadataSourceException {
        final String id = getSelector(query.toString());
        return StringUtils.isNotBlank(id) ? retry(new DoiCheckCallable(id)) : retry(new CountByQueryCallable(query));
    }

    @Override
    public Collection<ImportRecord> getRecords(String query, int start, int count) throws MetadataSourceException {
        final String selector = getSelector(query);
        return retry(new SearchByIdCallable(selector, count, start));
    }

    @Override
    public Collection<ImportRecord> getRecords(Query query) throws MetadataSourceException {
        final String id = getSelector(query.toString());
        if (StringUtils.isNotBlank(id)) {
            return retry(new SearchByIdCallable(id));
        } else {
            return retry(new SearchByQueryCallable(query));
        }
    }

    @Override
    public ImportRecord getRecord(Query query) throws MetadataSourceException {
        List<ImportRecord> records;
        final String id = getSelector(query.toString());
        if (StringUtils.isNotBlank(id)) {
            records = retry(new SearchByIdCallable(id));
        } else {
            records = retry(new SearchByQueryCallable(query));
        }
        return CollectionUtils.isEmpty(records) ? null : records.get(0);
    }

    @Override
    public Collection<ImportRecord> findMatchingRecords(Query query) throws MetadataSourceException {
        final String id = getSelector(query.toString());
        if (StringUtils.isNotBlank(id)) {
            return retry(new SearchByIdCallable(id));
        } else {
            return retry(new FindMatchingRecordCallable(query));
        }
    }

    @Override
    public Collection<ImportRecord> findMatchingRecords(Item item) throws MetadataSourceException {
        throw new MethodNotFoundException("This method is not implemented for CrossRef");
    }

    /**
     * This method builds the second part of the url to append to the crossref host.
     * According to the crossref specifications, it can be a path for a single
     * resource (like a doi) or a query for multiple resources
     * @return
     */
    public String getSelector(String selector) {

        selector = selector.trim();

        // Workaround for encoded slashes.
        if (selector.contains("%252F")) {
            selector = selector.replace("%252F", "/");
        }

        // if it's a doi, it can be used directly
        if (DoiCheck.isDoi(selector)) {
            return selector;
        }

        StringBuilder sb = new StringBuilder();

        String firstToken = selector.split("\\s")[0];
        String extraQuery = selector.length() > firstToken.length()
            ? selector.substring(firstToken.length()).trim()
            : null;

        // if the first token is an orcid, it will be used as filter
        if (OrcidCheck.isOrcid(firstToken)) {
            sb.append("?filter=orcid:").append(firstToken);
            if (StringUtils.isNotEmpty(extraQuery)) {
                sb.append("&query=").append(URLEncoder.encode(extraQuery, StandardCharsets.UTF_8));
            }
        } else {
            sb.append("?query=").append(URLEncoder.encode(selector, StandardCharsets.UTF_8));
        }

        return sb.toString();
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
            query.addParameter("query", StringUtils.trim(queryString));
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

            Map<String, Map<String, String>> params = new HashMap<String, Map<String,String>>();
            String response = liveImportClient.executeHttpGetRequest(1000, uriBuilder.toString(), params);
            if (StringUtils.isEmpty(response)) {
                return results;
            }
            JsonNode jsonNode = convertStringJsonToJsonNode(response);
            Iterator<JsonNode> nodes = jsonNode.at("/message/items").iterator();
            while (nodes.hasNext()) {
                JsonNode node = nodes.next();
                if (!node.isMissingNode()) {
                    results.add(transformSourceRecords(node.toString()));
                }
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
            query.addParameter("id", StringUtils.trim(id));
        }

        private SearchByIdCallable(String id, Integer maxResult, Integer start) {
            query = new Query();
            query.addParameter("id", id);
            query.addParameter("count", maxResult);
            query.addParameter("start", start);
        }

        /**
         * This method handles two cases. The first case is when a single record is requested: the request does not
         * contain any parameter, the response contains one json object. The second case is when a query is performed,
         * with 0 to N results: the request contains parameters, the response contains a json array
         */
        @Override
        public List<ImportRecord> call() throws Exception {
            List<ImportRecord> results = new ArrayList<>();
            String selector = query.getParameterAsClass("id", String.class);
            boolean isDoi = DoiCheck.isDoi(selector);
            if (isDoi) {
                selector = "/" + DoiCheck.purgeDoiValue(selector);
            }
            URIBuilder uriBuilder = new URIBuilder(url + selector);

            // if the query is a single doi, we expect a single result and cannot use parameters
            if (!isDoi) {
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

            Map<String, Map<String, String>> params = new HashMap<String, Map<String,String>>();
            String resp = liveImportClient.executeHttpGetRequest(1000, uriBuilder.toString(), params);
            if (StringUtils.isEmpty(resp)) {
                return results;
            }
            JsonNode jsonNode = convertStringJsonToJsonNode(resp);
            Iterator<JsonNode> nodes = jsonNode.at("/message/items").iterator();
            while (nodes.hasNext()) {
                JsonNode node = nodes.next();
                if (!node.isMissingNode()) {
                    results.add(transformSourceRecords(node.toString()));
                }
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
            query.addParameter("query", StringUtils.trim(queryString));
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
            query.addParameter("id", StringUtils.trim(id));
            this.query = query;
        }

        @Override
        public Integer call() throws Exception {
            String selector = query.getParameterAsClass("id", String.class);
            URIBuilder uriBuilder = new URIBuilder(url + "/" + selector);
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
