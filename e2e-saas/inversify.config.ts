import { ILoginPage, TYPES, CLASSES, inversifyConfig } from 'e2e';
import { RhCheLoginPage } from './pageobjects/RhCheLoginPage';
import { TestWorkspaceUtils } from './utils/TestWorkspaceUtils';

const e2eContainer = inversifyConfig.e2eContainer;
e2eContainer.unbind(TYPES.LoginPage);
e2eContainer.bind<ILoginPage>(TYPES.LoginPage).to(RhCheLoginPage).inSingletonScope();

e2eContainer.unbind(CLASSES.TestWorkspaceUtil);

e2eContainer.bind<TestWorkspaceUtils>(CLASSES.TestWorkspaceUtil).to(TestWorkspaceUtils).inSingletonScope();
const rhCheContainer = e2eContainer;

export { rhCheContainer };
