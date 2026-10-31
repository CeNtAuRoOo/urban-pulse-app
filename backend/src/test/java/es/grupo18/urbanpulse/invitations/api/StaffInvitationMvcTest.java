package es.grupo18.urbanpulse.invitations.api;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.invitations.dto.InvitationDTO;
import es.grupo18.urbanpulse.invitations.model.InvitationStatus;
import es.grupo18.urbanpulse.invitations.service.StaffInvitationService;
import es.grupo18.urbanpulse.shared.error.ProblemDetailFactory;
import es.grupo18.urbanpulse.shared.ratelimit.InMemoryRateLimiter;
import es.grupo18.urbanpulse.shared.security.DatabaseJwtAuthenticationConverter;
import es.grupo18.urbanpulse.shared.security.RestAccessDeniedHandler;
import es.grupo18.urbanpulse.shared.security.RestAuthenticationEntryPoint;
import es.grupo18.urbanpulse.shared.security.SecurityConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffInvitationController.class)
@Import({ SecurityConfig.class, DatabaseJwtAuthenticationConverter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, ProblemDetailFactory.class, InMemoryRateLimiter.class })
@TestPropertySource("classpath:auth-test.properties")
class StaffInvitationMvcTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    JwtEncoder encoder;
    @MockitoBean
    StaffInvitationService service;
    @MockitoBean
    UserRepository users;
    private UUID user;
    private final UUID invitationId = UUID.randomUUID();
    private static final String CREATE = "/api/v1/staff-invitations";
    private static final String REDEEM = "/api/v1/users/me/staff-invitation-redemptions";
    private static final String CODE_BODY = "{\"invitationCode\":\"" + "A".repeat(43) + "\"}";

    @BeforeEach
    void setup() {
        user = UUID.randomUUID();
        state(true, RoleCode.CITIZEN, RoleCode.ADMIN);
        when(service.create(any(), anyLong(), any())).thenReturn(new InvitationDTO(invitationId,
                "person@example.org", RoleCode.ADMIN, null, InvitationStatus.ACTIVE, user, null, null,
                Instant.now(), Instant.now().plusSeconds(604800), null, null));
    }

    private void state(boolean verified, RoleCode... roles) {
        var rows = Arrays.stream(roles).map(role -> {
            var row = mock(UserRepository.SecurityState.class);
            when(row.getActive()).thenReturn(true);
            when(row.getEmailVerified()).thenReturn(verified);
            when(row.getTokenVersion()).thenReturn(0L);
            when(row.getRoleCode()).thenReturn(role);
            return row;
        }).toList();
        when(users.findSecurityState(user)).thenReturn(rows);
    }

    private String bearer() {
        Instant now = Instant.now();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("urbanpulse-tests").subject(user.toString()).issuedAt(now)
                        .expiresAt(now.plusSeconds(7200)).claim("ver", 0L).build()))
                .getTokenValue();
    }

    @Test
    void creationSupportsBothVariantsAndNeverReturnsCode() throws Exception {
        for (String role : List.of("ADMIN", "OPERATOR", "TECHNICIAN", "DEPARTMENT_COORDINATOR")) {
            String department = role.equals("TECHNICIAN") || role.equals("DEPARTMENT_COORDINATOR")
                    ? ",\"departmentId\":\"" + UUID.randomUUID() + "\""
                    : "";
            mvc.perform(post(CREATE).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\" PERSON@EXAMPLE.ORG \",\"role\":\"" + role + "\"" + department + "}"))
                    .andExpect(status().isCreated()).andExpect(header().string("Location", CREATE + "/" + invitationId))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.invitationCode").doesNotExist())
                    .andExpect(jsonPath("$.codeHash").doesNotExist());
        }
        verify(service, times(4)).create(any(), eq(0L), argThat(r -> r.email().equals("person@example.org")));
    }

    @Test
    void rejectsMissingEmailCitizenWrongScopeExpiryAndUnknownProperties() throws Exception {
        for (String body : List.of(
                "{\"role\":\"ADMIN\"}",
                "{\"role\":\"CITIZEN\",\"email\":\"person@example.org\"}",
                "{\"role\":\"TECHNICIAN\",\"email\":\"person@example.org\"}",
                "{\"role\":\"TECHNICIAN\",\"email\":\"person@example.org\",\"departmentId\":null}",
                "{\"role\":\"ADMIN\",\"email\":\"person@example.org\",\"departmentId\":null}",
                "{\"role\":\"ADMIN\",\"email\":\"person@example.org\",\"expiresAt\":\"2027-01-01T00:00:00Z\"}",
                "{\"role\":\"ADMIN\",\"email\":\"person@example.org\",\"extra\":true}")) {
            mvc.perform(post(CREATE).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isUnprocessableContent())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(service);
    }

    @Test
    void requiresAdminAndVerifiedAccountForEveryAdministrativeRoute() throws Exception {
        for (boolean verified : List.of(true, false)) {
            state(verified, RoleCode.CITIZEN);
            for (String path : List.of(CREATE, CREATE + "/" + invitationId)) {
                mvc.perform(get(path).header("Authorization", bearer())).andExpect(status().isForbidden());
            }
            mvc.perform(post(CREATE).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\":\"ADMIN\",\"email\":\"person@example.org\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(CREATE + "/" + invitationId + "/revocations").header("Authorization", bearer()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(CREATE)).andExpect(status().isUnauthorized());
        state(false, RoleCode.CITIZEN, RoleCode.ADMIN);
        mvc.perform(get(CREATE).header("Authorization", bearer())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        verifyNoInteractions(service);
    }

    @Test
    void redemptionRequiresVerifiedCitizenAndStrictBody() throws Exception {
        mvc.perform(post(REDEEM).contentType(MediaType.APPLICATION_JSON).content(CODE_BODY))
                .andExpect(status().isUnauthorized());
        state(false, RoleCode.CITIZEN);
        mvc.perform(post(REDEEM).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(CODE_BODY)).andExpect(status().isForbidden());
        state(true, RoleCode.ADMIN);
        mvc.perform(post(REDEEM).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(CODE_BODY)).andExpect(status().isUnauthorized());
        state(true, RoleCode.CITIZEN);
        mvc.perform(post(REDEEM).header("Authorization", bearer()).with(r -> {
            r.setRemoteAddr(user.toString());
            return r;
        })
                .contentType(MediaType.APPLICATION_JSON).content(CODE_BODY.replace("}", ",\"role\":\"ADMIN\"}")))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(service);
    }

    @Test
    void paginationDefaultsLimitsEmptyPagesAndDatabaseFailuresUseProblemDetail() throws Exception {
        mvc.perform(get(CREATE).header("Authorization", bearer())).andExpect(status().isOk());
        verify(service).list(any(), eq(0L), argThat(q -> q.page() == 1 && q.pageSize() == 20));
        for (String query : List.of("?page=0", "?pageSize=101", "?pageSize=0", "?email=bad", "?status=UNKNOWN")) {
            mvc.perform(get(CREATE + query).header("Authorization", bearer()))
                    .andExpect(status().isUnprocessableContent());
        }
        when(service.get(any(), anyLong(), any())).thenThrow(new DataAccessResourceFailureException("PRIVATE_DB"));
        mvc.perform(get(CREATE + "/not-a-uuid").header("Authorization", bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(get(CREATE + "/" + invitationId).header("Authorization", bearer()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void limitsBodiesAndRejectsRevocationBody() throws Exception {
        for (String path : List.of(CREATE, REDEEM)) {
            mvc.perform(post(path).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content(" ".repeat(16385))).andExpect(status().isBadRequest());
        }
        mvc.perform(post(CREATE + "/" + invitationId + "/revocations").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void realUserBucketRejectsEleventhAttemptWithRetryAfterAndNewUserIsIndependent() throws Exception {
        String ip = UUID.randomUUID().toString();
        for (int i = 0; i < 11; i++) {
            var result = mvc.perform(post(REDEEM).header("Authorization", bearer())
                    .with(r -> {
                        r.setRemoteAddr(ip);
                        return r;
                    })
                    .contentType(MediaType.APPLICATION_JSON).content(CODE_BODY));
            if (i < 10)
                result.andExpect(status().isOk());
            else
                result.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                        .andExpect(jsonPath("$.code").value("RATE_LIMIT"));
        }
        verify(service, times(10)).redeem(any(), anyLong(), anyString());
        user = UUID.randomUUID();
        state(true, RoleCode.CITIZEN);
        mvc.perform(post(REDEEM).header("Authorization", bearer()).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        })
                .contentType(MediaType.APPLICATION_JSON).content(CODE_BODY)).andExpect(status().isOk());
    }

    @Test
    void realIpBucketRejectsAcrossUsersAndIgnoresForwardedHeaders() throws Exception {
        String ip = UUID.randomUUID().toString();
        for (int i = 0; i < 31; i++) {
            user = UUID.randomUUID();
            state(true, RoleCode.CITIZEN);
            mvc.perform(post(REDEEM).header("Authorization", bearer()).header("X-Forwarded-For", UUID.randomUUID())
                    .with(r -> {
                        r.setRemoteAddr(ip);
                        return r;
                    })
                    .contentType(MediaType.APPLICATION_JSON).content(CODE_BODY))
                    .andExpect(status().is(i < 30 ? 200 : 429));
        }
        verify(service, times(30)).redeem(any(), anyLong(), anyString());
    }
}
