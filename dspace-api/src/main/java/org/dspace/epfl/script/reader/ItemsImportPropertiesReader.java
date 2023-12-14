/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.reader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ItemsImportPropertiesReader implements ItemsImportMetadataFieldReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemsImportPropertiesReader.class);

    @Autowired
    private ConfigurationService configurationService;

    private Properties properties;

    private String readerName;

    private String propertiesPath;

    private String defaultValue;

    @PostConstruct
    private void setupMapping() {

        properties = new Properties();

        try (FileInputStream fis = new FileInputStream(getPropertiesFile())) {
            properties.load(fis);
        } catch (IOException ex) {
            LOGGER.error("An error occurs reading properties file at path " + propertiesPath, ex);
        }

    }

    @Override
    public List<MetadataValueDTO> readValues(Context context, String metadataField, String type, NodeList nodeList) {
        List<MetadataValueDTO> metadataValues = new ArrayList<MetadataValueDTO>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            String key = node.getTextContent();

            if (StringUtils.isBlank(key) || (StringUtils.isBlank(defaultValue) && !properties.containsKey(key))) {
                continue;
            }

            metadataValues.add(new MetadataValueDTO(metadataField, properties.getProperty(key, defaultValue)));
        }

        return metadataValues;
    }

    private File getPropertiesFile() {
        String parent = configurationService.getProperty("dspace.dir") + File.separator + "config" + File.separator;
        return new File(parent, propertiesPath);
    }

    @Override
    public String getReaderName() {
        return readerName;
    }

    public void setReaderName(String readerName) {
        this.readerName = readerName;
    }

    public String getPropertiesPath() {
        return propertiesPath;
    }

    public void setPropertiesPath(String propertiesPath) {
        this.propertiesPath = propertiesPath;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

}
