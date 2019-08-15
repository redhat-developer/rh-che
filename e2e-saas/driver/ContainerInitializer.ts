/*********************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
import { Container } from 'inversify';
import { TYPES, ICheLoginPage, ITestWorkspaceUtil } from 'e2e';
import { RhCheLoginPage } from '../pageobjects/RhCheLoginPage';
import { RhCheTestWorkspaceUtils } from '../utils/RhCheTestWorkspaceUtils';
import * as path from 'path';

export function getContainer(): Container {
    let pathh = path.resolve('.');
    let containerInitializerUpstream = require(`${pathh}/node_modules/e2e/dist/driver/ContainerInitializer.js`);
    let e2eContainer : Container = containerInitializerUpstream.getContainer();

    e2eContainer.unbind(TYPES.CheLogin);
    e2eContainer.bind<ICheLoginPage>(TYPES.CheLogin).to(RhCheLoginPage).inSingletonScope();

    e2eContainer.unbind(TYPES.WorkspaceUtil);
    e2eContainer.bind<ITestWorkspaceUtil>(TYPES.WorkspaceUtil).to(RhCheTestWorkspaceUtils).inSingletonScope();

    return e2eContainer;
}
