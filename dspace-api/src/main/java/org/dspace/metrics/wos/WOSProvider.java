/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.metrics.wos;
import java.util.List;
import java.util.Objects;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.metrics.scopus.CrisMetricDTO;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author mykhaylo boychuk (mykhaylo.boychuk at 4science.it)
 */
public class WOSProvider {

    private static Logger log = LogManager.getLogger(WOSProvider.class);

    @Autowired
    private WOSRestConnector wosRestConnector;

    public CrisMetricDTO getWOSObject(String id) {
        log.debug("looking for wos metrics for DOI: " + id);
        String wosResponse = wosRestConnector.get(id);
        if (StringUtils.isNotBlank(wosResponse)) {
            return extractMetricCount(wosResponse);
        }
        log.debug("WOS Response: " + wosResponse);
        log.debug("The DOI : " + id + " is wrong!");
        return null;
    }

    private CrisMetricDTO extractMetricCount(String wosResponse) {
        Integer metricCount = null;
        CrisMetricDTO metricDTO = new CrisMetricDTO();
        final String path = "$.Data.Records.records.REC[0].dynamic_data."
                + "citation_related.tc_list.silo_tc[?(@.coll_id== 'WOS')].local_count";
        try {
            // this logic was modified to avoid an explicit cast to org.json.JSONArray,
            // as JsonPath.read could return a net.minidev.json.JSONArray. Using a list we avoid the problem
            List<Object> result = JsonPath.read(wosResponse, path);
            if (result != null && !result.isEmpty()) {
                metricCount = (Integer) result.get(0);
            }
        } catch (PathNotFoundException e) {
            log.error("The path : " + path + " does not exist!");
        }
        if (Objects.isNull(metricCount)) {
            return null;
        }
        metricDTO.setMetricCount(metricCount.doubleValue());
        metricDTO.setMetricType(UpdateWOSMetrics.WOS_METRIC_TYPE);
        return metricDTO;
    }
}