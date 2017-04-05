/*******************************************************************************
 * Copyright (c) 2017 Red Hat.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat - Initial Contribution
 *******************************************************************************/
package com.redhat.bayesian.agent;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.agent.shared.model.Agent;
import org.eclipse.che.api.agent.shared.model.impl.BasicAgent;

import java.io.IOException;

/**
 * Bayesian Language Server
 *
 * @see Agent
 *
 * @author Pavel Odvody
 */
@Singleton
public class BayesianAgent extends BasicAgent {
    private static final String AGENT_DESCRIPTOR = "com.redhat.bayesian.lsp.json";
    private static final String AGENT_SCRIPT     = "com.redhat.bayesian.lsp.script.sh";

    @Inject
    public BayesianAgent() throws IOException {
        super(AGENT_DESCRIPTOR, AGENT_SCRIPT);
    }
}
