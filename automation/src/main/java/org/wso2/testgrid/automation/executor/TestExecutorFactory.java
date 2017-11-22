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

import org.wso2.testgrid.automation.TestAutomationException;
import org.wso2.testgrid.common.constants.TestGridConstants;
import org.wso2.testgrid.common.util.StringUtil;

/**
 * Factory class to return the specific test executor for the test type.
 *
 * @since 1.0.0
 */
public class TestExecutorFactory {

    /**
     * Returns the specific test executor.
     *
     * @param testType Test type
     * @return test executor for the given test type
     */
    public static TestExecutor getTestExecutor(String testType) throws TestAutomationException {
        switch (testType) {
            case TestGridConstants.TEST_TYPE_JMETER:
                return new JMeterExecutor();
            case TestGridConstants.TEST_TYPE_TESTNG:
                return new TestNgExecutor();
            default:
                throw new TestAutomationException(StringUtil.concatStrings("Test executor for test type ", testType,
                        " not implemented."));
        }
    }
}
