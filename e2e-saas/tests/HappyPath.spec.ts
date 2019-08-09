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
import { TYPES, CLASSES, TestConstants, ICheLoginPage, Dashboard, Editor, Ide, NameGenerator , NewWorkspace, ProjectTree, TopMenu, QuickOpenContainer, Terminal } from 'e2e';
import { rhCheContainer,  } from '../inversify.config';
import { error, Key } from 'selenium-webdriver';
import * as restClient from 'typed-rest-client/RestClient';
import { RhCheTestConstants } from '../RhCheTestConstants';

const workspaceName: string = NameGenerator.generate('wksp-test-', 5);
const namespace: string = TestConstants.TS_SELENIUM_USERNAME;
const sampleName: string = 'console-java-simple';
const fileFolderPath: string = `${sampleName}/src/main/java/org/eclipse/che/examples`;
const tabTitle: string = 'HelloWorld.java';
const codeNavigationClassName: string = 'String.class';

const loginPage: ICheLoginPage = rhCheContainer.get<ICheLoginPage>(TYPES.CheLogin);
const dashboard: Dashboard = rhCheContainer.get(CLASSES.Dashboard);
const newWorkspace: NewWorkspace = rhCheContainer.get(CLASSES.NewWorkspace);
const ide: Ide = rhCheContainer.get(CLASSES.Ide);
const projectTree: ProjectTree = rhCheContainer.get(CLASSES.ProjectTree);
const editor: Editor = rhCheContainer.get(CLASSES.Editor);
const topMenu: TopMenu = rhCheContainer.get(CLASSES.TopMenu);
const quickOpenContainer: QuickOpenContainer = rhCheContainer.get(CLASSES.QuickOpenContainer);
const terminal: Terminal = rhCheContainer.get(CLASSES.Terminal);

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

        test('Create and open workspace', async () => {
            await newWorkspace.createAndOpenWorkspace(workspaceName, 'Java Maven');
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
    });

    suite('Validation of workspace build and run', async () => {
        test('Build application', async () => {
            await runTask('che: maven build');
            await ide.waitNotification('Task 0 has exited with code 0.', 30000);
            await ide.waitNotificationDisappearance('Task 0 has exited with code 0.', 5, 5000);
        });

        test('Close the terminal tasks', async () => {
            await terminal.closeTerminalTab('maven build');
        });

        test('Run application', async () => {
            await runTask('che: maven build and run');
            await ide.waitNotification('Task 1 has exited with code 0.', 30000);
            await ide.waitNotificationDisappearance('Task 1 has exited with code 0.', 5, 5000);
        });

        test('Close the terminal tasks', async () => {
            await terminal.closeTerminalTab('maven build and run');
        });
    });

    suite('Language server validation', async () => {
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

        test('Error highlighting', async () => {
            await editor.type(tabTitle, 'error', 7);
            await editor.waitErrorInLine(7);
            await editor.performKeyCombination(tabTitle, Key.chord(Key.BACK_SPACE, Key.BACK_SPACE, Key.BACK_SPACE, Key.BACK_SPACE, Key.BACK_SPACE));
            await editor.waitErrorInLineDisappearance(7);
        });

        test('Autocomplete', async () => {
            await editor.moveCursorToLineAndChar(tabTitle, 6, 11);
            await editor.pressControlSpaceCombination(tabTitle);
            await editor.waitSuggestionContainer();
            await editor.waitSuggestion(tabTitle, 'System - java.lang');
        });

        test('Codenavigation', async () => {
            await editor.moveCursorToLineAndChar(tabTitle, 5, 10);
            await editor.performKeyCombination(tabTitle, Key.chord(Key.CONTROL, Key.F12));
            await editor.waitEditorAvailable(codeNavigationClassName);
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

    async function runTask(task: string) {
        await topMenu.selectOption('Terminal', 'Run Task...');
        try {
            await quickOpenContainer.waitContainer();
        } catch (err) {
            if (err instanceof error.TimeoutError) {
                console.warn(`After clicking to the "Terminal" -> "Run Task ..." the "Quick Open Container" has not been displayed, one more try`);

                await topMenu.selectOption('Terminal', 'Run Task...');
                await quickOpenContainer.waitContainer();
            }
        }

        await quickOpenContainer.clickOnContainerItem(task);
    }
});
