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

package org.wso2.testgrid.tests;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.wso2.testgrid.TestProperties;
import org.wso2.testgrid.common.EmailUtils;
import org.wso2.testgrid.common.HostValidator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class TestPhases {

    private static final Logger logger = LoggerFactory.getLogger(TestPhases.class);
    private TestProperties testProperties;

    @BeforeTest
    public void init() {

        testProperties = new TestProperties();
        HostnameVerifier allHostsValid = new HostValidator();
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }

    @Test(dataProvider = "jobs")
    public void buildTriggerTest(String jobName) {

        JenkinsJob currentBuild = null;
        HttpsURLConnection connection = null;
        logger.info("Running tests for the job : " + jobName);
        try {
            String jenkinsToken = testProperties.jenkinsToken;
            URL buildTriggerUrl = new URL(TestProperties.jenkinsUrl + "/job/" + jobName + "/build?token=" +
                                          jenkinsToken);
            URL buildStatusUrl =
                    new URL(TestProperties.jenkinsUrl + "/job/" + jobName + "/lastBuild/api/json");

            JenkinsJob jenkinsJob = getLastJob(buildStatusUrl);
            logger.info("Job status for job ID : " + jenkinsJob.id + " : " + jenkinsJob.status);

            connection = (HttpsURLConnection) buildTriggerUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);

            SSLSocketFactory sslSocketFactory = createSslSocketFactory();
            connection.setSSLSocketFactory(sslSocketFactory);

            int response = connection.getResponseCode();
            if (response == 201) {
                logger.info("build Triggered");
            } else {
                Assert.fail("Phase 1 build couldn't be triggered. Response code : " + response);
            }

            jenkinsJob = getLastJob(buildStatusUrl);

            while (jenkinsJob.building) {
                jenkinsJob = getLastJob(buildStatusUrl);
                logger.info(jobName + " #(" + jenkinsJob.id + ") building ");
                TimeUnit.SECONDS.sleep(2);
            }

            currentBuild = getLastJob(buildStatusUrl);
            Assert.assertEquals(currentBuild.status, "SUCCESS");

            logger.info("Checking the Email content of " + jobName);
            EmailUtils emailUtils = connectToEmail();
            testTextContained(emailUtils, jenkinsJob.id, jobName);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        // Validating the logs
        logTest(jobName, currentBuild.id);
        summaryTest(jobName, currentBuild.id);
    }

    private void logTest(String jobName, String buildID) {

        try {
            validateLog(getTestPlanID(jobName, buildID));
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

    }

    private void summaryTest(String jobName, String buildID) {

        try {
            testSummaryValidate(getTestPlanID(jobName, buildID), jobName);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    private void validateLog(String testPlan) throws Exception {

        String webPage = testProperties.tgDashboardApiUrl + "/test-plans/log/" + testPlan;

        URL url = new URL(webPage);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", testProperties.tgApiToken);
        SSLSocketFactory sslSocketFactory = createSslSocketFactory();
        connection.setSSLSocketFactory(sslSocketFactory);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            if (response.toString().contains(testPlan)) {
                logger.info("Correct log is found");
            } else {
                Assert.fail("Correct log is not found");
            }
        }
    }

    private String getTestPlanID(String jobName, String buildNo) throws Exception {

        URL url = new URL(testProperties.jenkinsUrl + "/job/" + jobName + "/" + buildNo +
                          "/consoleText");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoOutput(true);
        SSLSocketFactory sslSocketFactory = createSslSocketFactory();
        connection.setSSLSocketFactory(sslSocketFactory);
        String testplan;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            String patternString = ".*Preparing workspace for testplan.*";

            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher;
            String rowTestPlanID = "";
            while ((inputLine = in.readLine()) != null) {
                matcher = pattern.matcher(inputLine);
                if (matcher.find()) {
                    rowTestPlanID = inputLine;
                    break;
                }
            }
            testplan = rowTestPlanID.split(":")[5].replaceAll("\\s", "");
        }
        connection.disconnect();
        return testplan;
    }

    private void testTextContained(EmailUtils emailUtils, String buildNo, String jobName) {

        try {
            String emailSubject = "'" + jobName + "' Test Results! #(" + buildNo + ")";
            Message[] emails = null;
            for (int i = 0; i < 30; i++) {
                emails = emailUtils.getMessagesBySubject(emailSubject, false, 100);
                if (emails.length != 0) {
                    logger.info("EMAIL Found");
                    break;
                }
                TimeUnit.SECONDS.sleep(2);
                logger.info("Waiting for email : " + emailSubject);
            }
            Message email = emails[0];
            Assert.assertTrue(emailUtils.isTextInMessage(email, jobName + " integration test Results!"),
                    jobName + " integration test Results!");
            logger.info("Email received on " + email.getReceivedDate());
        } catch (ArrayIndexOutOfBoundsException e) {
            Assert.fail("Email not received for the build " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error occurred while asserting the email ", e);
            Assert.fail("Error occurred while asserting the email text " + e.getMessage());
        }
    }

    private static EmailUtils connectToEmail() {

        try {
            //gmail need to alow less secure apps
            EmailUtils emailUtils = new EmailUtils(TestProperties.email, TestProperties.emailPassword,
                    "smtp.gmail.com", EmailUtils.EmailFolder.INBOX);
            return emailUtils;
        } catch (MessagingException e) {
            Assert.fail(e.getMessage());
            return null;
        }
    }

    private void testSummaryValidate(String testplan, String jobName) throws Exception {

        URL url = new URL(TestProperties.tgDashboardApiUrl + "/test-plans/test-summary/" + testplan);

        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setDoOutput(true);
        con.setRequestProperty("Authorization", "test");
        SSLSocketFactory sslSocketFactory = createSslSocketFactory();
        con.setSSLSocketFactory(sslSocketFactory);
        StringBuffer response;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String inputLine;
            response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }

        JSONObject responseObj = new JSONObject(response.toString());
        responseObj = responseObj.getJSONArray("scenarioSummaries").getJSONObject(0);

        if (jobName.equals("Phase-1")) {
            Assert.assertEquals(responseObj.getInt("totalSuccess"), 541);
        } else {
            Assert.assertEquals(responseObj.getInt("totalSuccess"), 1);
        }
        Assert.assertEquals(responseObj.getInt("totalFail"), 0);
        if (jobName.equals("Phase-1")) {
            Assert.assertEquals(responseObj.getString("scenarioDescription"), "Test-Phase-1");
        } else {
            Assert.assertEquals(responseObj.getString("scenarioDescription"), "product-ei-scenarios");
        }
        con.disconnect();
    }

    private JenkinsJob getLastJob(URL url) throws Exception {

        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setDoOutput(true);
        SSLSocketFactory sslSocketFactory = createSslSocketFactory();
        con.setSSLSocketFactory(sslSocketFactory);

        StringBuffer response;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String inputLine;
            response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        JSONObject responseObj = new JSONObject(response.toString());
        JenkinsJob jenkinsJob;
        if (responseObj.getBoolean("building")) {
            jenkinsJob = new JenkinsJob(responseObj.getString("id"), responseObj.getBoolean("building"));
        } else {
            jenkinsJob = new JenkinsJob(responseObj.getString("result"), responseObj.getString("id"),
                    responseObj.getBoolean("building"));
        }
        con.disconnect();
        return jenkinsJob;
    }

    /**
     * Bean to capture Jenkins Job information.
     */
    public static class JenkinsJob {

        public String status;
        public String id;
        public boolean building;

        public JenkinsJob(String status, String id, Boolean building) {

            this.status = status;
            this.id = id;
            this.building = building;
        }

        private JenkinsJob(String id, Boolean building) {
            this(null, id, building);
        }
    }

    /**
     * This method is to bypass SSL verification
     *
     * @return SSL socket factory that by will bypass SSL verification
     * @throws Exception java.security exception is thrown in an issue with SSLContext
     */
    private static SSLSocketFactory createSslSocketFactory() throws Exception {

        TrustManager[] byPassTrustManagers = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {

                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType) {

            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {

            }
        }};
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, byPassTrustManagers, new SecureRandom());
        return sslContext.getSocketFactory();
    }

    @DataProvider(name = "jobs")
    public static Object[][] jobs() {
        return new Object[][]{{"Phase-1"}, {"Phase-2"}};
    }
}
