/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.dataquality.factory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * This is an abstract class that can be used to force an implementation to be loaded
 * inside the {@code ApplicationContext} of Spring.
 * The {@code instance} that is placed inside this class represents the singleton instance
 * that will be loaded and shared with the {@code ApplicationContext}.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public abstract class AbstractFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware, BeanNameAware {

    protected String name;
    protected ApplicationContext applicationContext;
    protected T instance;

    protected abstract T createInstance() throws Exception;

    public T getObject() throws Exception {
        if (this.instance == null) {
            this.instance = createInstance();
        }
        return this.instance;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setBeanName(String name) {
        this.name = name;
    }

}
