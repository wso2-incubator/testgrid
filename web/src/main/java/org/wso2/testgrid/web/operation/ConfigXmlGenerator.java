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

package org.wso2.testgrid.web.operation;

import org.apache.hc.client5.http.fluent.Request;
import org.wso2.testgrid.web.bean.TestPlanRequest;

import static org.wso2.testgrid.web.utils.Constants.JENKINS_HOME;
import static org.wso2.testgrid.web.utils.Constants.JENKINS_USER_AUTH_KEY;

import java.io.IOException;

import static javax.ws.rs.core.HttpHeaders.USER_AGENT;

/**
 * Class of ConfigXmlGenerator which retrieve an existing config.xml from Jenkins and prepare new config.xml
 * to new job.
 */
public class ConfigXmlGenerator {

    /**
     * Returns the configuration file for the testPlanRequest which can be used to create new Jenkins job.
     * @param testPlanRequest request for creating new test plan.
     * @return configuration file for Jenkins job.
     */
    public String getConfigXml(TestPlanRequest testPlanRequest) throws Exception {
        String template = receiveConfigXmlFromJenkins();
        String[][] replacements = {
                {"$productName", testPlanRequest.getProductName()},
                {"$productVersion", "deprecated"}, //Since version and channel is planned to be removed from TestGrid.
                {"$productChannel", "deprecated"},
                {"$infrastructureRepo", "\"" + testPlanRequest.getInfrastructure().getRepository() + "\""},
                {"$deploymentRepo", "\"" + testPlanRequest.getDeployment().getRepository() + "\""},
                {"$scenariosRepo", "\"" + testPlanRequest.getScenarios().getRepository() + "\""},
                {"$infraLocation", "\"" + testPlanRequest.getInfrastructure().getRepository() + "\""},
                {"$deploymentLocation", "\"" + testPlanRequest.getDeployment().getRepository() + "\""},
                {"$scenariosLocation", "\"" + testPlanRequest.getScenarios().getRepository() + "\""}
        };
        String configXml = mergeTemplate(template, replacements);
        return configXml;
    }

    /**
     * Receives configuration file of the template job in Jenkins server.
     * @return configuration file of the template job.
     */
    public String receiveConfigXmlFromJenkins() throws IOException {
        try {
            return Request.Get(JENKINS_HOME + "/job/velocityTemplateFolder/job/velocityTemplateJob/config.xml")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Authorization", "Basic " + JENKINS_USER_AUTH_KEY)
                    .execute().returnContent().asString().trim();
        } catch (IOException e) {
            throw new IOException("Request failed while accessing $url: " + e.toString(), e);
        }
    }

    /**
     * Merge template terms with specific terms of the test plan request.
     * @param template configuration file of the template job.
     * @param replacements string 2D array containing terms to be replaced and with what to be replaced
     * @return configuration file suitable for the new test place request.
     */
    private String mergeTemplate(String template, String[][] replacements) {
        for (String[] replacement : replacements) {
            template = template.replace(replacement[0], replacement[1]);
        }
        return template;
    }
}
