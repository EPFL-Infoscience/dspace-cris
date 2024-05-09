/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.dataquality.adapter.factory;

import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dspace.dataquality.factory.AbstractFactoryBean;

/**
 * This class can be used to force the creation of a Bean that
 * encapsulate an Adapter.
 * Can be used to substitute a {@code <T>} bean inside the application, with an
 * adapter that contains also the changes introduced inside this addon, that are wrapped by the
 * {@code <U>} type.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public abstract class AbstractAdapterFactoryBean<T, U extends T> extends AbstractFactoryBean<T> {

    /**
     * Returns the Adaptee types that will be used to retrieve bean implementations loaded
     * inside Spring, in order to create the Adapter instance.
     * This instance will be of type {@code <U>}, but will be forced to be used also
     * by every {@code <T>} autowired bean.
     *
     * @return
     */
    protected abstract Class<?>[] getAdapteeTypes();

    @Override
    protected T createInstance() throws Exception {
        return AbstractAdapterFactory.createAdapter(
            (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1],
            getBeanDefinitionsForTypes(getAdapteeTypes())
        );
    }

    protected Stream<Object> getBeanDefinitionsForType(Class<?> type) {
        return Arrays.stream(this.applicationContext.getBeanNamesForType(type))
                     .filter(beanName -> !beanName.equals(this.name))
                     .map(beanName -> this.applicationContext.getBean(beanName));
    }

    protected Object[] getBeanDefinitionsForTypes(Class<?>... types) {
        return Arrays.stream(types)
                     .flatMap(type -> getBeanDefinitionsForType(type))
                     .collect(Collectors.toList())
                     .toArray();
    }


    @Override
    public Class<?> getObjectType() {
        return (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
}
