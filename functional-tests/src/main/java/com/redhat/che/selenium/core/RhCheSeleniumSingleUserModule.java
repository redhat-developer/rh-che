package com.redhat.che.selenium.core;

import com.google.inject.AbstractModule;
import org.eclipse.che.selenium.core.client.CheTestAuthServiceClient;
import org.eclipse.che.selenium.core.client.DummyCheTestMachineServiceClient;
import org.eclipse.che.selenium.core.client.TestAuthServiceClient;
import org.eclipse.che.selenium.core.client.TestMachineServiceClient;
import org.eclipse.che.selenium.core.provider.DefaultTestUserProvider;
import org.eclipse.che.selenium.core.user.SingleUserCheDefaultTestUserProvider;

public class RhCheSeleniumSingleUserModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(TestAuthServiceClient.class).to(CheTestAuthServiceClient.class);
    bind(TestMachineServiceClient.class).to(DummyCheTestMachineServiceClient.class);
    bind(DefaultTestUserProvider.class).to(SingleUserCheDefaultTestUserProvider.class);
  }

}
