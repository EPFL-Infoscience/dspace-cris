/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.epfl.script.service.impl;

import java.sql.SQLException;
import javax.persistence.NoResultException;
import javax.persistence.Query;

import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;
import org.dspace.epfl.script.model.ItemsImportCreationDate;
import org.dspace.epfl.script.service.ItemsImportCreationDateDao;

public class ItemsImportCreationDateDaoImpl extends AbstractHibernateDAO<ItemsImportCreationDate>
    implements ItemsImportCreationDateDao {

    @Override
    public ItemsImportCreationDate find(Context context, String id) {
        try {
            return getHibernateSession(context).find(ItemsImportCreationDate.class, id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String findCreationDate(Context context, String id) {
        try {
            Query query = createQuery(context,
                "SELECT cd.creationDate FROM ItemsImportCreationDate cd WHERE cd.id = :id");
            query.setParameter("id", id);
            return (String) query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
