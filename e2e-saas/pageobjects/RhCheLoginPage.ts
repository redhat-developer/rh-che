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
import { ILoginPage, TYPES, CLASSES, IDriver, TestConstants, DriverHelper } from 'e2e';
import { injectable, inject } from 'inversify';
import { ThenableWebDriver } from 'selenium-webdriver';
import { By } from 'selenium-webdriver';

const USERNAME_INPUT_ID : string = 'username';
const PASSWORD_INPUT_ID : string = 'password';
const NEXT_BUTTON_ID : string = 'login-show-step2';
const LOGIN_BUTTON_ID : string = 'kc-login';

@injectable()
export class RhCheLoginPage implements ILoginPage {
    constructor(
        @inject(CLASSES.DriverHelper) private readonly driverHelper: DriverHelper,
        @inject(TYPES.Driver) private readonly driver: IDriver) { }

    async login(timeout: number = 20) {
        const webDriver: ThenableWebDriver = this.driver.get();
        await webDriver.navigate().to(TestConstants.TS_SELENIUM_BASE_URL);

        const usernameFieldLocator: By = By.id(USERNAME_INPUT_ID);
        await this.driverHelper.waitVisibility(usernameFieldLocator, 20000);
        await this.driverHelper.enterValue(usernameFieldLocator, TestConstants.TS_SELENIUM_USERNAME, timeout );

        const nextButtonLocator: By = By.id(NEXT_BUTTON_ID);
        await this.driverHelper.waitAndClick(nextButtonLocator, timeout);

        const passwordFieldLocator: By = By.id(PASSWORD_INPUT_ID);
        await this.driverHelper.waitVisibility(passwordFieldLocator, 3000);
        await this.driverHelper.enterValue(passwordFieldLocator, TestConstants.TS_SELENIUM_PASSWORD, timeout );

        const loginButtonLocator: By = By.id(LOGIN_BUTTON_ID);
        await this.driverHelper.waitAndClick(loginButtonLocator, timeout);
    }
}
