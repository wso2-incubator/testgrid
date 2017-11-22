/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.testgrid.automation.executor;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.testgrid.automation.TestAutomationException;

/**
 * Test class to test the functionality of {@link TestExecutorFactory}.
 *
 * @since 1.0.0
 */
public class TestExecutorFactoryTest {

    @Test(description = "Tests whether the JMeterExecutor is returned for the given type JMETER")
    public void testTypeJMeterTest() throws TestAutomationException {
        TestExecutor testExecutor = TestExecutorFactory.getTestExecutor("JMETER");
        Assert.assertNotNull(testExecutor);
        Assert.assertTrue(testExecutor instanceof JMeterExecutor);
    }

    @Test(description = "Tests whether the TestNgExecutor is returned for the given type TESTNG")
    public void testTypeTestNGTest() throws TestAutomationException {
        TestExecutor testExecutor = TestExecutorFactory.getTestExecutor("TESTNG");
        Assert.assertNotNull(testExecutor);
        Assert.assertTrue(testExecutor instanceof TestNgExecutor);
    }

    @Test(expectedExceptions = TestAutomationException.class,
          expectedExceptionsMessageRegExp = "Test executor for test type OTHER not implemented.",
          description = "Tests whether an exception is thrown for other types")
    public void testTypeUnknownTest() throws TestAutomationException {
        TestExecutorFactory.getTestExecutor("OTHER");
    }
}
