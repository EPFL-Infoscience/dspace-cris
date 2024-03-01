/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.dataquality.adapter.factory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import javassist.util.proxy.ProxyFactory;
import org.dspace.dataquality.adapter.handler.AdapterInvocationHandler;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public abstract class AbstractAdapterFactory {

    private AbstractAdapterFactory() {
    }

    public static <T> T createAdapter(Class<T> interfaceClass, Object... target) {
        if (!Modifier.isInterface(interfaceClass.getModifiers())) {
            ProxyFactory factory = new ProxyFactory();
            factory.setSuperclass(interfaceClass);
            factory.setFilter((method) -> Modifier.isAbstract(method.getModifiers()));
            try {
                return (T) factory.create(new Class<?>[0], new Object[0], new AdapterInvocationHandler(target));
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                     InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            return (T) Proxy.newProxyInstance(null, new Class<?>[] {interfaceClass},
                                              new AdapterInvocationHandler(target));
        }
    }

}
