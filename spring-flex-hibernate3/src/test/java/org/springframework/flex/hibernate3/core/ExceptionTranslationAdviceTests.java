/*
 * Copyright 2002-2009 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.flex.hibernate3.core;

import static org.mockito.Mockito.when;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.flex.core.EndpointServiceMessagePointcutAdvisor;
import org.springframework.flex.core.ExceptionTranslationAdvice;
import org.springframework.flex.core.ExceptionTranslator;
import org.springframework.util.ClassUtils;

import flex.messaging.MessageException;
import flex.messaging.endpoints.AbstractEndpoint;
import flex.messaging.messages.Message;

import junit.framework.TestCase;

public class ExceptionTranslationAdviceTests extends TestCase {

    @Mock
    private AbstractEndpoint endpoint;

    @Mock
    private Message inMessage;

    @Mock
    private Message outMessage;

    private AbstractEndpoint advisedEndpoint;

    @Override
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        ProxyFactory factory = new ProxyFactory();
        factory.setProxyTargetClass(true);
        ExceptionTranslationAdvice advice = new ExceptionTranslationAdvice();
        advice.getExceptionTranslators().add(new TestExceptionTranslator());
        factory.addAdvisor(new EndpointServiceMessagePointcutAdvisor(advice));
        factory.setTarget(this.endpoint);
        this.advisedEndpoint = (AbstractEndpoint) factory.getProxy();
    }

    public void testKnownException() {

        when(this.endpoint.serviceMessage(this.inMessage)).thenThrow(new TestException());

        try {
            this.advisedEndpoint.serviceMessage(this.inMessage);
            fail();
        } catch (MessageException ex) {
            assertEquals(TestExceptionTranslator.PROCESSED_CODE, ex.getCode());
            assertTrue(ex.getRootCause() instanceof TestException);
        }
    }

    public void testKnownNestedException() {

        MessageException wrapper = new MessageException();
        wrapper.setCode("Server.Processing");
        wrapper.setRootCause(new TestException());
        when(this.endpoint.serviceMessage(this.inMessage)).thenThrow(wrapper);

        try {
            this.advisedEndpoint.serviceMessage(this.inMessage);
            fail();
        } catch (MessageException ex) {
            assertEquals(TestExceptionTranslator.PROCESSED_CODE, ex.getCode());
            assertTrue(ex.getRootCause() instanceof TestException);
        }
    }

    public void testNormalReturnPassthrough() {

        when(this.endpoint.serviceMessage(this.inMessage)).thenReturn(this.outMessage);

        Message result = this.advisedEndpoint.serviceMessage(this.inMessage);

        assertSame(this.outMessage, result);
    }

    public void testUnknownExceptionPassthrough() {

        MessageException expected = new MessageException();
        expected.setCode("Server.Processing");
        expected.setRootCause(new RuntimeException());
        when(this.endpoint.serviceMessage(this.inMessage)).thenThrow(expected);

        try {
            this.advisedEndpoint.serviceMessage(this.inMessage);
            fail();
        } catch (MessageException ex) {
            assertSame(expected, ex);
        }
    }

    @SuppressWarnings("serial")
    public class TestException extends RuntimeException {

    }

    public class TestExceptionTranslator implements ExceptionTranslator {

        public static final String PROCESSED_CODE = "Custom.Processed";

        public boolean handles(Class<?> clazz) {
            return ClassUtils.isAssignable(TestException.class, clazz);
        }

        public MessageException translate(Throwable t) {
            if (t instanceof TestException) {
                MessageException result = new MessageException();
                result.setRootCause(t);
                result.setCode(PROCESSED_CODE);
                return result;
            }
            return null;
        }
    }

}
