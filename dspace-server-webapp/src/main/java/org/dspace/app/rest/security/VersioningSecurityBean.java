/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.security;
import java.sql.SQLException;
import java.util.UUID;

import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.services.RequestService;
import org.dspace.util.UUIDUtils;
import org.dspace.versioning.service.VersioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Methods of this class are used on PreAuthorize annotations
 * to check security on versioning endpoint
 * 
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 */
@Component(value = "versioningSecurity")
public class VersioningSecurityBean {

    private static Logger logger = LoggerFactory.getLogger(VersioningSecurityBean.class);
    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private VersioningService versioningService;

    @Autowired
    private RequestService requestService;

    @Autowired
    private ItemService itemService;

    /**
     * This method checks if the versioning features are enabled
     *
     * @return true if is enabled or unset
     */
    public boolean isEnableVersioning() {
        return configurationService.getBooleanProperty("versioning.enabled", true);
    }

    public boolean canDeleteVersion(UUID uuid) {
        if (!isEnableVersioning()) {
            return false;
        }
        Context context = ContextUtil.obtainContext(requestService.getCurrentRequest().getHttpServletRequest());
        try {
            Item item = itemService.find(context, uuid);
            return versioningService.canDeleteItemVersion(context, item);
        } catch (SQLException e ) {
            logger.error("Error while checking versioning permissions for item {}", UUIDUtils.toString(uuid));
            return false;
        }
    }

}