package es.grupo18.urbanpulse.config;

import java.io.IOException;
import java.time.*;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import es.grupo18.urbanpulse.exception.ProblemDetailFactory;
import es.grupo18.urbanpulse.security.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.*;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configura los componentes de seguridad de la aplicación. Incluye
 * autenticación JWT, cifrado de contraseñas, validación JSON, CORS y protección
 * de las rutas mediante Spring Security.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({ JwtProperties.class, RateLimitProperties.class })
public class SecurityConfig {

    /**
     * Proporciona un reloj del sistema en UTC.
     *
     * @return reloj configurado en UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Configura el codificador de contraseñas mediante BCrypt.
     *
     * @return codificador BCrypt con un factor de coste de 12
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Construye la clave secreta utilizada para firmar y verificar los JWT.
     *
     * @param properties configuración JWT de la aplicación
     * @return clave secreta para el algoritmo HMAC-SHA256
     */
    @Bean
    public SecretKey jwtKey(JwtProperties properties) {
        return new SecretKeySpec(properties.keyBytes(), "HmacSHA256");
    }

    /**
     * Configura el componente encargado de firmar y generar tokens JWT.
     *
     * @param key clave secreta utilizada para la firma
     * @return codificador JWT basado en Nimbus
     */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /**
     * Configura la verificación de firma, emisor y validez temporal de los JWT,
     * así como la presencia y el formato de los claims necesarios.
     *
     * @param key        clave secreta utilizada para verificar la firma
     * @param properties configuración del emisor JWT
     * @param clock      reloj utilizado para validar las fechas del token
     * @return decodificador JWT con las validaciones configuradas
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey key, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);

        OAuth2TokenValidator<Jwt> required = jwt -> {
            try {
                DatabaseJwtAuthenticationConverter.subject(jwt);
                DatabaseJwtAuthenticationConverter.version(jwt);

                if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
                        || jwt.getIssuedAt().isAfter(clock.instant())
                        || !jwt.getExpiresAt().isAfter(clock.instant())
                        || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())) {
                    throw new IllegalArgumentException();
                }

                return OAuth2TokenValidatorResult.success();
            }

            catch (RuntimeException ex) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid claims", null));
            }
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(properties.issuer()), required));

        return decoder;
    }

    /**
     * Configura una deserialización JSON estricta para rechazar propiedades
     * desconocidas, contenido adicional, claves duplicadas y conversiones
     * implícitas de tipos no permitidas.
     *
     * @return personalizador de la configuración de Jackson
     */
    @Bean
    public JsonMapperBuilderCustomizer strictJson() {
        return builder -> builder
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }

    /**
     * Define los orígenes, métodos y cabeceras permitidos en las peticiones CORS.
     *
     * @param properties configuración de los orígenes autorizados
     * @return configuración CORS aplicable a todas las rutas
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(JwtProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("Retry-After"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    /**
     * Configura la cadena de filtros de seguridad para aplicaciones web.
     * Permite el registro y login públicos y exige JWT en el resto de rutas.
     * Por defecto exige correo verificado; confirmación, reenvío y cambio de
     * contraseña admiten la identidad limitada. Aplica CORS y rate limiting.
     *
     * @param http          configurador de seguridad HTTP de Spring
     * @param converter     conversor de JWT a autenticación con roles de la base de
     *                      datos
     * @param entryPoint    gestor de errores de autenticación
     * @param deniedHandler gestor de errores por permisos insuficientes
     * @param limiter       limitador de peticiones en memoria
     * @param limits        configuración de las políticas de limitación
     * @param problems      generador de respuestas de error ProblemDetail
     * @param mapper        serializador JSON para las respuestas de error
     * @param properties    configuración JWT y de orígenes permitidos
     * @return cadena de filtros de seguridad configurada
     * @throws Exception si se produce un error al construir la configuración
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, DatabaseJwtAuthenticationConverter converter,
            RestAuthenticationEntryPoint entryPoint, RestAccessDeniedHandler deniedHandler, InMemoryRateLimiter limiter,
            RateLimitProperties limits, ProblemDetailFactory problems, JsonMapper mapper, JwtProperties properties)
            throws Exception {

        CorsFilter corsFilter = new CorsFilter(corsConfigurationSource(properties));
        corsFilter.setCorsProcessor(new DefaultCorsProcessor() {
            /**
             * Rechaza una petición CORS no autorizada devolviendo una respuesta
             * HTTP 403 con los detalles del error en formato ProblemDetail.
             *
             * @param response respuesta HTTP en la que se escribirá el error
             * @throws IOException si se produce un error al escribir la respuesta
             */
            @Override
            protected void rejectRequest(ServerHttpResponse response) throws IOException {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
                mapper.writeValue(response.getBody(), problems.create(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Origen o cabeceras CORS no admitidos.", null));
            }
        });

        return http.cors(cors -> cors.disable())
                .addFilterAt(corsFilter, CorsFilter.class)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/email-verifications/confirm",
                                "/api/v1/auth/email-verifications/resend", "/api/v1/users/me/password-changes")
                        .authenticated()
                        .anyRequest().hasAuthority(DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED))
                .exceptionHandling(
                        errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            /**
                             * Personaliza el filtro de autenticación Bearer para utilizar el
                             * manejador de errores de autenticación configurado por la aplicación.
                             *
                             * @param <O>    tipo concreto del filtro de autenticación Bearer
                             * @param filter filtro de autenticación que se desea configurar
                             * @return filtro de autenticación con el manejador de errores establecido
                             */
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                var failureHandler = new AuthenticationEntryPointFailureHandler(entryPoint);
                                failureHandler.setRethrowAuthenticationServiceException(false);
                                filter.setAuthenticationFailureHandler(failureHandler);
                                return filter;
                            }
                        }))
                .addFilterBefore(new AuthRateLimitFilter(limiter, limits, problems, mapper),
                        BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new EmailVerificationRateLimitFilter(limiter, limits, problems),
                        BearerTokenAuthenticationFilter.class)
                .build();
    }
}
