/*********************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
import { NameGenerator, TestConstants, CLASSES, inversifyConfig, Dashboard, NewWorkspace, Ide, ProjectTree, Editor, TopMenu, QuickOpenContainer, Terminal } from 'e2e';
import 'reflect-metadata';
import { error, Key } from 'selenium-webdriver';
import * as restClient from 'typed-rest-client/RestClient';
import { RhCheTestConstants } from '../RhCheTestConstants';

const workspaceName: string = NameGenerator.generate('wksp-test-', 5);
const namespace: string = TestConstants.TS_SELENIUM_USERNAME;
const sampleName: string = 'java-web-vertx';
const fileFolderPath: string = `${sampleName}/src/main/java/io/vertx/examples/spring`;
const tabTitle: string = 'SpringExampleRunner.java';
const codeNavigationClassName: string = 'ApplicationContext.class';

const e2eContainer = inversifyConfig.e2eContainer;
const dashboard: Dashboard = e2eContainer.get(CLASSES.Dashboard);
const newWorkspace: NewWorkspace = e2eContainer.get(CLASSES.NewWorkspace);
const ide: Ide = e2eContainer.get(CLASSES.Ide);
const projectTree: ProjectTree = e2eContainer.get(CLASSES.ProjectTree);
const editor: Editor = e2eContainer.get(CLASSES.Editor);
const topMenu: TopMenu = e2eContainer.get(CLASSES.TopMenu);
const quickOpenContainer: QuickOpenContainer = e2eContainer.get(CLASSES.QuickOpenContainer);
const terminal: Terminal = e2eContainer.get(CLASSES.Terminal);

const vertxTasks: string = '{ \
    "tasks": [ \n \
        { \n \
            "type": "che", \n \
            "label": "run app", \n \
            "command": "JDBC_URL=jdbc:h2:/tmp/db \\\njava -jar -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 \\\n./target/*fat.jar\n", \n \
            "target": { \n \
                "workingDir": "${CHE_PROJECTS_ROOT}/java-web-vertx", \n \
                "containerName": "maven" \n \
            } \n \
        }, \n \
        { \n \
            "type": "che", \n \
            "label": "maven build", \n \
            "command": "mvn -Duser.home=${HOME} clean install  > ${CHE_PROJECTS_ROOT}/java-web-vertx/output.txt ", \n \
            "target": { \n \
                "workingDir": "${CHE_PROJECTS_ROOT}/java-web-vertx", \n \
                "containerName": "maven" \n \
            } \n \
        } \n \
    ] \n \
}';

suite('RhChe E2E Java Vert.x test', async () => {
    suite('Create Java Vert.x workspace ' + workspaceName, async () => {
        test('Open \'New Workspace\' page', async () => {
            await newWorkspace.openPageByUI();
        });

        test('Create and open workspace', async () => {
            await newWorkspace.createAndOpenWorkspace(workspaceName, 'Java Vert.x');
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

    });

    suite('Validation of project build', async () => {
        // workaround for pop-up not shown  https://github.com/eclipse/che/issues/14724 remove all tests once it is fixed
        test('Workaround for pop-up not shown', async () => {
            let taskName: string = 'che: maven build';
            let tasksFile: string = 'tasks.json';
            await topMenu.selectOption('Terminal', 'Configure Tasks...');
            await quickOpenContainer.clickOnContainerItem(taskName);
            await editor.waitEditorOpened(tasksFile);
            await editor.performKeyCombination(tasksFile, Key.chord(Key.CONTROL, 'a'));
            await editor.performKeyCombination(tasksFile, Key.DELETE);
            await editor.performKeyCombination(tasksFile, vertxTasks);

            await editor.performKeyCombination(tasksFile, Key.chord(Key.CONTROL, 's'));
            await editor.waitTabWithSavedStatus(tasksFile);
        });

        test('Build application', async () => {
            let taskName: string = 'che: maven build';
            await runTask(taskName);
            await quickOpenContainer.clickOnContainerItem('Continue without scanning the task output');

            // replace next two lines by commented line when pop-up issue is fixed
            // await ide.waitNotification('Task ' + taskName + ' has exited with code 0.', 60000);
            await projectTree.expandPathAndOpenFileInAssociatedWorkspace(sampleName, 'output.txt');
            await editor.followAndWaitForText('output.txt', '[INFO] BUILD SUCCESS', 220000, 5000);
        });

        test('Close the terminal tasks', async () => {
            await terminal.closeTerminalTab('maven build');
        });
    });

    suite('Language server validation', async () => {
        test('Expand project and open file in editor', async () => {
            await projectTree.expandPathAndOpenFileInAssociatedWorkspace(fileFolderPath, tabTitle);
            await editor.selectTab(tabTitle);
        });

        test('Java LS initialization', async () => {
            // await ide.checkLsInitializationStart('Starting Java Language Server');
            await ide.waitStatusBarContains('Starting Java Language Server', 20000);
            await ide.waitStatusBarTextAbsence('Starting Java Language Server', 1800000);
            await ide.waitStatusBarTextAbsence('Building workspace', 360000);
        });

        test('Suggestion invoking', async () => {
            await ide.closeAllNotifications();
            await editor.waitEditorAvailable(tabTitle);
            await editor.clickOnTab(tabTitle);
            await editor.waitEditorAvailable(tabTitle);
            await editor.waitTabFocused(tabTitle);
            await editor.moveCursorToLineAndChar(tabTitle, 19, 11);
            await editor.pressControlSpaceCombination(tabTitle);
            await editor.waitSuggestion(tabTitle, 'cancelTimer(long arg0) : boolean');
        });

        test('Error highlighting', async () => {
            await editor.type(tabTitle, 'error', 21);
            await editor.waitErrorInLine(21);
            await editor.performKeyCombination(tabTitle, Key.chord(Key.BACK_SPACE, Key.BACK_SPACE, Key.BACK_SPACE, Key.BACK_SPACE, Key.BACK_SPACE));
            await editor.waitErrorInLineDisappearance(21);
        });

        test('Autocomplete', async () => {
            await editor.moveCursorToLineAndChar(tabTitle, 18, 15);
            await editor.pressControlSpaceCombination(tabTitle);
            await editor.waitSuggestionContainer();
            await editor.waitSuggestion(tabTitle, 'Vertx - io.vertx.core');
        });

        test('Codenavigation', async () => {
            await editor.moveCursorToLineAndChar(tabTitle, 17, 15);
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
