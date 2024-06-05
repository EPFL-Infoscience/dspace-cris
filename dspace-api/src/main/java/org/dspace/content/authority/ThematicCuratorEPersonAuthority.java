/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.authority;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.util.UUIDUtils;
import org.dspace.web.ContextUtil;

/**
 * Implementation of {@link ChoiceAuthority} based on ThematicCuratorGroup. Allows you to set
 * the id of an eperson as authority.
 *
 * @author Aliaksei Bykau
 */
public class ThematicCuratorEPersonAuthority implements ChoiceAuthority {

    private static final Logger log = LogManager.getLogger(ThematicCuratorEPersonAuthority.class);

    public static final String THEMATIC_AREA_GROUP_NAME = "ThematicAreaCurators";

    /**
     * the name assigned to the specific instance by the PluginService, @see
     * {@link NameAwarePlugin}
     **/
    private String authorityName;

    private final EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();

    private final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();

    private final AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();

    @Override
    public Choices getBestMatch(String text, String locale) {
        return getMatches(text, 0, 2, locale);
    }

    @Override
    public Choices getMatches(String text, int start, int limit, String locale) {
        if (limit <= 0) {
            limit = 20;
        }

        Context context = getContext();

        List<EPerson> ePersons = searchEPersons(context, text, start, limit);

        List<Choice> choiceList = new ArrayList<Choice>();
        for (EPerson eperson : ePersons) {
            choiceList.add(new Choice(eperson.getID().toString(), eperson.getFullName(), eperson.getFullName()));
        }
        Choice[] results = new Choice[choiceList.size()];
        results = choiceList.toArray(results);
        return new Choices(results, start, ePersons.size(), Choices.CF_AMBIGUOUS, ePersons.size() > (start + limit), 0);
    }

    @Override
    public String getLabel(String key, String locale) {

        UUID uuid = UUIDUtils.fromString(key);
        if (uuid == null) {
            return null;
        }

        Context context = getContext();
        try {
            EPerson ePerson = ePersonService.find(context, uuid);
            return ePerson != null ? ePerson.getFullName() : null;
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    private List<EPerson> searchEPersons(Context context, String text, int start, int limit) {
        try {
            List<EPerson> allEPersonsFromSearch = ePersonService.search(context, text, start, limit);
            Group group = groupService.findByName(context, THEMATIC_AREA_GROUP_NAME);
            List<EPerson> groupEPersons = group.getMembers();

            List<EPerson> resultEPersonList = new ArrayList<>();
            for (EPerson ePersonFromSearch : allEPersonsFromSearch) {
                if (groupEPersons.contains(ePersonFromSearch)) {
                    resultEPersonList.add(ePersonFromSearch);
                }
            }

            int toIndex = Math.min(limit + start, resultEPersonList.size());
            return resultEPersonList.subList(start, toIndex);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    private Context getContext() {
        Context context = ContextUtil.obtainCurrentRequestContext();
        return context != null ? context : new Context();
    }

    private boolean isCurrentUserAdminOrAccessGroupManager(Context context) {
        try {
            return authorizeService.isAdmin(context) || authorizeService.isAccountManager(context);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getPluginInstanceName() {
        return authorityName;
    }

    @Override
    public void setPluginInstanceName(String name) {
        this.authorityName = name;
    }
}