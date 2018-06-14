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

import React, {Component} from 'react';
import '../App.css';
import {CardMedia} from 'material-ui/Card';
import FlatButton from 'material-ui/FlatButton';
import Avatar from 'material-ui/Avatar';
import List from 'material-ui/List/List';
import ListItem from 'material-ui/List/ListItem';
import Divider from 'material-ui/Divider';
import LinearProgress from 'material-ui/LinearProgress';
import Download from 'downloadjs'
import Snackbar from 'material-ui/Snackbar';
import {FAIL, SUCCESS, ERROR, PENDING, RUNNING, HTTP_NOT_FOUND, HTTP_UNAUTHORIZED, LOGIN_URI, TESTGRID_CONTEXT, DID_NOT_RUN, INCOMPLETE}
  from '../constants.js';
import {Button, Table, Card, CardText, CardTitle, Col, Row} from 'reactstrap';
import InfraCombinationView from "./InfraCombinationView";
import ReactTooltip from 'react-tooltip'
import {HTTP_OK} from "../constants";

/**
 * View responsible for displaying test run log and summary information.
 *
 * @since 1.0.0
 */
class TestRunView extends Component {

  constructor(props) {
    super(props);
    this.state = {
      testScenarioSummaries: [],
      scenarioTestCaseEntries: [],
      testSummaryLoadStatus: PENDING,
      logContent: "",
      logDownloadStatus: PENDING,
      isLogTruncated: false,
      inputStreamSize: "",
      showLogDownloadErrorDialog: false,
      currentInfra: null
    };
  }

  componentDidMount() {
    let currentInfra = {};
    let currentUrl = window.location.href.split("/");
    currentInfra.relatedProduct = currentUrl[currentUrl.length - 4];
    currentInfra.relatedDeplymentPattern = currentUrl[currentUrl.length - 3];
    if (this.props.active.reducer.currentInfra) {
      currentInfra.testPlanId = this.props.active.reducer.currentInfra.testPlanId;
      currentInfra.infraParameters = this.props.active.reducer.currentInfra.infraParameters;
      currentInfra.testPlanStatus = this.props.active.reducer.currentInfra.testPlanStatus;
      this.getReportData(currentInfra);
      this.setState({currentInfra: currentInfra});
    } else {
      let url = TESTGRID_CONTEXT + "/api/test-plans/" + currentUrl.pop();
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
        }).then(data => {
        currentInfra.testPlanId = data.id;
        currentInfra.infraParameters = data.infraParams;
        currentInfra.testPlanStatus = data.status;
        this.props.active.reducer.currentInfra = currentInfra;
        this.getReportData(currentInfra);
        this.setState({currentInfra: currentInfra})
      });
    }
  }

  getReportData(currentInfra) {
    const testScenarioSummaryUrl = TESTGRID_CONTEXT + '/api/test-plans/test-summary/' + currentInfra.testPlanId;
    const logTruncatedContentUrl = TESTGRID_CONTEXT + '/api/test-plans/log/' + currentInfra.testPlanId
      + "?truncate=" + true;

    fetch(testScenarioSummaryUrl, {
      method: "GET",
      credentials: 'same-origin',
      headers: {
        'Accept': 'application/json'
      }
    })
      .then(this.handleError)
      .then(response => {
        this.setState({
          testSummaryLoadStatus: response.ok ? SUCCESS : ERROR
        });
        return response.json();
      }).then(data => this.setState({
      testScenarioSummaries: data.scenarioSummaries,
      scenarioTestCaseEntries: data.scenarioTestCaseEntries
    }));

    if (currentInfra.testPlanStatus === FAIL || currentInfra.testPlanStatus === SUCCESS) {
      fetch(logTruncatedContentUrl, {
        method: "GET",
        credentials: 'same-origin',
        headers: {
          'Accept': 'application/json'
        }
      })
        .then(this.handleError)
        .then(response => {
          this.setState({
            logDownloadStatus: response.ok ? SUCCESS : ERROR
          });
          return response.json();
        }).then(data =>
        this.setState({
          logContent: data.inputStreamContent,
          isLogTruncated: data.truncated,
          inputStreamSize: data.completeInputStreamSize
        }));
    }
  }

  handleLogDownloadStatusDialogClose = () => {
    this.setState({
      showLogDownloadStatusDialog: false,
    });
  };

  handleLiveLogData(data) {
    this.setState({logContent: this.state.logContent + data});
  }

  handleError(response) {
    if (response.status.toString() === HTTP_UNAUTHORIZED) {
      window.location.replace(LOGIN_URI);
    }
    return response;
  }

  toggle(Message) {
    this.setState({
      showLogDownloadStatusMessage: Message,
      showLogDownloadStatusDialog: true,
    });
  }

  downloadScenarioResult(scenarioId) {
    let url = TESTGRID_CONTEXT + '/api/test-plans/result/' + this.state.currentInfra.testPlanId + "/" + scenarioId;
    fetch(url, {
      method: "GET",
      credentials: 'same-origin',
    }).then(response => {
        if (response.status === HTTP_NOT_FOUND) {
          let errorMessage = "Unable to locate results in the remote storage.";
          this.toggle(errorMessage);
        } else if (response.status !== HTTP_OK) {
          let errorMessage = "Internal server error. Couldn't download the results at the moment.";
          this.toggle(errorMessage);
        } else if (response.status === HTTP_OK) {
          let statusMessage = "Download will begin in a moment..";
          this.toggle(statusMessage);
          document.location = url;
        }
      }
    ).catch(error => console.error(error));
  }

  render() {
    const divider = (<Divider inset={false} style={{borderBottomWidth: 1}}/>);
    const logAllContentUrl = TESTGRID_CONTEXT + '/api/test-plans/log/' +
      window.location.href.split("/").pop() + "?truncate=" + false;
    let isFailedTestsTitleAdded = false;

    return (
      <div>
        {this.state && this.state.currentInfra && (() => {
          switch (this.state.currentInfra.testPlanStatus) {
            case FAIL:
              return <Row>
                <Col sm="12">
                  <Card body inverse style={{ backgroundColor: '#e57373', borderColor: '#e57373' }}>
                    <CardTitle><i className="fa fa-exclamation-circle" aria-hidden="true" data-tip="Failed!">
                      <span> {this.state.currentInfra.relatedProduct}</span>
                    </i><ReactTooltip/></CardTitle>
                    <CardText>{this.state.currentInfra.relatedDeplymentPattern}</CardText>
                    {InfraCombinationView.parseInfraCombination(this.state.currentInfra.infraParameters)}
                  </Card>
                </Col>
              </Row>;
            case SUCCESS:
              return <Row>
                <Col sm="12">
                  <Card body inverse color="success">
                    <CardTitle><i className="fa fa-check-circle" aria-hidden="true" data-tip="Success!">
                      <span> {this.state.currentInfra.relatedProduct}</span>
                    </i><ReactTooltip/></CardTitle>
                    <CardText>{this.state.currentInfra.relatedDeplymentPattern}</CardText>
                    {InfraCombinationView.parseInfraCombination(this.state.currentInfra.infraParameters)}
                  </Card>
                </Col>
              </Row>;
            case PENDING:
            case RUNNING:
            default:
              return <Row>
                <Col sm="12">
                  <Card body inverse color="info">
                    <CardTitle><i className="fa fa-spinner fa-pulse" data-tip="Running!">
                      <span className="sr-only">Loading...</span></i><ReactTooltip/>
                      <span> {this.state.currentInfra.relatedProduct}</span></CardTitle>
                    <CardText>{this.state.currentInfra.relatedDeplymentPattern}</CardText>
                    {InfraCombinationView.parseInfraCombination(this.state.currentInfra.infraParameters)}
                  </Card>
                </Col>
              </Row>;
          }
        })()}
        <Card style={{padding: 20}}>
          <CardMedia>
            {/*TestGrid generated contents*/}
            <List>
              <ListItem disabled={true}
                        leftAvatar={
                          <Avatar
                            src={require('../log.png')}
                            size={50}
                            style={{
                              borderRadius: 0,
                              backgroundColor: "#ffffff"
                            }}/>
                        }>
                <FlatButton label="Download Test Run Log"
                            onClick={() => (fetch(logAllContentUrl, {
                              method: "GET",
                              credentials: 'same-origin',
                              headers: {
                                'Accept': 'application/json'
                              }
                            })
                              .then(this.handleError)
                              .then(response => {
                                if (response.status !== HTTP_OK) {
                                  let statusMessage = "Error on downloading log file...";
                                  this.toggle(statusMessage);
                                }
                                return response.json();
                              }).then(data => {
                                  if (!this.state.showLogDownloadStatusDialog) {
                                    Download(data.inputStreamContent, "test-run.log", "plain/text");
                                  }
                                }
                              ))}


                />
                <Snackbar
                  open={this.state.showLogDownloadStatusDialog}
                  message={this.state.showLogDownloadStatusMessage}
                  autoHideDuration={4000}
                  onRequestClose={this.handleLogDownloadStatusDialogClose}
                  contentStyle={{
                    fontWeight: 600,
                    fontSize: "15px"
                  }}
                />
              </ListItem>
            </List>
            {divider}
            {/*Scenario execution summary*/}
            <h2>Scenario execution summary</h2>
            {(() => {
              switch (this.state.testSummaryLoadStatus) {
                case ERROR:
                  return <div style={{
                    padding: 5,
                    color: "#D8000C",
                    backgroundColor: "#FFD2D2"
                  }}>
                    <br/>
                    <strong>Oh snap! </strong>
                    Error occurred when loading test summaries.
                  </div>;
                case SUCCESS:
                  return <div>
                    <Table responsive>
                      <thead displaySelectAll={false} adjustForCheckbox={false}>
                      <tr>
                        <th style={{width: "5%", textAlign: "center"}}/>
                        <th>Scenario</th>
                        <th style={{width: "15%", textAlign: "center"}}>Total Success</th>
                        <th style={{width: "15%", textAlign: "center"}}>Total Failed</th>
                        <th style={{width: "15%", textAlign: "center"}}>Success Percentage</th>
                        <th style={{width: "15%", textAlign: "center"}}>Results</th>
                      </tr>
                      </thead>
                      <tbody displayRowCheckbox={false} showRowHover={true}>
                      {this.state.testScenarioSummaries.map((data, index) => {
                        return (<tr key={index}>
                          <td style={{width: "5%"}}>
                            {(() => {
                              switch (data.scenarioStatus) {
                                case SUCCESS:
                                  return <div>
                                    <Button outline color="success" size="sm" className="success-status-btn">
                                      <i className="fa fa-check-circle" aria-hidden="true" data-tip="Success!"> </i>
                                      <ReactTooltip/>
                                    </Button>
                                  </div>;
                                case FAIL:
                                  return <div>
                                    <Button outline color="danger" size="sm">
                                      <i className="fa fa-exclamation-circle" aria-hidden="true"
                                         data-tip="Failed!"> </i>
                                      <ReactTooltip/>
                                    </Button>
                                  </div>;
                                case PENDING:
                                  return <div>
                                    <Button outline color="info" size="sm">
                                      <i className="fa fa-tasks" aria-hidden="true" data-tip="Pending!"> </i>
                                      <ReactTooltip/>
                                    </Button>
                                  </div>;
                                case INCOMPLETE:
                                  return <div>
                                    <Button outline color="info" size="sm" className="incomplete-status-btn">
                                      <i className="fa fa-hourglass-half" aria-hidden="true"
                                         data-tip="Incomplete!"> </i>
                                      <ReactTooltip/>
                                    </Button>
                                  </div>;
                                case DID_NOT_RUN:
                                  return <div>
                                    <Button outline color="info" size="sm" className="not-run-status-btn" >
                                      <i className="fa fa-ban" aria-hidden="true" data-tip="Did Not Run!"> </i>
                                      <ReactTooltip/>
                                    </Button>
                                  </div>;
                                case ERROR:
                                  return <div>
                                    <Button outline color="danger" size="sm" className="error-status-btn">
                                      <i className="fa fa-times-circle" aria-hidden="true" data-tip="Error!"> </i>
                                      <ReactTooltip/>
                                    </Button>
                                  </div>;
                                case "RUNNING":
                                default:
                                  return <div>
                                    <Button outline color="info" size="sm" className="running-status-btn">
                                      <i className="fa fa-spinner fa-pulse" data-tip="Running!"> </i>
                                      <span className="sr-only">Loading...</span>
                                      <ReactTooltip/>
                                    </Button>
                                  </div>
                              }
                            })()}
                          </td>
                          <td style={{
                            fontSize: "15px",
                            wordWrap: "break-word",
                            whiteSpace: "wrap",
                            textDecoration: "none"
                          }}><a href={"#" + data.scenarioDescription}>
                            {data.scenarioDescription}
                          </a>
                          </td>
                          <td
                            style={{
                              width: "15%",
                              textAlign: "center",
                              color: "#189800",
                              fontSize: "20px",
                              wordWrap: "break-word",
                              whiteSpace: "wrap"
                            }}>{data.totalSuccess}</td>
                          <td
                            style={{
                              width: "15%",
                              textAlign: "center",
                              color: "#c12f29",
                              fontSize: "20px",
                              wordWrap: "break-word",
                              whiteSpace: "wrap"
                            }}>{data.totalFail}</td>
                          <td
                            style={{
                              width: "15%",
                              textAlign: "center",
                              fontSize: "20px",
                              wordWrap: "break-word",
                              whiteSpace: "wrap"
                            }}>
                            {
                              isNaN(data.successPercentage) ?
                                "0.0" :
                                parseFloat(data.successPercentage).toFixed(2)
                            }%
                          </td>
                          <td
                            style={{
                              width: "15%",
                              textAlign: "center",
                              fontSize: "20px",
                              wordWrap: "break-word",
                              whiteSpace: "wrap"
                            }}>
                              <Button outline color="info" size="sm"
                                     onClick={this.downloadScenarioResult.bind(this, data.scenarioDir)}>
                              <i className="fa fa-download" aria-hidden="true"> </i>
                              </Button>
                          </td>
                        </tr>)
                      })}
                      </tbody>
                    </Table>
                    <br/>
                    <br/>
                    {divider}
                    {/*Detailed Report for failed test cases*/}
                    {this.state.scenarioTestCaseEntries.map((data, index) => {
                      if (data.testCaseEntries.length > 0) {
                        const failedTestTitleContent = isFailedTestsTitleAdded ?
                          "" : <h2>Failed Tests</h2>;
                        isFailedTestsTitleAdded = true;
                        return (
                          <div>
                            {failedTestTitleContent}
                            <h2 style={{color: "#e46226"}}>
                              <a id={data.scenarioDescription}>
                                {data.scenarioDescription}
                              </a>
                            </h2>
                            <Table responsive>
                              <thead displaySelectAll={false} adjustForCheckbox={false}>
                              <tr>
                                <th style={{width: "5%", textAlign: "center"}}/>
                                <th style={{width: "30%"}}>Test Case</th>
                                <th style={{width: "65%"}}>Failure Message</th>
                              </tr>
                              </thead>
                              <tbody displayRowCheckbox={false}
                                     showRowHover={true}>
                              {data.testCaseEntries.map((entry, index) => {
                                return (
                                  <tr key={index}>
                                    <td
                                      style={{width: "5%"}}>
                                      {entry.isTestSuccess ?
                                        <Button outline color="success" size="sm" className="success-status-btn">
                                          <i className="fa fa-check-circle" aria-hidden="true" data-tip="Success!"> </i>
                                          <ReactTooltip/>
                                        </Button> :
                                        <Button outline color="danger" size="sm">
                                          <i className="fa fa-exclamation-circle" aria-hidden="true"
                                             data-tip="Failed!"> </i>
                                          <ReactTooltip/>
                                        </Button>}
                                    </td>
                                    <td style={{
                                      fontSize: "15px",
                                      width: "30%",
                                      wordWrap: "break-word",
                                      whiteSpace: "wrap",
                                    }}>{entry.testCase}</td>
                                    <td style={{
                                      fontSize: "15px",
                                      width: "65%",
                                      wordWrap: "break-word",
                                      whiteSpace: "wrap",
                                      paddingTop: 15,
                                      paddingBottom: 15
                                    }}>
                                      {entry.failureMessage}
                                    </td>
                                  </tr>
                                )
                              })}
                              </tbody>
                            </Table>
                            <br/>
                          </div>)
                      } else {
                        return ("")
                      }
                    })}
                  </div>;
                case PENDING:
                default:
                  return <div>
                    <br/>
                    <br/>
                    <b>Loading test summaries...</b>
                    <br/>
                    <LinearProgress mode="indeterminate"/>
                  </div>;
              }
            })()}
          </CardMedia>
          <br/>
          <br/>
        </Card>
      </div>
    );
  }
}

export default TestRunView;
