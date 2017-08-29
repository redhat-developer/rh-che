package com.redhat.che.keycloak.token.provider.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OpenShiftUserToProjectNameConverterTest {

  @Test
  public void shouldStripEmailDomainFromUsername() {
    // Given
    String usernameWithEmail = "testuser@domain.com";
    String expected = "testuser";

    // When
    String actual =
        OpenShiftUserToProjectNameConverter.getProjectNameFromUsername(usernameWithEmail);

    // Then
    assertEquals("Should remove @domain from username", expected, actual);
  }

  @Test
  public void shouldConvertDotsToDashesInUsername() {
    // Given
    String usernameWithDot = "test.user";
    String expected = "test-user";

    // When
    String actual = OpenShiftUserToProjectNameConverter.getProjectNameFromUsername(usernameWithDot);

    // Then
    assertEquals("Should convert '.' to '-' in username", expected, actual);
  }

  @Test
  public void shouldConvertDotsAndStripEmail() {
    // Given
    String username = "test.user@domain.com";
    String expected = "test-user";

    // When
    String actual = OpenShiftUserToProjectNameConverter.getProjectNameFromUsername(username);

    // Then
    assertEquals("Should convert '.' to '-' in username and strip email", expected, actual);
  }

  @Test
  public void shouldReplaceUnderscoreWithDash() {
    // Given
    String usernameWithUnderscore = "test_user";
    String expected = "test-user";

    // When
    String actual =
        OpenShiftUserToProjectNameConverter.getProjectNameFromUsername(usernameWithUnderscore);

    // Then
    assertEquals("Should convert '_' to '-' in username", expected, actual);
  }

  @Test
  public void shouldReplaceNonAlphaNumericWithDash() {
    // Given
    String username = "test_test._test+test";
    String expected = "test-test--test-test";

    // When
    String actual = OpenShiftUserToProjectNameConverter.getProjectNameFromUsername(username);

    // Then
    assertEquals(
        "Should convert all characters not matching regex [a-z0-9]" + " to '-' in username",
        expected,
        actual);
  }

  @Test
  public void shouldDoNothingForAlphanumericUsernames() {
    // Given
    String username = "abcdefghijklmnopqrstuvwxyz1234567890";
    String expected = "abcdefghijklmnopqrstuvwxyz1234567890";

    // When
    String actual = OpenShiftUserToProjectNameConverter.getProjectNameFromUsername(username);

    // Then
    assertEquals("Should convert '.' to '-' in username", expected, actual);
  }
}
