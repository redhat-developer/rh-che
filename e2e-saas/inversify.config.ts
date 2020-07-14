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
import { inversifyConfig, TYPES, ICheLoginPage, ITokenHandler } from 'e2e';
import { RhCheLoginPage } from './pageobjects/RhCheLoginPage';
import { RhCheTokenHandler } from './utils/RhCheTokenHandler';

const rhcheContainer: Container = inversifyConfig.e2eContainer;

rhcheContainer.rebind<ICheLoginPage>(TYPES.CheLogin).to(RhCheLoginPage);
rhcheContainer.rebind<ITokenHandler>(TYPES.ITokenHandler).to(RhCheTokenHandler);
console.log('ITokenHandler rebinded to the downstream implementation.');

export { rhcheContainer };
