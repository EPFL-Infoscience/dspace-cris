/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.epo.service;

import static org.dspace.importer.external.liveimportclient.service.LiveImportClientImpl.HEADER_PARAMETERS;
import static org.dspace.util.FunctionalUtils.throwingMapperWrapper;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.client.utils.URIBuilder;
import org.apache.jena.ext.xerces.impl.dv.util.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.util.XMLUtils;
import org.dspace.content.Item;
import org.dspace.importer.external.datamodel.ImportRecord;
import org.dspace.importer.external.datamodel.Query;
import org.dspace.importer.external.exception.MetadataSourceException;
import org.dspace.importer.external.liveimportclient.service.LiveImportClient;
import org.dspace.importer.external.metadatamapping.MetadataFieldConfig;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.dspace.importer.external.metadatamapping.contributor.EpoIdMetadataContributor.EpoDocumentId;
import org.dspace.importer.external.service.AbstractImportMetadataSourceService;
import org.dspace.importer.external.service.components.QuerySource;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implements a data source for querying EPO
 * 
 * @author Pasquale Cavallo (pasquale.cavallo at 4Science dot it)
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4Science.com)
 */
public class EpoImportMetadataSourceServiceImpl extends AbstractImportMetadataSourceService<Element>
        implements QuerySource {

    private final static Logger log = LogManager.getLogger();

    private String url;
    private String authUrl;
    private String searchUrl;

    private String consumerKey;
    private String consumerSecret;
    private String bearerToken;
    private Date bearerExpire;

    /**
     * Bearer is valid for 20 minutes according to the doc. Let's override it if
     * needed and use a safer default to 5 minutes
     */
    private int bearerValiditySeconds = 300;

    private MetadataFieldConfig dateFilled;
    private MetadataFieldConfig applicationNumber;

    public static final String APP_NO_DATE_SEPARATOR = "$$$";
    private static final String APP_NO_DATE_SEPARATOR_REGEX = "\\$\\$\\$";

    @Autowired
    private LiveImportClient liveImportClient;

    @Override
    public void init() throws Exception {}

    /**
     * The string that identifies this import implementation. Preferable a URI
     *
     * @return the identifying uri
     */
    @Override
    public String getImportSource() {
        return "epo";
    }

    /**
     * Set the customer epo key
     * @param consumerKey the customer consumer key
     */
    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    /**
     * Set the costumer epo secret
     * @param consumerSecret the customer epo secret
     */
    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public void setDateFilled(MetadataFieldConfig dateFilled) {
        this.dateFilled = dateFilled;
    }

    public MetadataFieldConfig getDateFilled() {
        return dateFilled;
    }

    public void setApplicationNumber(MetadataFieldConfig applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public MetadataFieldConfig getApplicationNumber() {
        return applicationNumber;
    }

    /**
     * Bearer is valid for 20 minutes according to the doc. Let's override it if
     * needed and use a safer default to 5 minutes
     *
     * @param bearerValiditySeconds
     */
    public void setBearerValiditySeconds(int bearerValiditySeconds) {
        this.bearerValiditySeconds = bearerValiditySeconds;
    }

    /***
     * Log to EPO
     * 
     * @return access token
     * @throws IOException e
     * @throws HttpException e
     */
    protected String login() throws IOException, HttpException {
        if (bearerToken != null && bearerValiditySeconds > 0 && new Date().before(bearerExpire)) {
            return bearerToken;
        } else {
            Map<String, Map<String, String>> params = Map.of(
                HEADER_PARAMETERS,
                Map.of(
                "Authorization", "Basic " + Base64.encode((consumerKey + ":" + consumerSecret).getBytes()),
                "Content-type", "application/x-www-form-urlencoded"
                )
            );
            String json = liveImportClient.executeHttpPostRequest(this.authUrl, params,
                    "grant_type=client_credentials");
            bearerToken = StringUtils.isBlank(json)
                ? json
                : new ObjectMapper(new JsonFactory()).readTree(json).get("access_token").asText();
            long currentTimeMillis = System.currentTimeMillis();
            long newTimeMillis = currentTimeMillis + (bearerValiditySeconds * 1000);
            bearerExpire = new Date(newTimeMillis);
            return bearerToken;
        }
    }

    @Override
    public int getRecordsCount(String query) throws MetadataSourceException {
        try {
            return StringUtils.isNotBlank(consumerKey) && StringUtils.isNotBlank(consumerSecret)
                ? retry(new CountRecordsCallable(query, login()))
                : 0;
        } catch (IOException | HttpException e) {
            log.warn(e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public int getRecordsCount(Query query) throws MetadataSourceException {
        try {
            return StringUtils.isNotBlank(consumerKey) && StringUtils.isNotBlank(consumerSecret)
                ? retry(new CountRecordsCallable(query, login()))
                : 0;
        } catch (IOException | HttpException e) {
            log.warn(e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Collection<ImportRecord> getRecords(String query, int start, int count) throws MetadataSourceException {
        try {
            return StringUtils.isNotBlank(consumerKey) && StringUtils.isNotBlank(consumerSecret)
                ? retry(new SearchByQueryCallable(query, login(), start, count))
                : new ArrayList<>();
        } catch (IOException | HttpException e) {
            log.warn(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Collection<ImportRecord> getRecords(Query query) throws MetadataSourceException {
        try {
            return StringUtils.isNotBlank(consumerKey) && StringUtils.isNotBlank(consumerSecret)
                ? retry(new SearchByQueryCallable(query, login()))
                : new ArrayList<>();
        } catch (IOException | HttpException e) {
            log.warn(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public ImportRecord getRecord(String id) throws MetadataSourceException {
        try {
            return StringUtils.isNotBlank(consumerKey) && StringUtils.isNotBlank(consumerSecret)
                ? retry(new SearchByIdCallable(id, login())).stream().findFirst().orElse(null)
                : null;
        } catch (IOException | HttpException e) {
            log.warn(e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public ImportRecord getRecord(Query query) throws MetadataSourceException {
        return null;
    }

    @Override
    public Collection<ImportRecord> findMatchingRecords(Item item) throws MetadataSourceException {
        return null;
    }

    @Override
    public Collection<ImportRecord> findMatchingRecords(Query query) throws MetadataSourceException {
        return null;
    }

    /**
     * This class is a Callable implementation to count the number of entries for an EPO query.
     * This Callable use as query value to EPO the string queryString passed to constructor.
     * If the object will be constructed through Query instance, the value of the Query's
     * map with the key "query" will be used.
     * 
     * @author Mykhaylo Boychuk (mykhaylo.boychuk@4science.com)
     */
    private class CountRecordsCallable implements Callable<Integer> {

        private final String bearer;
        private final String query;

        private CountRecordsCallable(Query query, String bearer) {
            this.query = query.getParameterAsClass("query", String.class);
            this.bearer = bearer;
        }

        private CountRecordsCallable(String query, String bearer) {
            this.query = query;
            this.bearer = bearer;
        }

        public Integer call() {
            return countDocument(bearer, query);
        }
    }

    /**
     * This class is a Callable implementation to get an EPO entry using epodocID (epodoc:AB1234567T)
     * The epodocID to use can be passed through the constructor as a String or as Query's map entry, with the key "id".
     *
     * @author Mykhaylo Boychuk (mykhaylo.boychuk@4science.com)
     */
    private class SearchByIdCallable implements Callable<List<ImportRecord>> {

        private final String id;
        private final String bearer;

        private SearchByIdCallable(String id, String bearer) {
            this.id = id;
            this.bearer = bearer;
        }

        public List<ImportRecord> call() {
            int positionToSplit = id.indexOf(":");
            String docType = EpoDocumentId.EPODOC;
            String idS = id;
            if (positionToSplit != -1) {
                docType = id.substring(0, positionToSplit);
                idS = id.substring(positionToSplit + 1);
            } else if (id.contains(APP_NO_DATE_SEPARATOR)) {
                // check to ensure that we actually have both values
                if (id.split(APP_NO_DATE_SEPARATOR_REGEX).length < 2) {
                    return Collections.emptyList();
                }
                // special case the id is the combination of the applicationnumber and date filed
                String query = "applicationnumber=" + id.split(APP_NO_DATE_SEPARATOR_REGEX)[0];
                return new SearchByQueryCallable(query, bearer, 0, 10)
                    .call()
                    .stream()
                    .filter(
                        r -> r.getValue(dateFilled.getSchema(), dateFilled.getElement(), dateFilled.getQualifier())
                              .stream()
                              .map(MetadatumDTO::getValue)
                              .anyMatch(value -> StringUtils.equals(value, id.split(APP_NO_DATE_SEPARATOR_REGEX)[1]))
                    )
                    .limit(1)
                    .collect(Collectors.toList());
            }
            // search by Patent Number
            return searchDocument(bearer, idS, docType);
        }
    }

    /**
     * This class is a Callable implementation to get EPO entries based on query object.
     * This Callable use as query value the string queryString passed to constructor.
     * If the object will be constructed through Query instance, a Query's map entry with key "query" will be used.
     * Pagination is supported too, using the value of the Query's map with keys "start" and "count".
     * 
     * @author Mykhaylo Boychuk (mykhaylo.boychuk@4science.com)
     */
    private class SearchByQueryCallable implements Callable<List<ImportRecord>> {

        private final Query query;
        private final Integer start;
        private final Integer count;
        private final String bearer;

        private SearchByQueryCallable(Query query, String bearer) {
            this.query = query;
            this.bearer = bearer;
            this.start = 0;
            this.count = 20;
        }

        public SearchByQueryCallable(String queryValue, String bearer, Integer start, Integer count) {
            this.query = new Query();
            this.query.addParameter("query", queryValue);
            this.start = Objects.nonNull(start) ? start : 0;
            this.count = Objects.nonNull(count) ? count : 20;
            this.bearer = bearer;
        }

        @Override
        public List<ImportRecord> call() {
            List<ImportRecord> records = new ArrayList<>();
            String queryString = query.getParameterAsClass("query", String.class);
            if (StringUtils.isAnyBlank(consumerKey, consumerSecret, bearer, queryString)) {
                return records;
            }
            List<EpoDocumentId> epoDocIds = searchDocumentIds(bearer, queryString, start + 1, count);
            if (epoDocIds.size() != count) {
                log.warn("retrieved a different number of identifiers than expected " + epoDocIds.size() +
                        " vs " + count + " epoDocIds");
            }
            for (EpoDocumentId epoDocId : epoDocIds) {
                List<ImportRecord> foundRecords = searchDocument(bearer, epoDocId);
                if (foundRecords.size() > 1) {
                    log.warn("More than one record are returned with epocID " + epoDocId);
                } else if (foundRecords.size() == 0) {
                    log.warn("No record are returned with epocID " + epoDocId);
                }
                records.addAll(foundRecords);
            }
            return records;
        }
    }

    private Integer countDocument(String bearer, String query) {
        if (StringUtils.isBlank(bearer)) {
            return 0;
        }
        try {
            Map<String, Map<String, String>> params = new HashMap<>();
            Map<String, String> headerParameters = new HashMap<>();
            headerParameters.put("Authorization", "Bearer " + bearer);
            headerParameters.put("X-OPS-Range", "1-1");
            params.put(HEADER_PARAMETERS, headerParameters);

            URIBuilder uriBuilder = new URIBuilder(this.searchUrl);
            uriBuilder.addParameter("q", query);

            String response = liveImportClient.executeHttpGetRequest(1000, uriBuilder.toString(), params);
            if (StringUtils.isBlank(response)) {
                return 0;
            }

            SAXBuilder saxBuilder = XMLUtils.getSAXBuilder();
            // To properly parse EPO responses, we must allow DOCTYPEs overall. But, we can still apply all the
            // other default XXE protections, including disabling external entities and entity expansion.
            // NOTE: we only need to allow DOCTYPEs for this initial API call. All other calls have them disabled.
            saxBuilder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            Document document = saxBuilder.build(new StringReader(response));
            Element root = document.getRootElement();

            List<Namespace> namespaces = Arrays.asList(
                 Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink"),
                 Namespace.getNamespace("ops", "http://ops.epo.org"),
                 Namespace.getNamespace("ns", "http://www.epo.org/exchange"));

            List<Object> nodes = XPathFactory.instance()
                .compile("//ops:biblio-search/@total-result-count", Filters.fpassthrough(), null, namespaces)
                .evaluate(root);

            //exactly one element expected for any field
            return Integer.parseInt(CollectionUtils.isEmpty(nodes) ? StringUtils.EMPTY : getValue(nodes.get(0)));
        } catch (JDOMException | IOException | URISyntaxException e) {
            log.error(e.getMessage(), e);
            return 0;
        }
    }

    private List<EpoDocumentId> searchDocumentIds(String bearer, String query, int start, int count) {
        int end = start + count - 1;
        if (StringUtils.isBlank(bearer)) {
            return new ArrayList<>();
        }
        try {
            Map<String, Map<String, String>> params = new HashMap<>();
            Map<String, String> headerParameters = new HashMap<>();
            headerParameters.put("Authorization", "Bearer " + bearer);
            if (start >= 1 && end > start) {
                headerParameters.put("X-OPS-Range", start + "-" + end);
            }
            params.put(HEADER_PARAMETERS, headerParameters);

            URIBuilder uriBuilder = new URIBuilder(this.searchUrl);
            uriBuilder.addParameter("q", query);

            String response = liveImportClient.executeHttpGetRequest(1000, uriBuilder.toString(), params);
            if (StringUtils.isBlank(response)) {
                return new ArrayList<>();
            }

            SAXBuilder saxBuilder = XMLUtils.getSAXBuilder();
            Document document = saxBuilder.build(new StringReader(response));
            Element root = document.getRootElement();

            List<Namespace> namespaces = Arrays.asList(
                 Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink"),
                 Namespace.getNamespace("ops", "http://ops.epo.org"),
                 Namespace.getNamespace("ns", "http://www.epo.org/exchange")
            );

            List<Element> docIs = XPathFactory.instance()
                .compile("//ns:document-id", Filters.element(), null, namespaces)
                .evaluate(root);

            return docIs.stream()
                        .map(throwingMapperWrapper(docId -> new EpoDocumentId(docId, namespaces)))
                        .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    private List<ImportRecord> searchDocument(String bearer, EpoDocumentId id) {
        return searchDocument(bearer, id.getId(), id.getDocumentIdType());
    }

    private List<ImportRecord> searchDocument(String bearer, String id, String docType) {
        if (StringUtils.isBlank(bearer)) {
            return new ArrayList<>();
        }
        try {
            Map<String, Map<String, String>> params = new HashMap<>();
            Map<String, String> headerParameters = new HashMap<>();
            headerParameters.put("Authorization", "Bearer " + bearer);
            params.put(HEADER_PARAMETERS, headerParameters);

            String url = this.url.replace("$(doctype)", docType).replace("$(id)", id);
            String response = liveImportClient.executeHttpGetRequest(1000, url, params);

            return StringUtils.isBlank(response)
                ? new ArrayList<>()
                : splitToRecords(response)
                    .stream()
                    .map(this::transformSourceRecords)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    private List<Element> splitToRecords(String recordsSrc) {
        try {
            SAXBuilder saxBuilder = XMLUtils.getSAXBuilder();
            Document document = saxBuilder.build(new StringReader(recordsSrc));
            Element root = document.getRootElement();
            List<Namespace> namespaces = Collections.singletonList(
                Namespace.getNamespace("ns", "http://www.epo.org/exchange")
            );
            XPathExpression<Element> xpath = XPathFactory.instance().compile(
                "//ns:exchange-documents",
                Filters.element(), null, namespaces
            );

            return xpath.evaluate(root);
        } catch (JDOMException | IOException e) {
            log.error(e.getMessage(), e);
            return new LinkedList<>();
        }
    }

    private String getValue(Object el) {
        if (el instanceof Element) {
            return ((Element) el).getText();
        } else if (el instanceof Attribute) {
            return ((Attribute) el).getValue();
        } else if (el instanceof String) {
            return (String) el;
        } else if (el instanceof Text) {
            return ((Text) el).getText();
        } else {
            log.error("node of type: " + el.getClass());
            return "";
        }
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setAuthUrl(String authUrl) {
        this.authUrl = authUrl;
    }

    public void setSearchUrl(String searchUrl) {
        this.searchUrl = searchUrl;
    }

    /**
     * This method force the next API call to obtain a new login token
     */
    public void expireLogin() {
        this.bearerToken = null;
    }
}
