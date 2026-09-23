/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.services.ConfigurationService;
import org.springframework.util.Assert;

/**
 * Class that parse a properties file present in the crosswalks directory and
 * allows to get its values given a key.
 *
 * @author Andrea Bollini
 * @author Kostas Stamatis
 * @author Luigi Andrea Pascarelli
 * @author Panagiotis Koutsourakis
 * @author Luca Giamminonni
 */
public class SimpleMapConverter {

    private String converterNameFile; // The properties filename

    private ConfigurationService configurationService;

    private Map<String, String> mapping;

    private String defaultValue = "";

    private Boolean allowEmptyValue = false;

    /**
     * Charset used to read the properties file. When left null, the file is loaded with
     * the original {@link Properties#load(java.io.InputStream)} behaviour. Set it to a
     * different charset (e.g. UTF-8) when the mapping file stores non-ASCII keys as raw
     * bytes in that encoding.
     */
    private Charset encoding;

    /**
     * This flag would inform the caller of the converter that it expects to deal
     * with authority values instead than text value
     */
    private boolean useAuthority = false;

    /**
     * Parse the configured property file.
     */
    public void init() {

        if (MapUtils.isNotEmpty(mapping)) {
            return;
        }

        Assert.notNull(converterNameFile, "No properties file name provided");
        Assert.notNull(configurationService, "No configuration service provided");

        String mappingFile = configurationService.getProperty(
            "dspace.dir") + File.separator + "config" + File.separator + "crosswalks" + File.separator +
            converterNameFile;

        try (FileInputStream fis = new FileInputStream(new File(mappingFile))) {

            Properties mapConfig = new Properties();
            if (encoding == null) {
                mapConfig.load(fis);
            } else {
                // A charset was explicitly configured: read the file through it. Needed when
                // the mapping file stores non-ASCII keys as raw bytes (e.g. UTF-8).
                try (Reader reader = new InputStreamReader(fis, encoding)) {
                    mapConfig.load(reader);
                }
            }

            this.mapping = parseProperties(mapConfig);

        } catch (Exception e) {
            throw new IllegalArgumentException("An error occurs parsing " + mappingFile, e);
        }

    }

    /**
     * Returns the value related to the given key. If the given key is not found the
     * incoming value is returned by default. If the given key is not found but
     * allowEmptyValue is set to true, an empty string is returned.
     *
     * @param  key the key to search for a value
     * @return     the value
     */
    public String getValue(String key) {

        String value = mapping.getOrDefault(key, defaultValue);

        if (StringUtils.equals("@@ident@@", value)) {
            return key;
        }

        if (!allowEmptyValue && StringUtils.isBlank(value)) {
            return key;
        }

        return value;
    }

    public boolean isUseAuthority() {
        return useAuthority;
    }

    public void setUseAuthority(boolean useAuthority) {
        this.useAuthority = useAuthority;
    }

    private Map<String, String> parseProperties(Properties properties) {

        Map<String, String> mapping = new HashMap<String, String>();

        for (Object key : properties.keySet()) {
            String keyString = (String) key;
            mapping.put(keyString, properties.getProperty(keyString, ""));
        }

        return mapping;

    }

    public Map<String, String> getMapping() {
        return mapping;
    }

    public void setMapping(Map<String, String> mapping) {
        this.mapping = new HashMap<>(mapping);
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setConverterNameFile(String converterNameFile) {
        this.converterNameFile = converterNameFile;
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public void setAllowEmptyValue(Boolean allowEmptyValue) {
        this.allowEmptyValue = allowEmptyValue;
    }

    public Boolean getAllowEmptyValue() {
        return allowEmptyValue;
    }

    /**
     * Sets the charset used to read the properties file, given its name (e.g. "UTF-8").
     * When not set, the file is loaded with the original Properties.load(InputStream)
     * behaviour.
     *
     * @param encoding the charset name
     */
    public void setEncoding(String encoding) {
        this.encoding = Charset.forName(encoding);
    }

    public String getEncoding() {
        return encoding != null ? encoding.name() : null;
    }
}
