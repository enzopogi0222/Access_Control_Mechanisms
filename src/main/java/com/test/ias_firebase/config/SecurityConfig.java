package com.test.ias_firebase.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final MfaEnforcementFilter mfaEnforcementFilter;

    public SecurityConfig(FirebaseAuthenticationFilter firebaseAuthenticationFilter,
                          MfaEnforcementFilter mfaEnforcementFilter) {
        this.firebaseAuthenticationFilter = firebaseAuthenticationFilter;
        this.mfaEnforcementFilter = mfaEnforcementFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            .securityContext(security ->
                security.securityContextRepository(securityContextRepository())
            )

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/",
                        "/index",
                        "/css/**",
                        "/js/**",
                        "/favicon.ico",
                        "/api/sessionLogin",
                        "/api/verifyTotp"
                ).permitAll()
                .requestMatchers("/register", "/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/secure", "/api/totp/**").authenticated()
                .anyRequest().authenticated()
            )

            .addFilterBefore(firebaseAuthenticationFilter,
                    org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(mfaEnforcementFilter, FirebaseAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
