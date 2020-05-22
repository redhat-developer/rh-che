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
import { NameGenerator} from 'e2e';
import * as testLibrary from 'e2e';

const workspaceName: string = NameGenerator.generate('wksp-test-', 5);
const sampleName: string = 'console-java-simple';
const fileFolderPath: string = `${sampleName}/src/main/java/org/eclipse/che/examples`;
const tabTitle: string = 'HelloWorld.java';
const codeNavigationClassName: string = 'String.class';
const stack : string = 'Java Maven';
const taskName: string = 'maven build';

suite(`${stack} test`, async () => {
    suite (`Create ${stack} workspace ${workspaceName}`, async () => {
        testLibrary.createAndOpenWorkspace(workspaceName, stack);
        testLibrary.waitWorkspaceReadiness(workspaceName, sampleName, 'src');
    });

    suite('Validation of workspace build and run', async () => {
        testLibrary.runTask(taskName, 120_000);
        testLibrary.closeTerminal(taskName);
    });

    suite('Language server validation', async () => {
        testLibrary.openFile(fileFolderPath, tabTitle);
        testLibrary.suggestionInvoking(tabTitle, 10, 20, 'append(char c) : PrintStream');
        testLibrary.errorHighlighting(tabTitle, 'erroneous_text', 11);
        testLibrary.autocomplete(tabTitle, 10, 11, 'System - java.lang');
        testLibrary.codeNavigation(tabTitle, 9, 10, codeNavigationClassName);
    });

    suite ('Stop and remove workspace', async() => {
        testLibrary.stopWorkspace(workspaceName);
        testLibrary.removeWorkspace(workspaceName);
    });
});
