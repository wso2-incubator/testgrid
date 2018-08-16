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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.testgrid.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.testgrid.common.agentoperation.AgentObservable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Thread to read stream output of the command execution and update observers.
 */
class AgentStreamGobbler extends Thread {
    InputStream inputStream;
    AgentObservable agentObservable;
    private static final Logger logger = LoggerFactory.getLogger(AgentStreamGobbler.class);

    /**
     * Constructor to initialize object.
     *
     * @param inputStream           Streaming output of the command execution
     * @param agentObservable       Observable waiting for logs
     */
    AgentStreamGobbler(InputStream inputStream, AgentObservable agentObservable) {
        this.inputStream = inputStream;
        this.agentObservable = agentObservable;
    }

    /**
     * Read stream and notify observers with the logs
     */
    @Override
    public void run() {
        try {
            InputStreamReader inputStreamReader = new InputStreamReader(this.inputStream, UTF_8);
            BufferedReader br = new BufferedReader(inputStreamReader);
            String line = "";
            while ((line = br.readLine()) != null) {
                this.agentObservable.notifyObservable(line);
            }
            this.agentObservable.notifyObservable(null);
        } catch (IOException e) {
            logger.info("Error while reading stream ", e);
        }
    }
}
