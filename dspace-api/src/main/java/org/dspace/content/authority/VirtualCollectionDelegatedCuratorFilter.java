/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.content.authority;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

public class VirtualCollectionDelegatedCuratorFilter extends EntityTypeAuthorityFilter {

    @Autowired
    private AuthorizeService authorizeService;
    public VirtualCollectionDelegatedCuratorFilter(List<String> customQueries) {
        super(customQueries);
    }

    @Override
    public List<String> getFilterQueries(Context context, LinkableEntityAuthority linkableEntityAuthority) {
        try {
            if (context.getCurrentUser() == null || authorizeService.isAdmin(context)) {
                return List.of();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        UUID currentUser = context.getCurrentUser().getID();
        return customQueries.stream()
                            .map(cq -> MessageFormat.format(cq,
                                                            currentUser,
                                                            currentUser))
                            .collect(Collectors.toList());
    }
}
