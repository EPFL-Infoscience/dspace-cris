/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import org.dspace.app.rest.RestResourceController;

/**
 * The DeduplicationSet REST Resource
 *
 * @author Francesco Pio Scognamiglio (francescopio.scognamiglio at 4science.it)
 */
@LinksRest(links = {
        @LinkRest(
                name = DeduplicationSetRest.ITEMS,
                method = "getItems"
        )
})
public class DeduplicationSetRest extends BaseObjectRest<String> {
    public static final String CATEGORY = "deduplications";
    public static final String NAME = "set";
    public static final String PLURAL_NAME = "sets";

    public static final String ITEMS = "items";

    private String id;

    private String signatureId;

    private String setChecksum;

    private List<String> otherSetIds;

    @Override
    @JsonProperty(access = Access.READ_ONLY)
    public String getType() {
        return NAME;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Class getController() {
        return RestResourceController.class;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSignatureId() {
        return signatureId;
    }

    public void setSignatureId(String signatureId) {
        this.signatureId = signatureId;
    }

    public String getSetChecksum() {
        return setChecksum;
    }

    public void setSetChecksum(String setChecksum) {
        this.setChecksum = setChecksum;
    }

    public List<String> getOtherSetIds() {
        return otherSetIds;
    }

    public void setOtherSetIds(List<String> otherSetIds) {
        this.otherSetIds = otherSetIds;
    }
}
