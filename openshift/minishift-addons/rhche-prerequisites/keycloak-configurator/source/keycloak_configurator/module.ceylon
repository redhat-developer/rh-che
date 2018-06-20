/*
 * Copyright (c) 2016-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */

suppressWarnings("packageName")
native("jvm")
license("Eclipse Public License v1.0")
by("David Festal")
module keycloak_configurator "1.0.0" {
	shared import maven:"org.keycloak:keycloak-admin-client" "3.4.3.Final";
	shared import maven:"org.keycloak:keycloak-services" "3.4.3.Final";
	shared import maven:"org.jboss.resteasy:resteasy-jackson2-provider" "3.0.24.Final";
	shared import java.base "8";
	shared import ceylon.interop.java "1.3.3";
}
