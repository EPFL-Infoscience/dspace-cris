/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.model;

import org.dspace.content.dto.ItemDTO;

public final class ItemImportDTO {

    private final String type;

    private final ItemDTO item;

    public ItemImportDTO(String type, ItemDTO item) {
        super();
        this.type = type;
        this.item = item;
    }

    public String getType() {
        return type;
    }

    public ItemDTO getItem() {
        return item;
    }

}
