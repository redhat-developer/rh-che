/*********************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
import 'reflect-metadata';
import { TYPES, CLASSES, TestConstants, ILoginPage, Dashboard, Editor, Ide, NameGenerator , NewWorkspace, ProjectTree } from 'e2e';
import { rhCheContainer,  } from '../inversify.config';
import { error } from 'selenium-webdriver';
import * as restClient from 'typed-rest-client/RestClient';
import { RhCheTestConstants } from '../RhCheTestConstants';

const workspaceName: string = NameGenerator.generate('wksp-test-', 5);
const namespace: string = TestConstants.TS_SELENIUM_USERNAME;
const sampleName: string = 'console-java-simple';
const fileFolderPath: string = `${sampleName}/src/main/java/org/eclipse/che/examples`;
const tabTitle: string = 'HelloWorld.java';

const loginPage: ILoginPage = rhCheContainer.get<ILoginPage>(TYPES.LoginPage);
const dashboard: Dashboard = rhCheContainer.get(CLASSES.Dashboard);
const newWorkspace: NewWorkspace = rhCheContainer.get(CLASSES.NewWorkspace);
const ide: Ide = rhCheContainer.get(CLASSES.Ide);
const projectTree: ProjectTree = rhCheContainer.get(CLASSES.ProjectTree);
const editor: Editor = rhCheContainer.get(CLASSES.Editor);

suite('RhChe E2E', async () => {
    suite('Login and wait dashboard', async () => {
        test('Login', async () => {
            await loginPage.login();
            await dashboard.waitPage(30000);
        });
    });
    
    suite('Create and run workspace', async () => {
        test(`Open 'New Workspace' page`, async () => {
            await newWorkspace.openPageByUI();
        });

        test(`Create and start '${workspaceName}' `, async () => {
            var namespace = TestConstants.TS_SELENIUM_USERNAME;
            await newWorkspace.createAndRunWorkspace(namespace, workspaceName, 'Java Maven', sampleName);
        });

    });

    suite('Work with IDE', async () => {
        test('Wait IDE availability', async () => {
            await ide.waitWorkspaceAndIde(namespace, workspaceName);
        });

        test('Open project tree container', async () => {
            await projectTree.openProjectTreeContainer();
        });

        test('Wait project imported', async () => {
            try {
                await projectTree.waitProjectImported(sampleName, 'src');
            } catch (err) {
                if (!(err instanceof error.TimeoutError)) {
                    throw err;
                }
                var rest: restClient.RestClient = new restClient.RestClient('user-agent');
                const workspaceStatusApiUrl: string = `${TestConstants.TS_SELENIUM_BASE_URL}/api/workspace/${namespace}:${workspaceName}`;

                const response: restClient.IRestResponse<any> = await rest.get(workspaceStatusApiUrl, {additionalHeaders: {'Authorization' : 'Bearer ' + RhCheTestConstants.E2E_SAAS_TESTS_USER_TOKEN } });
                console.log('WORKSPACE DEFINITION: ' + JSON.stringify(response.result));
                throw err;
            }
        });

        test('Expand project and open file in editor', async () => {
            await projectTree.expandPathAndOpenFile(fileFolderPath, tabTitle);
        });

        test.skip('Check "Java Language Server" initialization by statusbar', async () => {
            await ide.waitStatusBarContains('Starting Java Language Server');
            await ide.waitStatusBarContains('100% Starting Java Language Server');
            await ide.waitStatusBarTextAbcence('Starting Java Language Server');
        });

        test('Check "Java Language Server" initialization by suggestion invoking', async () => {
            await ide.closeAllNotifications();
            await editor.waitEditorAvailable(tabTitle);
            await editor.clickOnTab(tabTitle);
            await editor.waitEditorAvailable(tabTitle);
            await editor.waitTabFocused(tabTitle);
            await editor.moveCursorToLineAndChar(tabTitle, 6, 20);
            await editor.pressControlSpaceCombination(tabTitle);
            await editor.waitSuggestion(tabTitle, 'append(CharSequence csq, int start, int end) : PrintStream');
        });

    });

    suite('Stop and remove workspace', async () => {
        test('Stop workspace', async () => {
            await dashboard.stopWorkspaceByUI(workspaceName);
        });

        test('Delete workspace', async () => {
            await dashboard.deleteWorkspaceByUI(workspaceName);
        });

    });

});
