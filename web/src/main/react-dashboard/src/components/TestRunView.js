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
import Collapsible from 'react-collapsible';
import '../App.css';
import {CardMedia} from 'material-ui/Card';
import Divider from 'material-ui/Divider';
import LinearProgress from 'material-ui/LinearProgress';
import FlatButton from 'material-ui/FlatButton';
import Snackbar from 'material-ui/Snackbar';
import {FAIL, SUCCESS, ERROR, PENDING, RUNNING, HTTP_NOT_FOUND, HTTP_UNAUTHORIZED, LOGIN_URI,
  TESTGRID_API_CONTEXT, DID_NOT_RUN, INCOMPLETE} from '../constants.js';
import Button from '@material-ui/core/Button';
import {Table, Card, CardText, CardTitle, Col, Row} from 'reactstrap';
import InfraCombinationHistory from "./InfraCombinationHistory";
import ReactTooltip from 'react-tooltip'
import {HTTP_OK} from "../constants";
import OutputFilesPopover from "./OutputFilesPopover";
import Chip from '@material-ui/core/Chip';
import DoneIcon from '@material-ui/icons/Done';
import CloseIcon from '@material-ui/icons/Close';
import WaitIcon from '@material-ui/icons/DoneOutline';
import IncompleteIcon from '@material-ui/icons/Flip';

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
      currentInfra: props.currentInfra,
      TruncatedRunLogUrlStatus:null,
      grafanaUrl: "",
      wso2LogsUrl: ""
    };
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    const {productName, deploymentPatternName, testPlanId} = this.props.match.params;
    let currentInfra = {};
    currentInfra.relatedProduct = productName;
    currentInfra.relatedDeplymentPattern = deploymentPatternName;
    currentInfra.testPlanId = testPlanId;
    if (prevProps.currentInfra && (prevProps.currentInfra.testPlanId !== this.props.currentInfra.testPlanId)) {
      this.updateCurrentInfra(currentInfra);
    }
  }

  updateCurrentInfra(currentInfra) {
    currentInfra.testPlanId = this.props.currentInfra.testPlanId;
    currentInfra.infraParameters = this.props.currentInfra.infraParameters;
    currentInfra.testPlanStatus = this.props.currentInfra.testPlanStatus;
    this.getTestPlanData(currentInfra);
    this.setState({currentInfra: currentInfra});
    this.getReportData(currentInfra);
    this.getGrafanaUrl(currentInfra.testPlanId);
  }

  componentDidMount() {
    const { productName, deploymentPatternName, testPlanId } = this.props.match.params;
    let currentInfra = {};
    currentInfra.relatedProduct = productName;
    currentInfra.relatedDeplymentPattern = deploymentPatternName;
    currentInfra.testPlanId = testPlanId;

    if (this.props.currentInfra) {
      this.updateCurrentInfra(currentInfra);
    } else {
      let url = TESTGRID_API_CONTEXT + "/api/test-plans/" + testPlanId;
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
          currentInfra.testPlanPhase = data.phase;
          this.getReportData(currentInfra);
          this.getGrafanaUrl(currentInfra.testPlanId);
          this.getTestPlanData(currentInfra);
          this.setState({currentInfra: currentInfra});
      });
    }

    this.checkIfTestRunLogExists();
  }

  getReportData(currentInfra) {
    const testScenarioSummaryUrl = TESTGRID_API_CONTEXT + '/api/test-plans/test-summary/' + currentInfra.testPlanId;
    const logTruncatedContentUrl = TESTGRID_API_CONTEXT + '/api/test-plans/log/' + currentInfra.testPlanId
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
          if (response.ok) {
            let json = response.json();
            this.setState({
              logContent: json.inputStreamContent,
              isLogTruncated: json.truncated,
              inputStreamSize: json.completeInputStreamSize
            })
          }
        });
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
    let url = TESTGRID_API_CONTEXT + '/api/test-plans/result/' + this.state.currentInfra.testPlanId + "/" + scenarioId;
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

  checkIfTestRunLogExists() {
    const path = TESTGRID_API_CONTEXT + '/api/test-plans/log/' + window.location.href.split("/").pop() +
      "?truncate=" + true;
    fetch(path, {
      method: "HEAD",
      credentials: 'same-origin',
      headers: {
        'Accept': 'application/json'
      }
    })
      .then(this.handleError)
      .then(response => {
        this.setState({TruncatedRunLogUrlStatus:response.status});
      })
  }

  async getGrafanaUrl(testid){
    const grafanaUrl = TESTGRID_API_CONTEXT + '/api/test-plans/perf-url/' + testid;
    const fetchResult = fetch(grafanaUrl, {
                            method: "GET",
                            credentials: 'same-origin',
                            headers: {
                            'Accept': 'application/json'
                            }
                       }).then(this.handleError);
     const response = await fetchResult;
     const urlData = await response.text();
     this.setState({grafanaUrl: urlData});

  }

  getTestPlanData(currentInfra) {
          let url = TESTGRID_API_CONTEXT + "/api/test-plans/" + currentInfra.testPlanId;
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
                  .then(data => {
                    this.setState({deploymentLogsUrl: data.logUrl});
                    this.setState({buildURL: data.buildURL});
                    currentInfra.testRunNumber = data.testRunNumber;
                    currentInfra.testPlanPhase = data.phase;
                  })
          .catch(error => console.error(error));
  }

  parsePhases(){
    if (this.state && this.state.currentInfra.testPlanStatus === "SUCCESS") {
      return (
        <div>
          <br/>
          <Chip style={{background: 'white', color: "green", margin: "5px"}}
                icon={<DoneIcon/>}
                label="PREPARATION"
                color="primary"
          />
          <Chip style={{background: 'white', color: "green", margin: "5px"}}
                icon={<DoneIcon/>}
                label="INFRA"
                color="primary"
          />
          <Chip style={{background: 'white', color: "green", margin: "5px"}}
                icon={<DoneIcon/>}
                label="DEPLOY"
                color="primary"
          />
          <Chip style={{background: 'white', color: "green", margin: "5px"}}
                icon={<DoneIcon/>}
                label="TEST"
                color="primary"
          />
        </div>)
    } else if (this.state.currentInfra.testPlanPhase) {
      return <div><br/>
        {(() => {
          var phase = this.state.currentInfra.testPlanPhase;
          var phases = ["PREPARATION", "INFRA", "DEPLOY", "TEST"];
          var stage;
          var foundError = false;
          var foundStarted = false;
          var foundSucceeded = false;
          var rows = [];
          for (var i = 0; i < phases.length; i++) {
            if (phases[i] === phase.slice(0, phase.indexOf('_'))) {
              stage = phase.slice(phase.lastIndexOf('_') + 1)
              switch (stage) {
                case "ERROR":
                  rows.push(
                    <Chip style={{background: 'white', color: "red", margin: "5px"}}
                          icon={<CloseIcon/>}
                          label={phases[i]}
                          color="primary"
                    />)
                  foundError = true;
                  break;
                case "SUCCEEDED":
                  rows.push(
                    <Chip style={{background: 'white', color: "green", margin: "5px"}}
                          icon={<DoneIcon/>}
                          label={phases[i]}
                          color="primary"
                    />)
                  foundSucceeded = true;
                  break;
                case "INCOMPLETE":
                  rows.push(
                    <Chip style={{background: 'white', color: "brown", margin: "5px"}}
                          icon={<IncompleteIcon/>}
                          label={phases[i]}
                          color="primary"
                    />)
                  foundError = true;
                  break;
                case "STARTED":
                  default:
                  rows.push(<Chip style={{background: 'white', color: "green", margin: "5px"}}
                          icon={<i className="fa fa-spinner fa-pulse"/>}
                          label={phases[i]}
                          color="primary"
                    />)
                    foundStarted = true;
                  break;
              }
            } else if (foundStarted || foundSucceeded) {
              rows.push(
                <Chip style={{background: 'white', color: "grey", margin: "5px"}}
                      icon={<WaitIcon/>}
                      label={phases[i]}
                      color="primary"
                />)
            } else if (!foundError){
              rows.push(
                <Chip style={{background: 'white', color: "green", margin: "5px"}}
                      icon={<DoneIcon/>}
                      label={phases[i]}
                      color="primary"
                />)
            }
          }
          return rows;
        })()} </div>
    }
  }
  render() {
    var pageURL = window.location.href;
    const PERFDASH_URL = this.state.grafanaUrl
    const DEPL_LOGS_URL = this.state.deploymentLogsUrl
    const divider = (<Divider inset={false} style={{borderBottomWidth: 1}}/>);
    const logUrl = TESTGRID_API_CONTEXT + '/api/test-plans/log/' + pageURL.split("/").pop() + "?truncate=";
    const logAllContentUrl = logUrl + false;
    const turncatedRunLogUrl = logUrl + true;

    return (
      <div>
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
        {this.state && this.state.currentInfra && (() => {
          switch (this.state.currentInfra.testPlanStatus) {
            case SUCCESS:
              return <Row>
                <Col sm="12">
                  <Card body inverse color="success">
                    <CardTitle><i className="fa fa-check-circle" aria-hidden="true"
                                  data-tip={this.state.currentInfra.testPlanStatus}>
                      <span style={{fontSize: "120%"}}> {this.state.currentInfra.relatedProduct} Job</span>
                    </i><ReactTooltip/>
                    </CardTitle>
                    <CardText>{this.state.currentInfra.relatedDeplymentPattern} #{this.state.currentInfra.testRunNumber}
                    </CardText>
                    {InfraCombinationHistory.parseInfraCombination(this.state.currentInfra.infraParameters)}
                    {this.parsePhases()}
                  </Card>
                </Col>
              </Row>;
            case FAIL:
              return <Row>
                <Col sm="12">
                  <Card body inverse style={{ backgroundColor: "#e57373", borderColor: "#e57373" }}>
                    <CardTitle><i className="fa fa fa-times-circle" aria-hidden="true"
                                  data-tip={this.state.currentInfra.testPlanStatus}>
                      <span style={{fontSize: "120%"}}> {this.state.currentInfra.relatedProduct} Job</span>
                    </i><ReactTooltip/>
                    </CardTitle>
                    <CardText>{this.state.currentInfra.relatedDeplymentPattern} #{this.state.currentInfra.testRunNumber}
                    </CardText>
                    {InfraCombinationHistory.parseInfraCombination(this.state.currentInfra.infraParameters)}
                    {this.parsePhases()}
                  </Card>
                </Col>
              </Row>;
            case RUNNING:
              return <Row>
                <Col sm="12">
                  <Card body inverse color="info">
                    <CardTitle><i className="fa fa-spinner fa-pulse"
                                  data-tip={this.state.currentInfra.testPlanStatus}>
                    </i><ReactTooltip/>
                      <span style={{fontSize: "120%"}}> {this.state.currentInfra.relatedProduct} Job</span>
                    </CardTitle>
                    <CardText>{this.state.currentInfra.relatedDeplymentPattern} #{this.state.currentInfra.testRunNumber}
                    </CardText>
                    {InfraCombinationHistory.parseInfraCombination(this.state.currentInfra.infraParameters)}
                    {this.parsePhases()}
                  </Card>
                </Col>
              </Row>;
            default:
            case PENDING:
            case DID_NOT_RUN:
            case ERROR:
              return <Row>
                <Col sm="12">
                  <Card body inverse style={{ backgroundColor: "#d6d6d6", borderColor: "#d6d6d6" }}>
                    <CardTitle><i className="fa fa-exclamation-triangle" aria-hidden="true"
                                  data-tip={this.state.currentInfra.testPlanStatus}>
                      <span style={{fontSize: "120%"}}> {this.state.currentInfra.relatedProduct} Job</span>
                    </i><ReactTooltip/>
                    </CardTitle>
                    <CardText>{this.state.currentInfra.relatedDeplymentPattern} #{this.state.currentInfra.testRunNumber}
                    </CardText>
                    {InfraCombinationHistory.parseInfraCombination(this.state.currentInfra.infraParameters)}
                    {this.parsePhases()}
                  </Card>
                </Col>
              </Row>;

          }
        })()}
        {divider}
      <br/>
        <table>
            <tr>
              {this.state && this.state.currentInfra && this.state.currentInfra.testPlanStatus && (() => {
                if (this.state.currentInfra.testPlanStatus === RUNNING) {
                  return <td><Button id="tdd" size="sm" disabled variant="contained">
                   <i className="fa fa-id-card-o" aria-hidden="true" data-tip="Test plan is still running!"></i> &nbsp;
                    Test-Run log</Button> </td>
                } else {
                  return <td><Button id ="tdd" size="sm" variant="contained"
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
                                             this.toggle("Error on downloading log file...");
                                             if (document.getElementById('logConsole') !== null)
                                               document.getElementById('logConsole').style.display = "none"
                                           } else {
                                             window.open(logAllContentUrl, '_blank', false);
                                           }
                                         })
                                     )}
                  ><i className="fa fa-id-card-o" aria-hidden="true"> </i> &nbsp;Test-Run log</Button>
                  </td>
                }
              })()}
              <td>
              <Button id ="tdd" variant="contained" size="sm" style={{marginLeft: "10px"}}
                      onClick={() => {
                        if(this.state.buildURL === null || this.state.buildURL === undefined ){
                          this.toggle("Error when accessing TestGrid console");
                        }else{
                          window.open(this.state.buildURL, '_blank', false);
                        }
                      }}
              ><i className="fa fa-id-card-o" aria-hidden="true"> </i> &nbsp;Blue Ocean console</Button>
              </td>
              <td>
                {this.state && this.state.currentInfra && this.state.currentInfra.testPlanStatus && (() => {
                  if (this.state.currentInfra.testPlanStatus === RUNNING) {
                    return <div style={{paddingLeft: '10px'}}>
                      <Button disabled variant="contained">
                      <i className="fa fa-download" aria-hidden="true"> </i>  &nbsp;Downloads
                      </Button> </div>
                  } else {
                    return <OutputFilesPopover testPlanId={this.state.currentInfra.testPlanId}/>
                  }
                })()}
              </td>
            </tr>
        </table>
        <div style={{paddingLeft:"20px", display:"inline"}}>
        </div>
        <br/>
        <Card>
          {this.state && this.state.currentInfra && this.state.currentInfra.testPlanStatus && (() => {
            if (this.state.currentInfra.testPlanStatus !== RUNNING) {
              return <CardMedia>
                {/*Scenario execution summary*/}
                <Collapsible trigger="Scenario execution summary" open="true">
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
                                            <i className="fa fa-check-circle" aria-hidden="true"
                                               data-tip="Success!"> </i>
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
                                          <Button outline color="info" size="sm" className="not-run-status-btn">
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
                                }}><FlatButton class='view-history' style={{textAlign: "left"}}
                                               data-tip={data.scenarioConfigChangeSetDescription}>
                                  {(() => {
                                    if (data.scenarioConfigChangeSetName &&
                                      data.scenarioConfigChangeSetName !== 'default') {
                                      return <a href={"#" + data.scenarioDescription}>
                                        {data.scenarioConfigChangeSetName + "::" + data.scenarioDescription}
                                      </a>
                                    } else {
                                      return <a href={"#" + data.scenarioDescription}>
                                        {data.scenarioDescription}
                                      </a>
                                    }
                                  })()}
                                </FlatButton>
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
                              </tr>)
                            })}
                            </tbody>
                          </Table>
                          <br/>
                          <br/>
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
                </Collapsible>

                {/*Scenario execution summary*/}
                <Collapsible trigger="Failed tests" open="true" lazyRender={true}>
                  {(() => {
                    if (this.state.testSummaryLoadStatus === SUCCESS)
                      switch (this.state.testSummaryLoadStatus) {
                        case SUCCESS:
                          return <div>
                            {/*Detailed Report for failed test cases*/}
                            {this.state.scenarioTestCaseEntries.map((data, index) => {
                              if (data.testCaseEntries.length > 0) {
                                return (
                                  <div key={index} style={{padding: "10px"}}>
                                    <h4 style={{color: "#e46226"}}>
                                      <a id={data.scenarioDescription}>
                                        Scenario: {data.scenarioDescription}
                                      </a>
                                    </h4>
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
                                                <Button outline color="success" size="sm"
                                                        className="success-status-btn">
                                                  <i className="fa fa-check-circle" aria-hidden="true"
                                                     data-tip="Success!"> </i>
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
                                return <div style={{padding: "10px"}}>No failed tests..</div>
                              }
                            })}
                          </div>;
                        case PENDING:
                          return <div>
                            <br/>
                            <br/>
                            <b>Loading failed tests...</b>
                            <br/>
                            <LinearProgress mode="indeterminate"/>
                          </div>;
                        case ERROR:
                        default:
                          return <div style={{
                            padding: 5,
                            color: "#D8000C",
                            backgroundColor: "#FFD2D2"
                          }}>
                            <br/>
                            <strong>Oh snap! </strong>
                            Error occurred when loading failed tests.
                          </div>;
                      }
                  })()}
                </Collapsible>
                <Collapsible trigger="WSO2 Server logs" lazyRender={true}>
                  {(() => {
                    return <div>
                      <p style={{float: 'right'}}><a href={DEPL_LOGS_URL} target="_blank">View Kibana Dashboard</a></p>
                      <br/>
                      <iframe src={DEPL_LOGS_URL} height="900" width="100%" title="wso2serverlogs"></iframe>
                      <br/>
                    </div>
                  })()}
                </Collapsible>
                <Collapsible trigger="Performance Data" lazyRender={true}>
                  {(() => {
                    return <div>
                      <br/>
                      <iframe src={PERFDASH_URL} height="1500" width="1200" title="perfmetrics"></iframe>
                      <br/>
                    </div>
                  })()}
                </Collapsible>
                {(() => {
                  if (this.state.TruncatedRunLogUrlStatus && this.state.TruncatedRunLogUrlStatus === HTTP_OK) {
                    return <Collapsible trigger="Test-Run log summary" lazyRender={true}>
                      <div id="logConsoleFrame">
                        <iframe id="logConsole" title={"Test-Run Log"} style={{
                          height: "500px",
                          width: "100%"
                        }} src={turncatedRunLogUrl}></iframe>
                      </div>
                    </Collapsible>;
                  }
                })()}
              </CardMedia>
            }
          })()}
          <br/>
          <br/>
        </Card>
      </div>
    );
  }
}

export default TestRunView;
