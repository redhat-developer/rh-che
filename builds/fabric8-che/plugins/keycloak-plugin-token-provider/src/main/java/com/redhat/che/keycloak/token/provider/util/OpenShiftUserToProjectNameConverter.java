package com.redhat.che.keycloak.token.provider.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for extracting name used for project on OpenShift.io from a given OpenShift
 * username.
 *
 * <p>The name used to define OpenShift projects must be converted before being used as a project
 * name due to different limitations on format between the two.
 */
public class OpenShiftUserToProjectNameConverter {

  private static final Logger LOG =
      LoggerFactory.getLogger(OpenShiftUserToProjectNameConverter.class);

  /**
   * Gets expected project name from an OpenShift username. Currently, usernames are converted using
   * <li>all characters after an {@code '@'} character (e.g. in an email) are stripped off,
   *     including {@code '@'}
   * <li>{@code '.'} characters are converted into {@code '-'}
   * <li>if a username contains a {@code '+'} character, it should also be converted to {@code '-'}
   *
   * @param openShiftUsername The openshift username
   * @return the expected project name
   */
  public static String getProjectNameFromUsername(String openShiftUsername) {
    String projectName = openShiftUsername;
    LOG.debug("Getting project name from openshift user: {}", projectName);
    if (projectName.contains("@")) {
      // Username is an email address
      projectName = projectName.split("@")[0];
    }
    projectName = projectName.replaceAll("[^a-z0-9]", "-");

    LOG.debug("Got project name: {}", projectName);
    return projectName;
  }
}
