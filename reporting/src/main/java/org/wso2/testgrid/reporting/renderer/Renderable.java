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
package org.wso2.testgrid.reporting.renderer;

import org.wso2.testgrid.reporting.ReportingException;

import java.util.Map;

/**
 * Interface to be implemented in order to implement a report renderer.
 *
 * @since 1.0.0
 */
public interface Renderable {

    /**
     * Render a given model from a given template.
     *
     * @param view  name of the template file in resources/templates directory
     * @param model model to be rendered from the template
     * @return rendered template
     */
    String render(String view, Map<String, Object> model) throws ReportingException;
}
