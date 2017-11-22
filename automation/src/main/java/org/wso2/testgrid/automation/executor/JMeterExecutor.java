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

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.Summariser;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.wso2.testgrid.automation.TestAutomationException;
import org.wso2.testgrid.common.Deployment;
import org.wso2.testgrid.common.Host;
import org.wso2.testgrid.common.Port;
import org.wso2.testgrid.common.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Responsible for performing the tasks related to execution of single JMeter solution.
 *
 * @since 1.0.0
 */
public class JMeterExecutor implements TestExecutor {

    private static final Log log = LogFactory.getLog(JMeterExecutor.class);
    private String testLocation;
    private String testName;

    @Override
    public void execute(String script, Deployment deployment) throws TestAutomationException {
        StandardJMeterEngine jMeterEngine = new StandardJMeterEngine();
        if (StringUtil.isStringNullOrEmpty(testName) || StringUtil.isStringNullOrEmpty(testLocation)) {
            throw new TestAutomationException(
                    StringUtil.concatStrings("JMeter Executor not initialised properly.", "{ Test Name: ", testName,
                            ", Test Location: ", testLocation, "}"));
        }
        overrideJMeterConfig(testLocation, testName, deployment); // Override JMeter properties for current deployment.
        JMeterUtils.initLocale();

        HashTree testPlanTree;
        try {
            testPlanTree = SaveService.loadTree(new File(script));
        } catch (IOException | IllegalArgumentException e) {
            throw new TestAutomationException("Error occurred when loading test script.", e);
        }

        Summariser summariser = null;
        String summariserName = JMeterUtils.getPropDefault("summariser.name", "summary");
        if (summariserName.length() > 0) {
            summariser = new Summariser(summariserName);
        }

        Path scriptFileName = Paths.get(script).getFileName();
        if (scriptFileName == null) {
            throw new TestAutomationException(StringUtil.concatStrings("Script file ", script, " cannot be located."));
        }
        String resultFile =
                Paths.get(testLocation, "Results", "Jmeter", scriptFileName + ".xml").toAbsolutePath().toString();
        ResultCollector resultCollector = new ResultCollector(summariser);

        resultCollector.setFilename(resultFile);

        if (testPlanTree.getArray().length == 0) {
            throw new TestAutomationException("JMeter test plan is empty.");
        }
        testPlanTree.add(testPlanTree.getArray()[0], resultCollector);

        // Run JMeter Test
        jMeterEngine.configure(testPlanTree);
        jMeterEngine.run();
        jMeterEngine.exit();
    }

    @Override
    public void init(String testLocation, String testName) throws TestAutomationException {
        this.testName = testName;
        this.testLocation = testLocation;

        // Set JMeter home
        String jMeterHome = createTempDirectory(testLocation);
        JMeterUtils.setJMeterHome(jMeterHome);
    }

    /**
     * Creates a temporary directory in the given test location and returns the path of the created temp directory.
     *
     * @param testLocation location of the test scripts
     * @return path of the created temp directory
     * @throws TestAutomationException thrown when error on creating temp directory
     */
    private String createTempDirectory(String testLocation) throws TestAutomationException {
        try {
            Path tempDirectoryPath = Paths.get(testLocation).resolve("temp");
            Path binDirectoryPath = tempDirectoryPath.resolve("bin");
            Files.createDirectories(binDirectoryPath);

            // Copy properties files
            copyResourceFile(binDirectoryPath, "saveservice.properties");
            copyResourceFile(binDirectoryPath, "upgrade.properties");

            tempDirectoryPath.toFile().deleteOnExit();

            // Delete file on exit
            FileUtils.forceDeleteOnExit(new File(tempDirectoryPath.toAbsolutePath().toString()));

            return tempDirectoryPath.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new TestAutomationException(StringUtil
                    .concatStrings("Error occurred when creating temporary directory in ", testLocation), e);
        }
    }

    /**
     * Copies the given resource file to the given path.
     *
     * @param fileCopyPath path in which the file should be copied to
     * @param fileName     name of the file to be copied
     * @throws TestAutomationException thrown when error on copying the file
     */
    private void copyResourceFile(Path fileCopyPath, String fileName) throws TestAutomationException {
        Path filePath = fileCopyPath.resolve(fileName);
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (!Files.exists(filePath)) {
                Files.copy(inputStream, filePath);
            }
        } catch (IOException e) {
            throw new TestAutomationException(StringUtil
                    .concatStrings("Error occurred when copying file ", filePath.toAbsolutePath().toString()), e);
        }
    }

    /**
     * Overrides the JMeter properties with the properties required for the current deployment.
     *
     * @param testLocation directory location of the tests
     * @param testName     test name
     * @param deployment   deployment details of the current pattern
     */

    private void overrideJMeterConfig(String testLocation, String testName, Deployment deployment) {
        Path path = Paths.get(testLocation, "JMeter", testName, "src", "test", "resources", "user.properties");
        if (!Files.exists(path)) {
            log.info("JMeter user.properties file not specified - proceeding with JMeter default properties.");
            return;
        }
        JMeterUtils.loadJMeterProperties(path.toAbsolutePath().toString());
        for (Host host : deployment.getHosts()) {
            JMeterUtils.setProperty(host.getLabel(), host.getIp());
            for (Port port : host.getPorts()) {
                JMeterUtils.setProperty(port.getProtocol(), String.valueOf(port.getPortNumber()));
            }
        }
    }
}
