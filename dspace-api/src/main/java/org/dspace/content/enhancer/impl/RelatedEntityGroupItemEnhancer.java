/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.enhancer.impl;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.UUID;

import org.dspace.content.MetadataValue;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.GroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class RelatedEntityGroupItemEnhancer extends RelatedEntityItemEnhancer {

    private static final Logger logger = LoggerFactory.getLogger(RelatedEntityGroupItemEnhancer.class);

    @Autowired
    private GroupService groupService;

    @Override
    protected String getRelatedItemValue(Context context, MetadataValue relatedItemMetadataValue) {
        return Optional.ofNullable(findGroupByName(context, relatedItemMetadataValue))
            .map(Group::getID)
            .map(UUID::toString)
            .orElse(null);
    }

    protected Group findGroupByName(Context context, MetadataValue relatedItemMetadataValue) {
        Group group = null;
        try {
            group = this.groupService.findByName(context, relatedItemMetadataValue.getValue());
        } catch (SQLException e) {
            String message = MessageFormat.format(
                "Error while searching for group with name {}",
                relatedItemMetadataValue.getValue()
            );
            logger.error(message,e);
            throw new SQLRuntimeException(message,e);
        }
        return group;
    }

}
