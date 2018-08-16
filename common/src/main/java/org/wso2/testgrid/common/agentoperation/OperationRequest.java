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

package org.wso2.testgrid.common.agentoperation;

import java.util.UUID;

/**
 * Tinkerer response class to handle request from Tinkerer message queue.
 */
public class OperationRequest extends Operation {
    private String request;

    /**
     * Default constructor to create operation request
     */
    public OperationRequest() {
        super();
        this.setOperationId(UUID.randomUUID().toString());
    }

    /**
     * Generation operation request for given command and code
     *
     * @param request       Command to send
     * @param code          Type of the operation
     */
    public OperationRequest(String request, OperationCode code) {
        super();
        this.request = request;
        this.setCode(code);
    }

    /**
     * Get request command
     *
     * @return  The request
     */
    public String getRequest() {
        return request;
    }

    /**
     * Set request command
     *
     * @param request The request
     */
    public void setRequest(String request) {
        this.request = request;
    }
}
