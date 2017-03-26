package org.eclipse.che.wsagent.server;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.keycloak.KeycloakSecurityContext;

public class KeycloakCheckFilter extends org.keycloak.adapters.servlet.KeycloakOIDCFilter {

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;


        if (request.getRequestURI().endsWith("/ws") || request.getRequestURI().endsWith("/eventbus")
                || request.getScheme().equals("ws") || req.getScheme().equals("wss")) {
            System.out.println("AGENT: Skipping " + request.getRequestURI());
            chain.doFilter(req, res);
        } else {
            super.doFilter(req, res, chain);
            KeycloakSecurityContext context =  (KeycloakSecurityContext) request .getAttribute(KeycloakSecurityContext.class.getName());
            KeycloakTokenProvider.current_token = context.getIdTokenString();
            System.out.println("AGENT: "+request.getRequestURL() + " status : " + ((HttpServletResponse) res).getStatus());
        }
    }

}