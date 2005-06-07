/**
 *
 * Copyright 2005 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.gbean.spring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.gbean.metadata.ClassMetadata;
import org.gbean.metadata.ConstructorMetadata;
import org.gbean.metadata.MetadataManager;
import org.gbean.metadata.ParameterMetadata;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * @version $Revision$ $Date$
 */
public class NamedConstructorArgs implements BeanFactoryPostProcessor {
    private final MetadataManager metadataManager;
    private final Map defaultValues = new HashMap();

    public NamedConstructorArgs(MetadataManager metadataManager) {
        this.metadataManager = metadataManager;
    }

    public void addDefaultValue(String name, Class type, Object value) {
        defaultValues.put(new DefaultProperty(name, type), value);
    }
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        SpringVisitor visitor = new AbstractSpringVisitor() {
            public void visitBeanDefinition(BeanDefinition beanDefinition, Object data) throws BeansException {
                super.visitBeanDefinition(beanDefinition, data);

                if (!(beanDefinition instanceof RootBeanDefinition)) {
                    return;
                }

                RootBeanDefinition rootBeanDefinition = ((RootBeanDefinition) beanDefinition);
                processParameters(rootBeanDefinition);

            }
        };
        visitor.visitBeanFactory(beanFactory, null);
    }

    private void processParameters(RootBeanDefinition rootBeanDefinition) {
        ConstructorArgumentValues constructorArgumentValues = rootBeanDefinition.getConstructorArgumentValues();

        // if this bean already has constructor arguments defined, don't mess with them
        if (constructorArgumentValues.getArgumentCount() > 0) {
            return;
        }

        // try to get a list of constructor arg names to use
        ConstructorMetadata constructorMetadata = getConstructor(rootBeanDefinition);
        if (constructorMetadata == null) {
            return;
        }

        // remove each named property and add an indexed constructor arg
        MutablePropertyValues propertyValues = rootBeanDefinition.getPropertyValues();
        List parameters = constructorMetadata.getParameters();
        for (ListIterator iterator = parameters.listIterator(); iterator.hasNext();) {
            ParameterMetadata parameterMetadata = (ParameterMetadata) iterator.next();
            String name = (String) parameterMetadata.get("name");

            Class parameterType = parameterMetadata.getType();
            PropertyValue propertyValue = propertyValues.getPropertyValue(name);
            if (propertyValue != null) {
                propertyValues.removePropertyValue(name);
                constructorArgumentValues.addIndexedArgumentValue(iterator.previousIndex(), propertyValue.getValue(), parameterType.getName());
            } else {
                Object defaultValue = defaultValues.get(new DefaultProperty(name, parameterType));
                if (defaultValue == null) {
                    defaultValue = DEFAULT_VALUE.get(parameterType);
                }
                constructorArgumentValues.addIndexedArgumentValue(iterator.previousIndex(), defaultValue, parameterType.getName());
            }
        }

        // todo set any usable default values on the bean definition
    }

    private ConstructorMetadata getConstructor(RootBeanDefinition rootBeanDefinition) {
        Class beanType = rootBeanDefinition.getBeanClass();

        // try to get the class metadata
        ClassMetadata classMetadata = metadataManager.getClassMetadata(beanType);

        // get a set containing the names of the defined properties
        Set propertyNames = new HashSet();
        PropertyValue[] values = rootBeanDefinition.getPropertyValues().getPropertyValues();
        for (int i = 0; i < values.length; i++) {
            propertyNames.add(values[i].getName());
        }

        // get the constructors sorted by longest arg length first
        List constructors = new ArrayList(classMetadata.getConstructors());
        Collections.sort(constructors, new ArgLengthComparator());

        // try to find a constructor for which we have all of the properties defined
        for (Iterator iterator = constructors.iterator(); iterator.hasNext();) {
            ConstructorMetadata constructorMetadata = (ConstructorMetadata) iterator.next();
            if (isUsableConstructor(constructorMetadata, propertyNames)) {
                return constructorMetadata;
            }
        }
        return null;
    }

    private boolean isUsableConstructor(ConstructorMetadata constructorMetadata, Set propertyNames) {
        if (constructorMetadata.getProperties().containsKey("always-use")) {
            return true;
        }

        LinkedHashMap constructorArgs = getConstructorArgs(constructorMetadata);
        if (constructorArgs == null) {
            return false;
        }

        for (Iterator argIterator = constructorArgs.entrySet().iterator(); argIterator.hasNext();) {
            Map.Entry entry = (Map.Entry) argIterator.next();
            String parameterName = (String) entry.getKey();
            Class parameterType = (Class) entry.getValue();
            // can we satify this property using a definde proeprty or default property
            if (!propertyNames.contains(parameterName) && !defaultValues.containsKey(new DefaultProperty(parameterName, parameterType))) {
                return false;
            }
        }

        return true;
    }

    private LinkedHashMap getConstructorArgs(ConstructorMetadata constructor) {
        LinkedHashMap constructorArgs = new LinkedHashMap();

        List parameterMetadata = constructor.getParameters();
        for (Iterator iterator = parameterMetadata.iterator(); iterator.hasNext();) {
            ParameterMetadata parameter = (ParameterMetadata) iterator.next();
            String name = (String) parameter.get("name");
            if (name == null) {
                return null;
            }
            constructorArgs.put(name, parameter.getType());
        }
        return constructorArgs;
    }

    private static class ArgLengthComparator implements Comparator {
        public int compare(Object o1, Object o2) {
            ConstructorMetadata constructor1 = (ConstructorMetadata) o1;
            ConstructorMetadata constructor2 = (ConstructorMetadata) o2;
            return constructor2.getParameters().size() - constructor1.getParameters().size();
        }
    }

    private static class DefaultProperty {
        private final String propertyName;
        private final Class propertyType;

        public DefaultProperty(String propertyName, Class propertyType) {
            this.propertyName = propertyName;
            this.propertyType = propertyType;
        }
        public boolean equals(Object object) {
            if (!(object instanceof DefaultProperty)) {
                return false;
            }

            DefaultProperty defaultProperty = (DefaultProperty) object;
            return propertyName.equals(defaultProperty.propertyName) && propertyType.equals(propertyType);
        }

        public int hashCode() {
            int result = 17;
            result = 37 * result + propertyName.hashCode();
            result = 37 * result + propertyType.hashCode();
            return result;
        }

        public String toString() {
            return "[" + propertyName + " " + propertyType + "]";
        }
    }

    private static final Map DEFAULT_VALUE;
    static {
        Map temp = new HashMap();
        temp.put(Boolean.TYPE, Boolean.FALSE);
        temp.put(Byte.TYPE, new Byte((byte) 0));
        temp.put(Character.TYPE, new Character((char) 0));
        temp.put(Short.TYPE, new Short((short) 0));
        temp.put(Integer.TYPE, new Integer(0));
        temp.put(Long.TYPE, new Long(0));
        temp.put(Float.TYPE, new Float(0));
        temp.put(Double.TYPE, new Double(0));

        DEFAULT_VALUE = Collections.unmodifiableMap(temp);
    }
}