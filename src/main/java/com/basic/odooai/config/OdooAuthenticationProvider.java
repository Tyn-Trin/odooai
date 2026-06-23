package com.basic.odooai.config;

import com.basic.odooai.model.OdooSession;
import com.basic.odooai.service.OdooAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OdooAuthenticationProvider implements AuthenticationProvider {

    @Autowired
    private OdooAuthService odooAuthService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        try {
            OdooSession odooSession = odooAuthService.login(username, password);
            return new UsernamePasswordAuthenticationToken(odooSession, null, List.of());
        } catch (Exception e) {
            throw new BadCredentialsException("ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
