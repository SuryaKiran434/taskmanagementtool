package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.filter.JwtRequestFilter;
import com.suryakiran.taskmanagementtool.service.CustomUserDetailsService;
import com.suryakiran.taskmanagementtool.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the premise behind dismissing the two {@code java/spring-disabled-csrf-protection}
 * alerts on {@code SecurityConfig} as false positives.
 *
 * <p>{@code csrf().disable()} is only safe on an API that carries no ambient credential —
 * no cookie, no session — because a cross-site request cannot then be made to speak for a
 * victim: the browser has nothing to attach on its own, and script on the attacker's
 * origin cannot read a token out of the victim's storage to attach deliberately. That
 * property is what makes the alerts false positives, and it lives in this filter rather
 * than in the config, so it is what these tests guard.</p>
 *
 * <p>If someone later adds cookie-based auth or a session, the first test here fails, and
 * the dismissal has to be revisited along with it.</p>
 */
class BearerOnlyAuthenticationTest {

    private CustomUserDetailsService userDetailsService;
    private JwtUtil jwtUtil;
    private JwtRequestFilter filter;

    private final UserDetails principal =
            User.withUsername("owner@example.com").password("irrelevant").authorities("ROLE_USER").build();

    @BeforeEach
    void setUp() {
        userDetailsService = mock(CustomUserDetailsService.class);
        jwtUtil = mock(JwtUtil.class);
        filter = new JwtRequestFilter(userDetailsService, jwtUtil);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * A token presented in a cookie must authenticate nobody. A cookie is the credential a
     * browser attaches by itself on a cross-site request, so the moment one is honoured,
     * disabling CSRF stops being defensible.
     */
    @Test
    void aTokenInACookieAuthenticatesNobody() throws Exception {
        String token = "a.valid.looking.token";
        when(jwtUtil.extractUsername(anyString())).thenReturn("owner@example.com");
        when(jwtUtil.validateToken(anyString(), any())).thenReturn(true);
        when(jwtUtil.extractRoles(anyString())).thenReturn(List.of("ROLE_USER"));
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(principal);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks");
        request.setCookies(
                new Cookie("Authorization", "Bearer " + token),
                new Cookie("jwt", token),
                new Cookie("JSESSIONID", "0123456789ABCDEF"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
    }

    /**
     * The positive control: the same token in the {@code Authorization} header does
     * authenticate. Without this, the test above would pass just as well against a filter
     * that authenticates nothing at all.
     */
    @Test
    void aTokenInTheAuthorizationHeaderAuthenticates() throws Exception {
        String token = "a.valid.looking.token";
        when(jwtUtil.extractUsername(token)).thenReturn("owner@example.com");
        when(jwtUtil.validateToken(anyString(), any())).thenReturn(true);
        when(jwtUtil.extractRoles(token)).thenReturn(List.of("ROLE_USER"));
        when(userDetailsService.loadUserByUsername("owner@example.com")).thenReturn(principal);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("owner@example.com");
    }

    /**
     * Authenticating must not create a session either — a session id is itself an ambient
     * credential, and its absence is the other half of what makes the disabled CSRF filter
     * safe here.
     */
    @Test
    void authenticatingCreatesNoSession() throws Exception {
        String token = "a.valid.looking.token";
        when(jwtUtil.extractUsername(token)).thenReturn("owner@example.com");
        when(jwtUtil.validateToken(anyString(), any())).thenReturn(true);
        when(jwtUtil.extractRoles(token)).thenReturn(List.of("ROLE_USER"));
        when(userDetailsService.loadUserByUsername("owner@example.com")).thenReturn(principal);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getSession(false)).isNull();
    }
}
