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

package org.wso2.testgrid.common.exception;

/**
 * This is a custom exception class to throw exceptions when an invalid class field is requested through reflection.
 */
public class InvalidClassFieldException extends Exception {

    /**
     * Constructs a new exception with {@code null} as its detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable)}.
     */
    public InvalidClassFieldException() {
        super();
    }

    /**
     * Constructs a new exception with the specified detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause}.
     *
     * @param message the detail message of the exception
     */
    public InvalidClassFieldException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified cause and a detail message of {@code (cause==null ? null :
     * cause.toString())} which typically contains the class and detail message of the {@code cause}.
     *
     * @param cause the cause of the exception
     */
    public InvalidClassFieldException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message of the exception
     * @param cause   the cause of the exception
     */
    public InvalidClassFieldException(String message, Throwable cause) {
        super(message, cause);
    }
}
