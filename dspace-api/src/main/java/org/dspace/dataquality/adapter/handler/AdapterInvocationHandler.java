/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.dataquality.adapter.handler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;

import javassist.util.proxy.MethodHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class AdapterInvocationHandler implements InvocationHandler, MethodHandler {

    private static final Logger logger = LoggerFactory.getLogger(AdapterInvocationHandler.class);

    private LinkedList<?> targets;

    public AdapterInvocationHandler(Object ... targets) {
        this.targets = new LinkedList<>(Arrays.asList(targets));
    }

    public Object invoke(Object self, Method method, Method proceed, Object[] args) throws Throwable {
        logger.debug(
            "Calling the method: " + Optional.ofNullable(method).map(Method::toGenericString).orElse("null") +
            " and proceed: " + Optional.ofNullable(proceed).map(Method::toGenericString).orElse("null")
        );
        try {
            return invoke(method, args);
        } catch (InvocationTargetException ex) {
            // May throw a NullPointerException if there is no target exception
            throw ex.getTargetException();
        }
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return invoke(method, args);
        } catch (InvocationTargetException ex) {
            logger.error(ex.getMessage());
            // May throw a NullPointerException if there is no target exception
            throw ex.getTargetException();
        }
    }

    private Object invoke(Method method, Object[] args)
        throws InvocationTargetException {
        StringBuilder invocations = new StringBuilder();
        Iterator<?> iterator = targets.iterator();
        Object delegatedObj;
        while (iterator.hasNext()) {
            delegatedObj = iterator.next();
            Method targetMethod = null;
            try {
                targetMethod = delegatedObj.getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException ex) {
                invocations.append(
                    "Target " + delegatedObj.getClass() + " does not support: " + method.toGenericString()
                );
            }
            if (targetMethod != null && method.getReturnType().isAssignableFrom(targetMethod.getReturnType())) {
                try {
                    return targetMethod.invoke(delegatedObj, args);
                } catch (Exception ex) {
                    invocations.append(
                        "Target " + delegatedObj.getClass() + " does not support: " + method.toGenericString()
                    );
                }
            } else {
                invocations.append(
                    "Target " + delegatedObj.getClass() + " does not support: " + method.toGenericString()
                );
            }
        }
        throw new UnsupportedOperationException(invocations.toString());
    }
}
