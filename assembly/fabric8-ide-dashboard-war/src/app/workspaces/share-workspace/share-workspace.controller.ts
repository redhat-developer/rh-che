/*
 * Copyright (c) 2016-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
'use strict';
import { CheAPI } from '../../../components/api/che-api.factory';
import { CheNotification } from '../../../components/notification/che-notification.factory';
import { CheWorkspace } from '../../../components/api/workspace/che-workspace.factory';

/**
 * Controller for workspace sharing with other users via factories
 *
 * @ngdoc controller
 * @name workspace.details.controller:ShareWorkspaceController
 * @description Controller for sharing workspaces via factory
 * @author Angel Misevski
 */
export class ShareWorkspaceController {

  static $inject = ['cheWorkspace', 'cheAPI', 'cheNotification', '$location', '$route', '$log'];

  /** Interaction with server API */
  private cheAPI: CheAPI;
  /** Che notification service */
  private cheNotification: CheNotification;
  /** JSON representing the current workspace */
  private workspace: che.IWorkspace;

  private $location: ng.ILocationService;
  private $log: ng.ILogService;

  private form: ng.IFormController;
  private factoryName: string;
  private isLoading: boolean;

  constructor(cheWorkspace: CheWorkspace,
              cheAPI: CheAPI,
              cheNotification: CheNotification,
              $location: ng.ILocationService,
              $route: ng.route.IRouteService,
              $log: ng.ILogService) {
    this.cheAPI = cheAPI;

    this.cheNotification = cheNotification;
    this.$location = $location;
    this.$log = $log;

    // Use route to get workspace json
    let namespace: string = $route.current.params.namespace;
    let workspaceName: string = $route.current.params.workspaceName;

    this.workspace = cheWorkspace.getWorkspaceByName(namespace, workspaceName);

    this.isLoading = false;
    this.factoryName = ''
  }

  createFactoryForWorkspace() {
    this.isLoading = true;
    let factoryContentPromise: ng.IPromise = this.cheAPI.getFactory().fetchFactoryContentFromWorkspace(this.workspace);
    factoryContentPromise.then((factoryContent: any) => {

      if (this.factoryName) {
        // try to set factory name
        try {
          let factoryObject = angular.fromJson(factoryContent);
          factoryObject.name = this.factoryName;
          factoryContent = angular.toJson(factoryObject);
        } catch (e) {
          this.$log.error(e);
        }
      }
      let factoryPromise: ng.IPromise = this.cheAPI.getFactory().createFactoryByContent(factoryContent)

      factoryPromise.then((factory: any) => {
        this.isLoading = false;
        this.$location.path('/factory/' + factory.id);
      }, (error: any) => {
        this.isLoading = false;
        this.cheNotification.showError(error.data.message ? error.data.message : 'Failed to create Factory.');
      });

    }, (error: any) => {

      let message = (error.data && error.data.message) ? error.data.message : 'Get factory configuration failed.';
      if (error.status === 400) {
        message = 'Factory can\'t be created. The selected workspace has no projects defined. Project sources must be available from an external storage.';
      }

      this.isLoading = false;
      this.cheNotification.showError(message);
    });
  }

  setForm(form: any): void {
    this.form = form;
  }

  isFormInvalid(): boolean {
    return this.form ? this.form.$invalid : false;
  }
}
