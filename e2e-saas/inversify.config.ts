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
import { inversifyConfig, TYPES, ICheLoginPage, ITestWorkspaceUtil, CLASSES } from 'e2e';
import { RhCheLoginPage } from './pageobjects/RhCheLoginPage';
import { RhCheTestWorkspaceUtils } from './utils/RhCheTestWorkspaceUtils';

const rhcheContainer: Container = inversifyConfig.e2eContainer;

rhcheContainer.unbind(TYPES.CheLogin);
rhcheContainer.bind<ICheLoginPage>(TYPES.CheLogin).to(RhCheLoginPage).inSingletonScope();

rhcheContainer.unbind(TYPES.WorkspaceUtil);
rhcheContainer.bind<ITestWorkspaceUtil>(TYPES.WorkspaceUtil).to(RhCheTestWorkspaceUtils).inSingletonScope();

export { rhcheContainer };
