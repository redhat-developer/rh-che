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
package com.redhat.che.plugin.product.info.client;

import com.google.gwt.resources.client.ClientBundle;
import org.vectomatic.dom.svg.ui.SVGResource;

/**
 * Logo for OpenShift version of Eclipse Che
 *
 * @author Florent Benoit
 */
public interface OpenShiftResources extends ClientBundle {
  @Source("logo/openshift-logo.svg")
  SVGResource logo();

  @Source("logo/openshift-watermark-logo.svg")
  SVGResource waterMarkLogo();
}
