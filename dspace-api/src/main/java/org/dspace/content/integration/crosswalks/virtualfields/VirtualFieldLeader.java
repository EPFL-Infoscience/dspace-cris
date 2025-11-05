/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.integration.crosswalks.virtualfields;

import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

public class VirtualFieldLeader implements VirtualField{

    @Autowired
    private ItemService itemService;

    @Override
    public String[] getMetadata(Context context, Item item, String fieldName) {
        String code;

        String dcType = itemService.getMetadataFirstValue(item, "dc", "type", null, Item.ANY);

        if (dcType == null) {
            code = "am";
        } else {
            switch (dcType.toLowerCase()) {
                case "text":
                    code = "am";
                    break;
                case "dataset":
                    code = "mm";
                    break;
                case "image":
                    code = "km";
                    break;
                case "collection":
                    code = "pc";
                    break;
                case "sound":
                    code = "im";
                    break;
                case "event":
                    code = "rm";
                    break;
                case "software":
                    code = "mm";
                    break;
                case "service":
                    code = "mm";
                    break;
                default:
                    code = "am";
                    break;
            }
        }
        StringBuilder leader = new StringBuilder("     ");
        leader.append(code);
        leader.append("         ");
        leader.append("3u");
        leader.append("     ");
        leader.append(" ");

        return new String[]{leader.toString()};
    }
}
