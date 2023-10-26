/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dto;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class acts as Data transfer object in which we can store data like in a
 * regular Bitstream object, but this one isn't saved in the DB. This can freely
 * be used to represent Bitstream without it being saved in the database, this
 * will typically be used when transferring data.
 *
 * @author Luca Giamminonni (luca.giamminonni at 4Science)
 *
 */
public class BitstreamDTO {

    private final String bundleName;

    private final Integer position;

    private String location;

    private final String checksum;

    private final List<MetadataValueDTO> metadataValues;

    private final List<ResourcePolicyDTO> resourcePolicies;

    public BitstreamDTO(String bundleName, String location, List<MetadataValueDTO> metadataValues) {
        this(bundleName, null, location, null, metadataValues, List.of());
    }

    public BitstreamDTO(String bundleName, String location, String checksum, List<MetadataValueDTO> metadataValues,
        List<ResourcePolicyDTO> resourcePolicies) {
        this(bundleName, null, location, checksum, metadataValues, resourcePolicies);
    }

    public BitstreamDTO(String bundleName, String location, List<MetadataValueDTO> metadataValues,
        List<ResourcePolicyDTO> resourcePolicies) {
        this(bundleName, null, location, null, metadataValues, resourcePolicies);
    }

    public BitstreamDTO(String bundleName, Integer position, String location,
        List<MetadataValueDTO> metadataValues, List<ResourcePolicyDTO> resourcePolicies) {
        this(bundleName, position, location, null, metadataValues, resourcePolicies);
    }

    public BitstreamDTO(String bundleName, Integer position, String location, String checksum,
        List<MetadataValueDTO> metadataValues, List<ResourcePolicyDTO> resourcePolicies) {
        this.bundleName = bundleName;
        this.position = position;
        this.location = location;
        this.checksum = checksum;
        this.metadataValues = emptyIfNull(metadataValues);
        this.resourcePolicies = emptyIfNull(resourcePolicies);
    }

    public String getBundleName() {
        return bundleName;
    }

    public String getLocation() {
        return location;
    }

    public String getChecksum() {
        return checksum;
    }

    public Integer getPosition() {
        return position;
    }

    public List<MetadataValueDTO> getMetadataValues() {
        return metadataValues;
    }

    public List<ResourcePolicyDTO> getResourcePolicies() {
        return resourcePolicies;
    }

    public List<MetadataValueDTO> getMetadataValues(String metadataField) {
        return metadataValues.stream()
            .filter(metadataValue -> metadataValue.getMetadataField().equals(metadataField))
            .collect(Collectors.toList());
    }

    public void updateLocationWithExtension(String fileExtension) {
        location += fileExtension;
    }
}
