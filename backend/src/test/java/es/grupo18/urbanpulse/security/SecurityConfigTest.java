package es.grupo18.urbanpulse.security;

import es.grupo18.urbanpulse.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    @Test
    void nonWebContextsKeepCryptoWithoutRequiringHttpSecurityOrExternalServices() throws Exception {
        var properties = PropertiesLoaderUtils.loadAllProperties("auth-test.properties");
        new ApplicationContextRunner().withUserConfiguration(SecurityConfig.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new PropertiesPropertySource("isolated-auth", properties)))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtEncoder.class)
                        .doesNotHaveBean(SecurityFilterChain.class));
    }
}
