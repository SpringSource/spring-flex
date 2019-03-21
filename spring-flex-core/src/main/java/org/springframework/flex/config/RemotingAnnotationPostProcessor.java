/*
 * Copyright 2002-2011 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.flex.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.flex.remoting.RemotingDestination;
import org.springframework.flex.remoting.RemotingDestinationExporter;
import org.springframework.flex.remoting.RemotingExclude;
import org.springframework.flex.remoting.RemotingInclude;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link BeanFactoryPostProcessor} implementation that searches the {@link BeanFactory} for beans annotated with
 * {@link RemotingDestination} and adds a corresponding {@link RemotingDestinationExporter} bean definition according to
 * the attributes of the {@link RemotingDestination} annotation and any methods found to be marked with either the
 * {@link RemotingInclude} or {@link RemotingExclude} annotation.
 * 
 * <p>
 * This processor will be enabled automatically when using the message-broker tag of the xml config namespace.
 * 
 * @author Jeremy Grelle
 */
public class RemotingAnnotationPostProcessor implements BeanFactoryPostProcessor {

    private static final Log log = LogFactory.getLog(RemotingAnnotationPostProcessor.class);

    // --------------------------- Bean Configuration Properties -------------//
    private static final String MESSAGE_BROKER_PROPERTY = "messageBroker";

    private static final String SERVICE_PROPERTY = "service";

    private static final String DESTINATION_ID_PROPERTY = "destinationId";

    private static final String CHANNELS_PROPERTY = "channels";

    private static final String INCLUDE_METHODS_PROPERTY = "includeMethods";

    private static final String EXCLUDE_METHODS_PROPERTY = "excludeMethods";

    private static final String SERVICE_ADAPTER_PROPERTY = "serviceAdapter";

    /**
     * 
     * {@inheritDoc}
     */
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

        Set<RemotingDestinationMetadata> remoteBeans = findRemotingDestinations(beanFactory);

        if (remoteBeans.size() > 0) {
            Assert.isInstanceOf(BeanDefinitionRegistry.class, beanFactory,
                "In order for services to be exported via the @RemotingDestination annotation, the current BeanFactory must be a BeanDefinitionRegistry.");
        }

        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

        for (RemotingDestinationMetadata remotingDestinationConfig : remoteBeans) {

            BeanDefinitionBuilder exporterBuilder = BeanDefinitionBuilder.rootBeanDefinition(RemotingDestinationExporter.class);
            exporterBuilder.getRawBeanDefinition().setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

            RemotingDestination remotingDestination = remotingDestinationConfig.getRemotingDestination();

            String messageBrokerId = StringUtils.hasText(remotingDestination.messageBroker()) ? remotingDestination.messageBroker()
                : BeanIds.MESSAGE_BROKER;
            String destinationId = StringUtils.hasText(remotingDestination.value()) ? remotingDestination.value()
                : remotingDestinationConfig.getBeanName();
            
            String[] channels = null;
            for (String channelValue : remotingDestination.channels()) {
                channelValue = beanFactory.resolveEmbeddedValue(channelValue);
                String[] parsedChannels = StringUtils.trimArrayElements(StringUtils.commaDelimitedListToStringArray(channelValue));
                channels = StringUtils.mergeStringArrays(channels, parsedChannels);
            }
            
            exporterBuilder.addPropertyReference(MESSAGE_BROKER_PROPERTY, messageBrokerId);
            exporterBuilder.addPropertyValue(SERVICE_PROPERTY, remotingDestinationConfig.getBeanName());
            exporterBuilder.addDependsOn(remotingDestinationConfig.getBeanName());
            exporterBuilder.addPropertyValue(DESTINATION_ID_PROPERTY, destinationId);
            exporterBuilder.addPropertyValue(CHANNELS_PROPERTY, channels);
            exporterBuilder.addPropertyValue(INCLUDE_METHODS_PROPERTY, remotingDestinationConfig.getIncludeMethods());
            exporterBuilder.addPropertyValue(EXCLUDE_METHODS_PROPERTY, remotingDestinationConfig.getExcludeMethods());
            exporterBuilder.addPropertyValue(SERVICE_ADAPTER_PROPERTY, remotingDestination.serviceAdapter());

            BeanDefinitionReaderUtils.registerWithGeneratedName(exporterBuilder.getBeanDefinition(), registry);
        }

    }

    /**
     * Helper that searches the BeanFactory for beans annotated with @RemotingDestination, being careful not to force
     * eager creation of the beans if it can be avoided.
     * 
     * @param beanFactory the BeanFactory to search
     * @return a set of collected RemotingDestinationMetadata
     */
    private Set<RemotingDestinationMetadata> findRemotingDestinations(ConfigurableListableBeanFactory beanFactory) {
        Set<RemotingDestinationMetadata> remotingDestinations = new HashSet<RemotingDestinationMetadata>();
        Set<String> beanNames = new HashSet<String>();
        beanNames.addAll(Arrays.asList(beanFactory.getBeanDefinitionNames()));
        if (beanFactory.getParentBeanFactory() instanceof ListableBeanFactory) {
            beanNames.addAll(Arrays.asList(((ListableBeanFactory)beanFactory.getParentBeanFactory()).getBeanDefinitionNames()));
        }
        for (String beanName : beanNames) {
            if (beanName.startsWith("scopedTarget.")) {
                continue;
            }
            RemotingDestination remotingDestination = null;
            BeanDefinition bd = beanFactory.getMergedBeanDefinition(beanName);
            if (bd.isAbstract() || bd.isLazyInit()) {
                continue;
            }
            if (bd instanceof AbstractBeanDefinition) {
                AbstractBeanDefinition abd = (AbstractBeanDefinition) bd;
                if (abd.hasBeanClass()) {
                    Class<?> beanClass = abd.getBeanClass();
                    remotingDestination = AnnotationUtils.findAnnotation(beanClass, RemotingDestination.class);
                    if (remotingDestination != null) {
                        remotingDestinations.add(new RemotingDestinationMetadata(remotingDestination, beanName, beanClass));
                        continue;
                    }
                }
            }
            Class<?> handlerType = beanFactory.getType(beanName);
            if (handlerType != null) {
                remotingDestination = AnnotationUtils.findAnnotation(handlerType, RemotingDestination.class);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Could not get type of bean '" + beanName + "' from bean factory.");
                }
            }
            if (remotingDestination != null) {
                remotingDestinations.add(new RemotingDestinationMetadata(remotingDestination, beanName, handlerType));
            } 
        }
        return remotingDestinations;
    }

}
