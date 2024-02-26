/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.dataquality.adapter.factory;

import java.lang.reflect.ParameterizedType;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public abstract class AddonFactory<T> {

    protected T createAdapter(Object... target) {
        return AbstractAdapterFactory.createAdapter(
            (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0],
            target
        );
    }

    public abstract T createAdapter();

}
