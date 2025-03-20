/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.Date;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * The AccessConditionDTO is a partial representation of the DSpace
 * {@link ResourcePolicyRest} as used in the patch payload for the upload and itemAccessConditions
 * submission sections (see {@link UploadBitstreamRest, @link DataAccessCondition}.
 * The main reason for this class is to have a DTO to use serialize/deserialize the REST model, that
 * include reference to the GroupRest and EPersonRest object, in the upload
 * section data in a simpler way where such reference are just UUID. Indeed, due
 * to the fact that the RestModel class are serialized according to the HAL
 * format and the reference are only exposed in the _links section of the
 * RestResource it was not possible to use the {@link ResourcePolicyRest} class
 * directly in the upload section
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 */
public class AccessConditionDTO  {

    private Integer id;

    private String name;

    private String description;

    private Date startDate;

    private Date endDate;

    private String stepId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String toJson() {
        return getGson().toJson(this);
    }

    public static AccessConditionDTO fromJson(String json) {
        JsonElement jsonElement = parseJson(json);
        String jsonObject = extractJsonObject(jsonElement);
        return getGson().fromJson(jsonObject, AccessConditionDTO.class);
    }

    private static Gson getGson() {
        return new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
    }

    private static JsonElement parseJson(String json) {
        return JsonParser.parseString(json);
    }

    private static String extractJsonObject(JsonElement jsonElement) {
        if (jsonElement.isJsonArray()) {
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            if (jsonArray.size() == 0) {
                throw new JsonSyntaxException("Empty JSON array is not allowed");
            }
            return jsonArray.get(0).toString();
        } else if (jsonElement.isJsonObject()) {
            return jsonElement.toString();
        }
        throw new JsonSyntaxException("Unexpected JSON type: " + jsonElement.getClass().getSimpleName());
    }
}
