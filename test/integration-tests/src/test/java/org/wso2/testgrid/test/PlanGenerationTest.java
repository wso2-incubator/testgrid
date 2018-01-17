/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.testgrid.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.testgrid.test.base.IntegrationTestBase;
import org.wso2.testgrid.test.base.Utils;

/**
 * This is a integration test class which covers test plan generation.
 *
 * @since 1.0
 */
public class PlanGenerationTest extends IntegrationTestBase {

    private static final Logger logger = LoggerFactory.getLogger(PlanGenerationTest.class);

    /**
     * Sample test case to test Test plan generation when a valid YAML is given.
     *
     * @throws Exception Throws an Exception if a error occurs during test execution
     */
    @Test public void testPlanGenerationTest() throws Exception {

        String args[] = { "-p", "WSO2IS", "-v", "5.4.0", "-c", "LTS", "-tc",
                "resources/test-configs/test-config.yaml" };
        Assert.assertEquals(Utils.executeGenTestPlan(args), 0);
    }
}
