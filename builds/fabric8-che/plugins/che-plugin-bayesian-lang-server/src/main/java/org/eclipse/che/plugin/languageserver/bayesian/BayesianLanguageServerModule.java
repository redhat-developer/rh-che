/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.plugin.languageserver.bayesian;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.eclipse.che.api.languageserver.launcher.LanguageServerLauncher;
import org.eclipse.che.api.languageserver.shared.model.LanguageDescription;
import org.eclipse.che.inject.DynaModule;
import org.eclipse.che.plugin.languageserver.bayesian.server.launcher.BayesianLanguageServerLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.asList;

/**
 * 'Test' Language Server Module
 */
@DynaModule
public class BayesianLanguageServerModule extends AbstractModule {

    private final static Logger  LOGGER      = LoggerFactory.getLogger(BayesianLanguageServerModule.class);

    public static final String   TXT_LANGUAGE_ID = "text";

    public static final String[] FILE_EXTENSIONS  = new String[]{"txt"};

	@Override
	protected void configure() {
		LOGGER.info("Configuring " + this.getClass().getName());
		Multibinder.newSetBinder(binder(), LanguageServerLauncher.class).addBinding()
				.to(BayesianLanguageServerLauncher.class);
        LanguageDescription description = new LanguageDescription();
        description.setFileExtensions(asList(FILE_EXTENSIONS));
        description.setLanguageId(TXT_LANGUAGE_ID);
        description.setMimeType("text/plain");
        Multibinder.newSetBinder(binder(), LanguageDescription.class).addBinding().toInstance(description);
    }
}
