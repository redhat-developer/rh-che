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
import { injectable, inject } from 'inversify';
import { ProjectTree, TestConstants, Editor, CLASSES, DriverHelper, Ide } from 'e2e';

@injectable()
export class RhCheProjectTree extends ProjectTree {

    /* tslint:disable no-unused-variable*/
    constructor(
        @inject(CLASSES.DriverHelper) private readonly rhCheDriverHelper: DriverHelper,
        @inject(CLASSES.Ide) private readonly rhCheIde: Ide,
        @inject(CLASSES.Editor) private readonly rhCheEditor: Editor) {
        super(rhCheDriverHelper, rhCheIde, rhCheEditor);
    }
    /* tslint:enable */

    async expandPathAndOpenFile(pathToItem: string, fileName: string, timeout: number = TestConstants.TS_SELENIUM_DEFAULT_TIMEOUT) {

        console.log('In Rh-CHe Project tree method expandPathAndOpenFile');
        let items: Array<string> = pathToItem.split('/');
        let projectName: string = items[0];
        let paths: Array<string> = new Array();
        paths.push(projectName);

        // make direct path for each project tree item
        for (let i = 1; i < items.length; i++) {
            let item = items[i];
            projectName = `${projectName}/${item}`;
            paths.push(projectName);
        }

        // expand each project tree item
        for (const path of paths) {
            await this.expandItem(path, timeout);
        }

        // open file
        await this.clickOnItem(`${pathToItem}/${fileName}`, timeout);

        // check file appearance in the editor
        await this.rhCheEditor.waitEditorOpened(fileName, timeout);
        await this.rhCheEditor.waitTab(fileName);
    }

}
