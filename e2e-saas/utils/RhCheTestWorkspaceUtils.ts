/*********************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/

import { injectable, inject } from 'inversify';
import { DriverHelper, CLASSES, TestConstants, ITestWorkspaceUtil, WorkspaceStatus } from 'e2e';
import 'reflect-metadata';
import * as rm from 'typed-rest-client/RestClient';
import { RhCheTestConstants } from '../RhCheTestConstants';

@injectable()
export class RhCheTestWorkspaceUtils implements ITestWorkspaceUtil {

    constructor(@inject(CLASSES.DriverHelper) private readonly driverHelper: DriverHelper,
        private readonly rest: rm.RestClient = new rm.RestClient('rest-samples')) {
            rest = new rm.RestClient('rest-samples');
         }

    public async cleanUpAllWorkspaces() {
        let id : string = await this.getIdOfRunningWorkspace();
        await this.stopWorkspaceById(id);
        await this.removeWorkspaceById(id);

    }

    public async getIdOfRunningWorkspaces(): Promise<string[]> {
        // using the same method as upstream has would be possible with having getCheBearerToken() in ITestWorkspaceUtil
        // then we can call upstream getIdOfRunningWorkspaces and override the getCheBearerToken 
        // that will also result in simplifying other methods
        return [ await this.getIdOfRunningWorkspace()];
    }

    public async waitWorkspaceStatus(namespace: string, workspaceName: string, expectedWorkspaceStatus: WorkspaceStatus) {
        const workspaceStatusApiUrl: string = `${TestConstants.TS_SELENIUM_BASE_URL}/api/workspace/${namespace}:${workspaceName}`;
        const attempts: number = TestConstants.TS_SELENIUM_WORKSPACE_STATUS_ATTEMPTS;
        const polling: number = TestConstants.TS_SELENIUM_WORKSPACE_STATUS_POLLING;

        for (let i = 0; i < attempts; i++) {
            const response: rm.IRestResponse<any> = await this.rest.get(workspaceStatusApiUrl, {additionalHeaders: {'Authorization' : 'Bearer ' + RhCheTestConstants.E2E_SAAS_TESTS_USER_TOKEN } });

            if (response.statusCode !== 200) {
                await this.driverHelper.wait(polling);
                continue;
            }

            const workspaceStatus: string = await response.result.status;

            if (workspaceStatus === expectedWorkspaceStatus) {
                return;
            }

            await this.driverHelper.wait(polling);
        }

        throw new Error(`Exceeded the maximum number of checking attempts, workspace status is different to '${expectedWorkspaceStatus}'`);
    }

    public async waitPluginAdding(namespace: string, workspaceName: string, pluginName: string) {
        const workspaceStatusApiUrl: string = `${TestConstants.TS_SELENIUM_BASE_URL}/api/workspace/${namespace}:${workspaceName}`;
        const attempts: number = TestConstants.TS_SELENIUM_PLUGIN_PRECENCE_ATTEMPTS;
        const polling: number = TestConstants.TS_SELENIUM_PLUGIN_PRECENCE_POLLING;

        for (let i = 0; i < attempts; i++) {
            const response: rm.IRestResponse<any> = await this.rest.get(workspaceStatusApiUrl, {additionalHeaders: {'Authorization' : 'Bearer ' + RhCheTestConstants.E2E_SAAS_TESTS_USER_TOKEN } });

            if (response.statusCode !== 200) {
                await this.driverHelper.wait(polling);
                continue;
            }

            const machines: string = JSON.stringify(response.result.runtime.machines);
            const isPluginPresent: boolean = machines.search(pluginName) > 0;

            if (isPluginPresent) {
                break;
            }

            if (i === attempts - 1) {
                throw new Error(`Exceeded maximum tries attempts, the '${pluginName}' plugin is not present in the workspace runtime.`);
            }

            await this.driverHelper.wait(polling);
        }
    }

    public async getIdOfRunningWorkspace(): Promise<string> {
        console.log('Hello from DOWNSTREAM getidofrounningworkspace');
        const getAllWorkspacesApiUrl: string = `${TestConstants.TS_SELENIUM_BASE_URL}/api/workspace`;
        const getAllWorkspacesResponse: rm.IRestResponse<any> = await this.rest.get(getAllWorkspacesApiUrl, {additionalHeaders: {'Authorization' : 'Bearer ' + RhCheTestConstants.E2E_SAAS_TESTS_USER_TOKEN } });
        interface IMyObj {
            id: string;
            status: string;
        }
        let stringified = JSON.stringify(getAllWorkspacesResponse.result);
        let arrayOfWorkspaces = <IMyObj[]>JSON.parse(stringified);
        let idOfRunningWorkspace : string = '';
        for (let entry of arrayOfWorkspaces) {
            if (entry.status === 'RUNNING') {
                idOfRunningWorkspace = entry.id;
            }
        }

        return idOfRunningWorkspace;
    }

    public async removeWorkspaceById(id: string) {
        const getInfoURL: string = `${TestConstants.TS_SELENIUM_BASE_URL}/api/workspace/${id}`;
        const attempts: number = TestConstants.TS_SELENIUM_PLUGIN_PRECENCE_ATTEMPTS;
        const polling: number = TestConstants.TS_SELENIUM_PLUGIN_PRECENCE_POLLING;

        let stopped : Boolean = false;
        for (let i = 0; i < attempts; i++) {
            const getInfoResponse: rm.IRestResponse<any> = await this.rest.get(getInfoURL, {additionalHeaders: {'Authorization' : 'Bearer ' + RhCheTestConstants.E2E_SAAS_TESTS_USER_TOKEN } });
            if ( getInfoResponse.result.status === 'STOPPED') {
                stopped = true;
                break;
            }

            await this.driverHelper.wait(polling);
        }

        if (stopped) {
        const deleteWorkspaceApiUrl: string = `${TestConstants.TS_SELENIUM_BASE_URL}/api/workspace/${id}`;
        const deleteWorkspaceResponse: rm.IRestResponse<any> = await this.rest.del(deleteWorkspaceApiUrl, {additionalHeaders: {'Authorization' : 'Bearer ' + RhCheTestConstants.E2E_SAAS_TESTS_USER_TOKEN } });

        // response code 204: "No Content" expected
        if (deleteWorkspaceResponse.statusCode !== 204) {
            throw new Error(`Can not remove workspace. Code: ${deleteWorkspaceResponse.statusCode} Result: ${deleteWorkspaceResponse.result}`);
        }} else {
            throw new Error(`Can not remove workspace with id ${id}, because it is still not in STOPPED state.`);
        }
    }

    public async stopWorkspaceById(id: string) {
        const stopWorkspaceApiUrl: string = `${TestConstants.TS_SELENIUM_BASE_URL}/api/workspace/${id}/runtime`;
        const stopWorkspaceResponse: rm.IRestResponse<any> = await this.rest.del(stopWorkspaceApiUrl, {additionalHeaders: {'Authorization' : 'Bearer ' + RhCheTestConstants.E2E_SAAS_TESTS_USER_TOKEN } });

        // response code 204: "No Content" expected
        if (stopWorkspaceResponse.statusCode !== 204) {
            throw new Error(`Can not stop workspace. Code: ${stopWorkspaceResponse.statusCode} Result: ${stopWorkspaceResponse.result}`);
        }
    }

    removeWorkspace(namespace: string, workspaceId: string): void {
        throw new Error('Method not implemented.');
    }

    stopWorkspace(namespace: string, workspaceId: string): void {
        throw new Error('Method not implemented.');
    }

}
