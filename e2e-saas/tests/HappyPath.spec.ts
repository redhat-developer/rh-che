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
import { TYPES, CLASSES, TestConstants, ILoginPage, Dashboard, Editor, Ide, NameGenerator , NewWorkspace, WorkspaceDetailsPlugins, ProjectTree } from 'e2e';
import { rhCheContainer,  } from '../inversify.config';

const workspaceName: string = NameGenerator.generate('wksp-test-', 5);
const namespace: string = TestConstants.TS_SELENIUM_USERNAME;
const sampleName: string = 'console-java-simple';
const pluginId: string = 'redhat/java/0.45.0';
const pluginVersion: string = '0.45.0';
const javaPluginName: string = 'Language Support for Java(TM)';
const fileFolderPath: string = `${sampleName}/src/main/java/org/eclipse/che/examples`;
const tabTitle: string = 'HelloWorld.java';

const loginPage: ILoginPage = rhCheContainer.get<ILoginPage>(TYPES.LoginPage);
const dashboard: Dashboard = rhCheContainer.get(CLASSES.Dashboard);
const newWorkspace: NewWorkspace = rhCheContainer.get(CLASSES.NewWorkspace);
const workspaceDetailsPlugins: WorkspaceDetailsPlugins = rhCheContainer.get(CLASSES.WorkspaceDetailsPlugins);
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

    suite('Create workspace and add plugin', async () => {
        test(`Open 'New Workspace' page`, async () => {
            await newWorkspace.openPageByUI();
        });

        test(`Create a '${workspaceName}' workspace and proceed editing`, async () => {
            await newWorkspace.createWorkspaceAndProceedEditing(workspaceName, 'che7', sampleName);
        });

        test(`Add 'Java Language Support' plugin to workspace`, async () => {
            await workspaceDetailsPlugins.addPluginAndOpenWorkspace(namespace, workspaceName, javaPluginName, pluginId, pluginVersion);
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
            await projectTree.waitProjectImported(sampleName, 'src');
        });

        test('Expand project and open file in editor', async () => {
            await projectTree.expandPathAndOpenFile(fileFolderPath, tabTitle);
        });

        // unskip after resolving issue https://github.com/eclipse/che/issues/12904
        test.skip(`Check 'Java Language Server' initialization by statusbar`, async () => {
            await ide.waitStatusBarContains('Starting Java Language Server');
            await ide.waitStatusBarContains('100% Starting Java Language Server');
            await ide.waitStatusBarTextAbcence('Starting Java Language Server');
        });

        // unskip after resolving issue https://github.com/eclipse/che/issues/12904
        test.skip(`Check 'Java Language Server' initialization by suggestion invoking`, async () => {
            await editor.waitEditorAvailable(tabTitle);
            await editor.clickOnTab(tabTitle);
            await editor.waitEditorAvailable(tabTitle);
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
