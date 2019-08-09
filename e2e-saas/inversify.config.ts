import { ICheLoginPage, TYPES, CLASSES, inversifyConfig } from 'e2e';
import { RhCheLoginPage } from './pageobjects/RhCheLoginPage';
import { TestWorkspaceUtils } from './utils/TestWorkspaceUtils';

const e2eContainer = inversifyConfig.e2eContainer;
e2eContainer.unbind(TYPES.CheLogin);
e2eContainer.bind<ICheLoginPage>(TYPES.CheLogin).to(RhCheLoginPage).inSingletonScope();

e2eContainer.unbind(CLASSES.TestWorkspaceUtil);

e2eContainer.bind<TestWorkspaceUtils>(CLASSES.TestWorkspaceUtil).to(TestWorkspaceUtils).inSingletonScope();
const rhCheContainer = e2eContainer;

export { rhCheContainer };
