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

package org.wso2.testgrid.deployment.tinkerer.api;

import org.glassfish.jersey.server.ChunkedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.testgrid.common.Agent;
import org.wso2.testgrid.common.ShellExecutor;
import org.wso2.testgrid.common.agentoperation.Operation;
import org.wso2.testgrid.common.agentoperation.OperationRequest;
import org.wso2.testgrid.common.agentoperation.OperationSegment;
import org.wso2.testgrid.common.exception.CommandExecutionException;
import org.wso2.testgrid.deployment.tinkerer.SessionManager;
import org.wso2.testgrid.deployment.tinkerer.beans.ErrorResponse;
import org.wso2.testgrid.deployment.tinkerer.exception.AgentHandleException;
import org.wso2.testgrid.deployment.tinkerer.exception.DeploymentTinkererException;
import org.wso2.testgrid.deployment.tinkerer.utils.AgentStreamHandler;
import org.wso2.testgrid.deployment.tinkerer.utils.Constants;
import org.wso2.testgrid.deployment.tinkerer.utils.SSHHelper;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.websocket.Session;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * This class represents REST service implementation of Deployment Tinkerer service.
 *
 * @since 1.0.0
 */

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class DeploymentTinkerer {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentTinkerer.class);

    /**
     * Get list of registered agents.
     *
     * @return A list of registered agents.
     */
    @GET
    @Path("agents")
    public Response listAllRegisteredAgents() {
        SessionManager sessionManager = SessionManager.getInstance();
        return Response.status(Response.Status.OK).entity(sessionManager.getAgents()).build();
    }


    /**
     * Get list of test plans.
     *
     * @return A list of active test plans.
     */
    @GET
    @Path("test-plans")
    public Response listAllTestPlans() {
        SessionManager sessionManager = SessionManager.getInstance();
        ConcurrentHashMap<String, String> testPlans = new ConcurrentHashMap<>();
        sessionManager.getAgents()
                .forEach(agent -> testPlans.putIfAbsent(agent.getTestPlanId(), agent.getTestPlanId()));
        return Response.status(Response.Status.OK).entity(testPlans.values()).build();
    }

    /**
     * Get list of registered agents under a test plan.
     *
     * @return A list of registered agents under a test plan.
     */
    @GET
    @Path("test-plan/{testPlanId}/agents")
    public Response listAllRegisteredTestPlanAgents(@PathParam("testPlanId") String testPlanId) {
        SessionManager sessionManager = SessionManager.getInstance();
        List<Agent> agents = sessionManager.getAgents().stream()
                .filter(agent -> testPlanId.equals(agent.getTestPlanId())).collect(Collectors.toList());
        return Response.status(Response.Status.OK).entity(agents).build();
    }

    /**
     * Send operation to agent and get response as stream.
     *
     * @param testPlanId       - Test plan id of the target agent.
     * @param instanceName     - Instance Name of the target agent.
     * @param operationRequest - Operation request.
     * @return The operation response.
     */
    @POST
    @Path("test-plan/{testPlanId}/agent/{instanceName}/stream-shell")
    @Consumes(MediaType.APPLICATION_JSON)
    public ChunkedOutput<String> sendStreamingOperation(@PathParam("testPlanId") String testPlanId,
                                                        @PathParam("instanceName") String instanceName,
                                                        OperationRequest operationRequest) {
        final ChunkedOutput<String> output = new ChunkedOutput<String>(String.class);
        logger.info("Operation request received " + operationRequest.toJSON());
        AgentStreamHandler agentStreamHandler = new AgentStreamHandler(output, operationRequest, testPlanId,
                instanceName);
        try {
            agentStreamHandler.startSendCommand();
            SessionManager.getAgentObservable().addObserver(agentStreamHandler);
        } catch (AgentHandleException e) {
            logger.error("Error while sending command to the Agent for test plan " + testPlanId, e);
        }
        return output;
    }

    /**
     * Abort running operation on agent by using operation id
     *
     * @param testPlanId            The test plan id
     * @param instanceName          The instance name
     * @param operationRequest      Operation request
     * @return                      Operation response
     */
    @POST
    @Path("test-plan/{testPlanId}/agent/{instanceName}/abort")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response abortOperation(@PathParam("testPlanId") String testPlanId,
                                  @PathParam("instanceName") String instanceName, OperationRequest operationRequest) {
        logger.info("Operation request received " + operationRequest.toJSON());
        Response responseToSend = Response.status(Response.Status.OK).build();
        SessionManager sessionManager = SessionManager.getInstance();
        Agent agent = sessionManager.getAgent(testPlanId, instanceName);
        if (agent != null) {
            if (sessionManager.hasAgentSession(agent.getAgentId())) {
                AgentStreamHandler agentStreamHandler = new AgentStreamHandler();
                OperationSegment operationSegmentReturn = new OperationSegment();
                operationSegmentReturn.setCode(Operation.OperationCode.ABORT);
                operationSegmentReturn.setCompleted(true);
                operationSegmentReturn.setOperationId(operationRequest.getOperationId());
                operationSegmentReturn.setExitValue(0);
                if (agentStreamHandler.abortOperation(operationRequest.getOperationId(), agent.getAgentId())) {
                    responseToSend = Response.status(Response.Status.OK).entity(operationSegmentReturn).build();
                } else {
                    responseToSend = Response.status(Response.Status.INTERNAL_SERVER_ERROR).
                            entity(operationSegmentReturn).build();
                }
            } else {
                ErrorResponse errorResponse = new ErrorResponse();
                errorResponse.setCode(Response.Status.NOT_FOUND.getStatusCode());
                errorResponse.setMessage("Agent not found with given ID " + agent.getAgentId());
                responseToSend = Response.status(Response.Status.NOT_FOUND).entity(errorResponse).build();
            }
        } else {
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.setCode(Response.Status.NOT_FOUND.getStatusCode());
            errorResponse.setMessage("No agent found, Agent is null for test plan id" + testPlanId);
            responseToSend = Response.status(Response.Status.NOT_FOUND).entity(errorResponse).build();
        }

        return responseToSend;
    }
    /**
     * Send operation to agent and get response.
     *
     * @param testPlanId       - Test plan id of the target agent.
     * @param instanceName     - Instance Name of the target agent.
     * @param operationRequest - Operation request.
     * @return The operation response.
     */
    @POST
    @Path("test-plan/{testPlanId}/agent/{instanceName}/operation")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response sendOperation(@PathParam("testPlanId") String testPlanId,
                                  @PathParam("instanceName") String instanceName, OperationRequest operationRequest) {
        SessionManager sessionManager = SessionManager.getInstance();
        Agent agent = sessionManager.getAgent(testPlanId, instanceName);
        OperationSegment operationSegment =  new OperationSegment();
        operationSegment.setResponse("");
        if (agent != null && sessionManager.hasAgentSession(agent.getAgentId())) {
            Session wsSession = sessionManager.getAgentSession(agent.getAgentId());
            try {
                // Set default operation id if not exist
                if (operationRequest.getOperationId().equals("")) {
                    operationRequest.setOperationId(UUID.randomUUID().toString());
                }
                // Create new operation queue
                sessionManager.addNewOperationQueue(operationRequest.getOperationId(), operationRequest.getCode(),
                        agent.getAgentId());
                // Send command to agent through the socket
                wsSession.getBasicRemote().sendText(operationRequest.toJSON());
                operationSegment.setOperationId(operationRequest.getOperationId());
                long initTime = Calendar.getInstance().getTimeInMillis();
                while (true) {
                    long currentTime = Calendar.getInstance().getTimeInMillis();
                    OperationSegment tempOperationSegment = sessionManager.dequeueOperationQueueMessages(
                            operationRequest.getOperationId());
                    if (tempOperationSegment != null) {
                        operationSegment.setResponse(operationSegment.getResponse().
                                concat(tempOperationSegment.getResponse()));
                        if (tempOperationSegment.getCompleted()) {
                            operationSegment.setCompleted(true);
                            operationSegment.setExitValue(tempOperationSegment.getExitValue());
                            break;
                        }
                    } else {
                        operationSegment.setCompleted(true);
                        operationSegment.setExitValue(1);
                        break;
                    }
                    if (initTime + Constants.OPERATION_TIMEOUT < currentTime) {
                        String message = "Operation timed out for agent: " + agent.getAgentId();
                        logger.error(message);
                        ErrorResponse errorResponse = new ErrorResponse();
                        errorResponse.setCode(Response.Status.REQUEST_TIMEOUT.getStatusCode());
                        errorResponse.setMessage(message);
                        return Response.status(Response.Status.REQUEST_TIMEOUT).entity(errorResponse).build();
                    }
                    try {
                        Thread.sleep(Constants.AGENT_WAIT_TIMEOUT);
                    } catch (InterruptedException ignore) {
                    }
                }
            } catch (IOException e) {
                String message = "Error occurred while sending operation to agent: " + agent.getAgentId();
                logger.error(message, e);
                ErrorResponse errorResponse = new ErrorResponse();
                errorResponse.setCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
                errorResponse.setMessage(message);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
            }
            return Response.status(Response.Status.OK)
                    .entity(operationSegment).build();
        } else {
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.setCode(Response.Status.NOT_FOUND.getStatusCode());
            errorResponse.setMessage("Agent not found with given ID");
            return Response.status(Response.Status.NOT_FOUND).entity(errorResponse).build();
        }
    }

    /**
     * Downloads a file from the given source to the destination.
     * The destination path must be a location in the host machine.
     *
     * @param testPlanId   TestPlanID for the instance where source is located
     * @param instanceName Name of the instance where source is located
     */
    @POST
    @Path("test-plan/{testPlanId}/agent/{instanceName}/stream-file")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response streamFile(@PathParam("testPlanId") String testPlanId,
                               @PathParam("instanceName") String instanceName,
                               OperationRequest operationRequest) {
        //get POST body data
        Map<String, String> data = operationRequest.getMetaData();
        SessionManager sessionManager = SessionManager.getInstance();
        Agent agent = sessionManager.getAgent(testPlanId, instanceName);

        if (data != null) {
            String sshKey = data.get("key");
            String bastianIP = data.get("bastian-ip");
            String source = data.get("source");
            String destination = data.get("destination");
            try {
                SSHHelper.addConfigEntry(sshKey, bastianIP, agent);
                ShellExecutor executor = new ShellExecutor();
                executor.executeCommand("ssh host:" + agent.getInstanceId() + " \"cat " + source
                        + "\" &> " + destination + "");

            } catch (IOException e) {
                String message = "Error occurred while adding ssh config entries for agent: " + agent.getAgentId();
                logger.error(message, e);
                ErrorResponse errorResponse = new ErrorResponse();
                errorResponse.setCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
                errorResponse.setMessage(message);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
            } catch (CommandExecutionException e) {
                String message = "Error occurred while executing the ssh command : " + agent.getAgentId();
                logger.error(message, e);
                ErrorResponse errorResponse = new ErrorResponse();
                errorResponse.setCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
                errorResponse.setMessage(message);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
            } catch (DeploymentTinkererException e) {
                String message = "Instance username is null in agent  : " + agent.getAgentId();
                logger.error(message, e);
                ErrorResponse errorResponse = new ErrorResponse();
                errorResponse.setCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
                errorResponse.setMessage(message);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
            }

        }
        return Response.status(Response.Status.OK).build();
    }

}
