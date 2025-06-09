/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.deduplication.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import com.ibm.icu.text.Normalizer;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

public class TitleWithDigitAndYearSignature extends MD5ValueSignature {

    private String metadataYear;

    @Autowired
    private ItemService itemService;

    @Override
    protected String normalize(DSpaceObject item, Context context, String value) {
        if (StringUtils.isNotBlank(value)) {

            String year = Objects.nonNull(item) ? getYear((Item) item) : StringUtils.EMPTY;
            String norm = Normalizer.normalize(value, Normalizer.NFD);
            CharsetDetector cd = new CharsetDetector();
            cd.setText(value.getBytes());
            CharsetMatch detect = cd.detect();

            if (Objects.nonNull(detect) && StringUtils.isNotBlank(detect.getLanguage())) {
                norm = norm.replaceAll("[^\\p{L}^\\p{N}]", "").toLowerCase(new Locale(detect.getLanguage()));
            } else {
                norm = norm.replaceAll("[^\\p{L}^\\p{N}]", "").toLowerCase();
            }

            return StringUtils.isNotBlank(year) ? year + " " + norm : norm;
        } else {
            return "item:" + item.getID();
        }
    }

    @Override
    protected String normalize(DSpaceObject item, String value) {
        String result = value;
        if (StringUtils.isEmpty(value)) {
            if (StringUtils.isNotEmpty(prefix)) {
                result = prefix + item.getID();
            } else {
                result = "entity:" + item.getID();
            }
        } else {
            for (String prefix : ignorePrefix) {
                if (value.startsWith(prefix)) {
                    result = value.substring(prefix.length());
                    break;
                }
            }
            String year = Objects.nonNull(item) ? getYear((Item) item) : StringUtils.EMPTY;
            result = StringUtils.isNotBlank(year) ? year + " " + result : result;
            if (StringUtils.isNotEmpty(prefix)) {
                result = prefix + result;
            }
        }

        return result;
    }

    private String getYear(Item item) {
        String value = itemService.getMetadata(item, metadataYear);
        return StringUtils.isNotBlank(value) ? StringUtils.substring(value, 0, 4) : StringUtils.EMPTY;
    }

    public String getMetadataYear() {
        return metadataYear;
    }

    public void setMetadataYear(String metadataYear) {
        this.metadataYear = metadataYear;
    }

    @Override
    public List<String> getConsumerTriggerMetadata() {
        return Arrays.asList(getMetadata(), metadataYear);
    }

}
