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
 *
 */

package org.wso2.testgrid.reporting;

import com.amazonaws.auth.PropertiesFileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.testgrid.common.Product;
import org.wso2.testgrid.common.Status;
import org.wso2.testgrid.common.TestGridConstants;
import org.wso2.testgrid.common.TestPlan;
import org.wso2.testgrid.common.config.ConfigurationContext;
import org.wso2.testgrid.common.config.ConfigurationContext.ConfigurationProperties;
import org.wso2.testgrid.common.config.PropertyFileReader;
import org.wso2.testgrid.common.infrastructure.InfrastructureParameter;
import org.wso2.testgrid.common.infrastructure.InfrastructureValueSet;
import org.wso2.testgrid.common.util.S3StorageUtil;
import org.wso2.testgrid.common.util.TestGridUtil;
import org.wso2.testgrid.dao.TestGridDAOException;
import org.wso2.testgrid.dao.uow.InfrastructureParameterUOW;
import org.wso2.testgrid.dao.uow.TestPlanUOW;
import org.wso2.testgrid.reporting.model.email.TPResultSection;
import org.wso2.testgrid.reporting.summary.InfrastructureBuildStatus;
import org.wso2.testgrid.reporting.summary.InfrastructureSummaryReporter;
import org.wso2.testgrid.reporting.surefire.SurefireReporter;
import org.wso2.testgrid.reporting.surefire.TestResult;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static org.wso2.testgrid.common.TestGridConstants.HTML_LINE_SEPARATOR;
import static org.wso2.testgrid.common.TestGridConstants.TEST_PLANS_URI;

/**
 * This class is responsible for process and generate all the content for the email-report for TestReportEngine.
 * The report will consist of base details such as product status, git build details, as well as per test-plan
 * (per infra-combination) details such as test-plan log, test-plan infra combination.
 */
public class EmailReportProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EmailReportProcessor.class);
    private static final int MAX_DISPLAY_TEST_COUNT = 20;
    private final InfrastructureSummaryReporter infrastructureSummaryReporter;
    private TestPlanUOW testPlanUOW;
    private InfrastructureParameterUOW infrastructureParameterUOW;

    public EmailReportProcessor() {
        this.testPlanUOW = new TestPlanUOW();
        this.infrastructureParameterUOW = new InfrastructureParameterUOW();
        this.infrastructureSummaryReporter = new InfrastructureSummaryReporter(infrastructureParameterUOW);
    }

    /**
     * This is created with default access modifier for the purpose of unit tests.
     *
     * @param testPlanUOW the TestPlanUOW
     */
    EmailReportProcessor(TestPlanUOW testPlanUOW, InfrastructureParameterUOW infrastructureParameterUOW) {
        this.testPlanUOW = testPlanUOW;
        this.infrastructureParameterUOW = infrastructureParameterUOW;
        this.infrastructureSummaryReporter = new InfrastructureSummaryReporter(infrastructureParameterUOW);
    }

    /**
     * Populates test-plan result sections in the report considering the latest test-plans of the product.
     *
     * @param product product needing the results
     * @return list of test-plan sections
     */
    public List<TPResultSection> generatePerTestPlanSection(Product product, List<TestPlan> testPlans)
            throws ReportingException {
        List<TPResultSection> perTestPlanList = new ArrayList<>();
        String testGridHost = ConfigurationContext.getProperty(ConfigurationContext.
                ConfigurationProperties.TESTGRID_HOST);
        String productName = product.getName();
        SurefireReporter surefireReporter = new SurefireReporter();
        for (TestPlan testPlan : testPlans) {
            if (testPlan.getStatus().equals(Status.SUCCESS)) {
                logger.info(String.format("Test plan ,%s, status is set to success. Not adding to email report. "
                        + "Infra combination: %s", testPlan.getId(), testPlan.getInfraParameters()));
                continue;
            }
            String deploymentPattern = testPlan.getDeploymentPattern().getName();
            String testPlanId = testPlan.getId();
            final String infraCombination = testPlan.getInfraParameters();
            final String dashboardLink = String.join("/", testGridHost, productName, deploymentPattern,
                    TEST_PLANS_URI, testPlanId);

            final TestResult report = surefireReporter.getReport(testPlan);
            if (logger.isDebugEnabled()) {
                logger.debug("Test results of test plan '" + testPlan.getId() + "': " + report);
            }

            if ("?".equals(report.getTotalTests()) || "0".equals(report.getTotalTests())
                    || report.getTotalTests().isEmpty()) {
                final Path reportPath = TestGridUtil.getSurefireReportsDir(testPlan);
                logger.error("Integration-test log file does not exist at '" + reportPath
                        + "' for test-plan: " + testPlan);
            }

            List<TestResult.TestCaseResult> failureTests = getTrimmedTests(report.getFailureTests(),
                    MAX_DISPLAY_TEST_COUNT);
            List<TestResult.TestCaseResult> errorTests = getTrimmedTests(report.getErrorTests(),
                    MAX_DISPLAY_TEST_COUNT);

            TPResultSection testPlanResultSection = new TPResultSection.TPResultSectionBuilder(
                    infraCombination, deploymentPattern, testPlan.getStatus())
                    .jobName(productName)
                    .dashboardLink(dashboardLink)
                    .failureTests(failureTests)
                    .errorTests(errorTests)
                    .totalTests(report.getTotalTests())
                    .totalFailures(report.getTotalFailures())
                    .totalErrors(report.getTotalErrors())
                    .totalSkipped(report.getTotalSkipped())
                    .build();
            perTestPlanList.add(testPlanResultSection);
        }
        return perTestPlanList;
    }

    private List<TestResult.TestCaseResult> getTrimmedTests(List<TestResult.TestCaseResult> tests,
            int maxDisplayTestCount) {
        final int actualCount = tests.size();
        int displayCount = tests.size();
        displayCount = displayCount < maxDisplayTestCount ? displayCount : maxDisplayTestCount;

        tests = new ArrayList<>(tests.subList(0, displayCount));
        if (displayCount < actualCount) {
            TestResult.TestCaseResult continuation = new TestResult.TestCaseResult();
            continuation.setClassName("...");
            TestResult.TestCaseResult allTestsInfo = new TestResult.TestCaseResult();
            allTestsInfo.setClassName("(view complete list of tests (" + actualCount + ") in testgrid-live..)");
            tests.add(continuation);
            tests.add(continuation);
            tests.add(allTestsInfo);
        }
        return tests;
    }

    /**
     * Returns the current status of the product.
     *
     * @param product product
     * @return current status of the product
     */
    public Status getProductStatus(Product product) {
        return testPlanUOW.getCurrentStatus(product);
    }

    /**
     * Returns the Git build information of the latest build of the product.
     * This will consist of git location and git revision used to build the distribution.
     *
     * @param product product
     * @return Git build information
     */
    public String getGitBuildDetails(Product product, List<TestPlan> testPlans) {
        StringBuilder stringBuilder = new StringBuilder();
        //All the test-plans are executed from the same git revision. Thus git build details are similar across them.
        //Therefore we refer the fist test-plan's git-build details.
        TestPlan testPlan = testPlans.get(0);
        String gitRevision = "";
        String gitLocation = "";

        Properties properties = getOutputPropertiesFile(testPlan);

        if (!properties.isEmpty()) {
            gitRevision = properties.getProperty(PropertyFileReader.BuildOutputProperties.GIT_REVISION.toString());
            gitLocation = properties.getProperty(PropertyFileReader.BuildOutputProperties.GIT_LOCATION.toString());
        }

        if (gitLocation.isEmpty()) {
            logger.error("Git location received as null/empty for test plan with id " + testPlan.getId());
            stringBuilder.append("Git location: Unknown!");
        } else {
            stringBuilder.append("Git location: ").append(gitLocation);
        }
        stringBuilder.append(HTML_LINE_SEPARATOR);
        if (gitRevision.isEmpty()) {
            logger.error("Git revision received as null/empty for test plan with id " + testPlan.getId());
            stringBuilder.append("Git revision: Unknown!");
        } else {
            stringBuilder.append("Git revision: ").append(gitRevision);
        }
        return stringBuilder.toString();
    }

    /**
     * Check if the latest run of a certain product include failed tests.
     *
     * @param testPlans List of test plans
     * @return if test-failures exists or not
     */
    public boolean hasFailedTests(List<TestPlan> testPlans) {
        for (TestPlan testPlan : testPlans) {
            if (testPlan.getStatus().equals(Status.FAIL)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @see InfrastructureSummaryReporter#getSummaryTable(List)
     * @param testPlans the test plans for which we need to generate the summary
     * @return summary table
     */
    public Map<String, InfrastructureBuildStatus> getSummaryTable(List<TestPlan> testPlans)
            throws TestGridDAOException {
        return infrastructureSummaryReporter.getSummaryTable(testPlans);
    }

    /**
     * Get the tested infrastructures as a html string content.
     *
     * @param testPlans executed test plans
     */
    public String getTestedInfrastructures(List<TestPlan> testPlans) throws TestGridDAOException {

        final Set<InfrastructureValueSet> infrastructureValueSet = infrastructureParameterUOW.getValueSet();
        Set<InfrastructureParameter> infraParams = new HashSet<>();
        for (TestPlan testPlan : testPlans) {
            infraParams.addAll(TestGridUtil.
                    getInfraParamsOfTestPlan(infrastructureValueSet, testPlan));
        }
        final Map<String, List<InfrastructureParameter>> infraMap = infraParams.stream()
                .collect(Collectors.groupingBy(InfrastructureParameter::getType));
        StringJoiner infraStr = new StringJoiner(", <br/>");
        for (Map.Entry<String, List<InfrastructureParameter>> entry : infraMap.entrySet()) {
            String s = "&nbsp;&nbsp;<b>" + entry.getKey() + "</b> : " + entry.getValue().stream()
                    .map(InfrastructureParameter::getName).collect(Collectors.joining(", "));
            infraStr.add(s);
        }
        return infraStr.toString();
    }

    public Map<String, String> getErroneousInfrastructures(List<TestPlan> testPlans) throws TestGridDAOException {
        Map<String, String> erroneousInfraMap = new HashMap<>();
        final Set<InfrastructureValueSet> infrastructureValueSet = infrastructureParameterUOW.getValueSet();
        Set<InfrastructureParameter> infraParams;
        Map<String, List<InfrastructureParameter>> infraMap;
        String infraStr;
        for (TestPlan testPlan : testPlans) {
            String logDownloadPath = TestGridUtil.getDashboardURLFor(testPlan);
            if (testPlan.getStatus() != Status.SUCCESS && testPlan.getStatus() != Status.FAIL) {
                infraParams = new HashSet<>(TestGridUtil.
                        getInfraParamsOfTestPlan(infrastructureValueSet, testPlan));
                infraMap = infraParams.stream().collect(Collectors.groupingBy(InfrastructureParameter::getType));
                infraStr = infraMap.entrySet().stream()
                        .map(entry -> entry.getValue().stream().map(InfrastructureParameter::getName)
                                .collect(Collectors.joining(", ")))
                        .collect(Collectors.joining(", "));
                erroneousInfraMap.put(infraStr, logDownloadPath);
            }
        }
        return erroneousInfraMap;
    }

    /**
     * Returns the Properties in output.properties located in AWS S3 bucket.
     *
     * @param testPlan test plan to read the outputs from
     * @return Properties in output.properties
     * @throws IOException if exception occurs when loading properties
     */
    private Properties getOutputPropertiesFile (TestPlan testPlan) {
        String s3DatabucketDir = S3StorageUtil.deriveS3DatabucketDir(testPlan);
        Path configFilePath = TestGridUtil.getConfigFilePath();
        String bucketKey = ConfigurationContext.getProperty(ConfigurationProperties.AWS_S3_BUCKET_NAME);
        String awsBucketRegion = ConfigurationContext.getProperty(ConfigurationProperties.AWS_REGION_NAME);

        String outputPropertyFilePath = Paths.get(s3DatabucketDir,
                TestGridConstants.TESTGRID_SCENARIO_OUTPUT_PROPERTY_FILE).toString();
        logger.info("Output property file path in S3 bucket is : " +
                Paths.get(bucketKey, outputPropertyFilePath).toString());

        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(awsBucketRegion)
                .withCredentials(new PropertiesFileCredentialsProvider(configFilePath.toString()))
                .build();
        S3Object s3object = s3Client.getObject(bucketKey, outputPropertyFilePath);
        Properties properties = new Properties();

        try (InputStreamReader inputStreamReader = new InputStreamReader(
                s3object.getObjectContent(), StandardCharsets.UTF_8)) {
            properties.load(inputStreamReader);
        } catch (IOException e) {
            logger.error("Error while reading properties from output.properties.", e);
        }
        return properties;
    }
}
