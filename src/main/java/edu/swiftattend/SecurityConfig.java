package edu.swiftattend;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Value("${app.jwt.secret}") private String jwtSecret;
    @Value("${app.jwt.minutes}") private long jwtMinutes;
    @Value("${app.cors.origin}") private String corsOrigin;

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecretKey jwtKey() { return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)); }
    @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(a -> a.requestMatchers("/", "/index.html", "/styles.css", "/app.js", "/face-api.js", "/html5-qrcode.min.js", "/favicon.svg", "/assets/**", "/models/**", "/api/health", "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/ws/**").permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll().anyRequest().authenticated())
          .addFilterBefore(new JwtFilter(jwtKey()), UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration(); c.setAllowedOrigins(List.of(corsOrigin));
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization","Content-Type","X-Requested-With")); c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/**",c); return s;
    }
    public String issueToken(String userId, String role, SecretKey key) {
        return Jwts.builder().subject(userId).claim("role", role).issuedAt(new java.util.Date())
          .expiration(new java.util.Date(System.currentTimeMillis()+jwtMinutes*60_000)).signWith(key).compact();
    }
    static class JwtFilter extends OncePerRequestFilter {
        private final SecretKey key; JwtFilter(SecretKey key) { this.key=key; }
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
            String h=req.getHeader("Authorization");
            if(h!=null&&h.startsWith("Bearer ")) try {
                var claims=Jwts.parser().verifyWith(key).build().parseSignedClaims(h.substring(7)).getPayload();
                var auth=new UsernamePasswordAuthenticationToken(claims.getSubject(),null,List.of(new SimpleGrantedAuthority("ROLE_"+claims.get("role",String.class))));
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            } catch(Exception ignored) { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            chain.doFilter(req,res);
        }
    }
}
