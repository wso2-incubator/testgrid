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

import React, { Component } from 'react';
import '../App.css';
import Subheader from 'material-ui/Subheader';
import SingleRecord from './SingleRecord.js';
import {HTTP_UNAUTHORIZED, LOGIN_URI, TESTGRID_CONTEXT} from '../constants.js';
import { Table } from 'reactstrap';

class TestCaseView extends Component {

    constructor(props) {
        super(props);
        this.state = {
            hits: {
                testCases: []
            }
        }
    }

    handleError(response) {
        if (response.status.toString() === HTTP_UNAUTHORIZED) {
            window.location.replace(LOGIN_URI);
            return response;
        }
        return response;
    }

    componentDidMount() {
        var url = TESTGRID_CONTEXT + '/api/test-scenarios/'+this.props.active.reducer.currentScenario.scenarioId;

        fetch(url, {
            method: "GET",
            credentials: 'same-origin',
            headers: {
                'Accept': 'application/json'
            }
        })
            .then(this.handleError)
            .then(response => {
                return response.json();
            })
            .then(data => this.setState({ hits: data }));

    }

    render() {
        return (
            <div>
                <Subheader style={{ fontSize: '20px' }} > <i>{this.props.active.reducer.currentProduct.productName} {this.props.active.reducer.currentProduct.productVersion} {this.props.active.reducer.currentProduct.productChannel} / {this.props.active.reducer.currentDeployment.deploymentName} /{this.props.active.reducer.currentInfra.deploymentName} /
                    {this.props.active.reducer.currentScenario.scenarioName} </i>
                </Subheader>

                <Table responsive>
                    <thead displaySelectAll={false} adjustForCheckbox={false}>
                    <tr>
                        <th>TestCase</th>
                        <th>TestResult</th>
                        <th>Error message</th>
                    </tr>
                    </thead>
                    <tbody displayRowCheckbox={false}>

                    {this.state.hits.testCases.map((data, index) => {
                        return (<tr key={index}>
                            <td>{data.name}</td>
                            <td><SingleRecord value={data.success} /></td>
                            <td style={{
                                color: 'red',
                                whiteSpace: 'normal',
                                wordWrap: 'break-word'
                            }}>{data.errorMsg}</td>
                        </tr>)
                    })}
                    </tbody>
                </Table>
            </div>
        );
    }
}

export default TestCaseView;
