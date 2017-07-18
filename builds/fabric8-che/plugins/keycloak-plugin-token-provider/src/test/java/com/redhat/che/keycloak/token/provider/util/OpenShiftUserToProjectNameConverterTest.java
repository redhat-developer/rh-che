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
        String actual = OpenShiftUserToProjectNameConverter.getProjectNameFromUsername(usernameWithEmail);

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
}
