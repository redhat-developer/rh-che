/*********************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/

 import { ICheLoginPage, inversifyConfig, TYPES, CLASSES, Dashboard } from 'e2e';
import { Container } from 'inversify';

 const e2eContainer: Container = inversifyConfig.e2eContainer;
 const loginPage: ICheLoginPage = e2eContainer.get<ICheLoginPage>(TYPES.CheLogin);
 const dashboard: Dashboard = e2eContainer.get(CLASSES.Dashboard);

suite('Login and wait dashboard', async () => {
    test('Login', async () => {
        await loginPage.login();
        await dashboard.waitPage(30000);
    });
});
