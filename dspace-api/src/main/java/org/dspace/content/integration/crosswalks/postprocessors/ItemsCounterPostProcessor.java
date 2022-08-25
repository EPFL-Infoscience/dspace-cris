/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.integration.crosswalks.postprocessors;

import java.util.List;
import java.util.function.Consumer;

/**
 * Implementation that set number of items instead of placeholder
 * string to identify a single item to be counted is provided in input
 */
public class ItemsCounterPostProcessor implements Consumer<List<String>> {

    private static final String COUNTER_FIELD = "#items.counter#";
    private final String itemPlaceholder;

    public ItemsCounterPostProcessor(String itemPlaceholder) {
        this.itemPlaceholder = itemPlaceholder;
    }

    @Override
    public void accept(List<String> strings) {
        int total = 0;
        int counterLineId = -1;
        for (int i = 0; i < strings.size(); i++) {
            String s = strings.get(i);
            if (s.contains(COUNTER_FIELD)) {
                counterLineId = i;
                continue;
            }
            if (s.contains(itemPlaceholder)) {
                total++;
            }
        }
        if (counterLineId >= 0) {
            strings.set(counterLineId, strings.get(counterLineId)
                .replaceAll(COUNTER_FIELD, String.valueOf(total)));
        }
    }
}
