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
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.testgrid.infrastructure.aws;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.testgrid.common.Deployment;
import org.wso2.testgrid.common.Host;
import org.wso2.testgrid.common.Infrastructure;
import org.wso2.testgrid.common.Script;
import org.wso2.testgrid.common.exception.TestGridInfrastructureException;
import org.wso2.testgrid.common.util.StringUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible for creating the AWS infrastructure.
 *
 * @since 1.0.0
 */
public class AWSManager {

    private Infrastructure infra;
    private static final Log log = LogFactory.getLog(AWSManager.class);

    /**
     * This constructor creates AWS deployer object and validate AWS related environment variables are present.
     *
     * @param awsKeyVariableName    Environment variable name for AWS ACCESS KEY.
     * @param awsSecretVariableName Environment variable name for AWS SECRET KEY.
     * @throws TestGridInfrastructureException Throws exception when environment variables are not set.
     */
    public AWSManager(String awsKeyVariableName, String awsSecretVariableName) throws TestGridInfrastructureException {
        String awsIdentity = System.getenv(awsKeyVariableName);
        String awsSecret = System.getenv(awsSecretVariableName);
        if (StringUtil.isStringNullOrEmpty(awsIdentity) || StringUtil.isStringNullOrEmpty(awsSecret)) {
            throw new TestGridInfrastructureException("AWS Credentials must be set as environment variables");
        }
    }

    /**
     * This method initiates creating infrastructure through CLoudFormation.
     *
     * @param script       Script object containing the CF details.
     * @param infraRepoDir Path of TestGrid home location in file system as a String.
     * @return Returns a  Deployment object with created infrastructure details.
     * @throws TestGridInfrastructureException When there is an error with CloudFormation script.
     */
    public Deployment createInfrastructure(Script script, String infraRepoDir)throws TestGridInfrastructureException {
        String cloudFormationName = script.getName();
        AmazonCloudFormation stackbuilder = AmazonCloudFormationClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(infra.getRegion())
                .build();

        CreateStackRequest stackRequest = new CreateStackRequest();
        stackRequest.setStackName(cloudFormationName);
        try {
            String file = new String(Files.readAllBytes(Paths.get(infraRepoDir, this.infra.getName(),
                    "AWS", "Scripts", script.getFilePath())), StandardCharsets.UTF_8);
            stackRequest.setTemplateBody(file);
            stackbuilder.createStack(stackRequest);
            if (log.isDebugEnabled()) {
                log.info("Stack configuration created for name " + cloudFormationName);
            }
            waitForAWSProcess(stackbuilder, cloudFormationName);
            DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
            describeStacksRequest.setStackName(cloudFormationName);
            DescribeStacksResult describeStacksResult = stackbuilder.describeStacks(describeStacksRequest);
            List<Host> hosts = new ArrayList<>();
            for (Stack st : describeStacksResult.getStacks()) {
                for (Output output : st.getOutputs()) {
                    if ("PublicDNS".equals(output.getOutputKey())) {
                        Host host = new Host();
                        host.setIp(output.getOutputValue());
                        hosts.add(host);
                    }
                }
            }
            Deployment deployment = new Deployment();
            deployment.setHosts(hosts);
            log.info("Created a CloudFormation Stack with the name :" + stackRequest.getStackName());
            return deployment;
        } catch (InterruptedException e) {
            throw new TestGridInfrastructureException("Error occured while waiting for " +
                    "CloudFormation Stack creation", e);
        } catch (IOException e) {
            throw new TestGridInfrastructureException("Error occured while Reading CloudFormation script", e);
        }
    }

    /**
     * This method waits for completion of AWS process and generate result depending on
     * the result code.
     *
     * @param stackBuilder AWS CF builder object.
     * @param stackName    The stack name to perform the waiting upon.
     * @return true or false depending on the result.
     * @throws InterruptedException            when an Interrupt occurs while waiting for CF result.
     * @throws TestGridInfrastructureException when an error occurs while reading the cf template file.
     */
    private boolean waitForAWSProcess(AmazonCloudFormation stackBuilder, String stackName) throws InterruptedException,
            TestGridInfrastructureException {
        DescribeStacksRequest wait = new DescribeStacksRequest();
        wait.setStackName(stackName);
        //the status of the operation
        boolean completed = false;
        //result of the operation
        boolean successful = false;
        log.info("Waiting ..");
        while (!completed) {
            List<Stack> stacks = stackBuilder.describeStacks(wait).getStacks();
            if (stacks.isEmpty()) {
                throw new TestGridInfrastructureException("There is no stack for the name :" + stackName);
            } else {
                for (Stack stack : stacks) {
                    if (StackStatus.CREATE_COMPLETE.toString().equals(stack.getStackStatus()) ||
                            StackStatus.DELETE_COMPLETE.toString().equals(stack.getStackStatus())) {
                        completed = true;
                        successful = true;
                    } else if (StackStatus.CREATE_FAILED.toString().equals(stack.getStackStatus()) ||
                            StackStatus.ROLLBACK_FAILED.toString().equals(stack.getStackStatus()) ||
                            StackStatus.ROLLBACK_COMPLETE.toString().equals(stack.getStackStatus())) {
                        completed = true;
                        successful = false;
                    } else if (StackStatus.CREATE_IN_PROGRESS.toString().equals(stack.getStackStatus()) ||
                            StackStatus.DELETE_IN_PROGRESS.toString().equals(stack.getStackStatus()) ||
                            StackStatus.ROLLBACK_IN_PROGRESS.toString().equals(stack.getStackStatus()) ||
                            StackStatus.REVIEW_IN_PROGRESS.toString().equals(stack.getStackStatus())) {
                        completed = false;
                    }
                }
            }
            //if the operation is not complete then wait 5 seconds and check again.
            if (!completed) {
                Thread.sleep(5000);
            }
        }
        return successful;
    }

    /**
     * Initialize the manager with an infrastructure object.
     *
     * @param infrastructure infrastructure details.
     */
    public void init(Infrastructure infrastructure) {
        this.infra = infrastructure;
    }

    /**
     * This method destroys the CF infrastructure given the stack name.
     *
     * @param script Script object with the CloudFormation details.
     * @return true or false to indicate the result of destroy operation.
     * @throws TestGridInfrastructureException when AWS error occurs in deletion process.
     * @throws InterruptedException            when there is an interruption while waiting for the result.
     */
    public boolean destroyInfrastructure(Script script) throws TestGridInfrastructureException, InterruptedException {
        String cloudFormationName = script.getName();
        AmazonCloudFormation stackdestroy = AmazonCloudFormationClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(infra.getRegion())
                .build();
        DeleteStackRequest deleteStackRequest = new DeleteStackRequest();
        deleteStackRequest.setStackName(cloudFormationName);
        stackdestroy.deleteStack(deleteStackRequest);
        return waitForAWSProcess(stackdestroy, cloudFormationName);
    }
}
