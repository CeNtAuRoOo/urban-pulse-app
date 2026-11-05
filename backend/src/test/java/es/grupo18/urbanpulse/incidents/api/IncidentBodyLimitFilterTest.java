package es.grupo18.urbanpulse.incidents.api;

import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.shared.error.ProblemDetailFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class IncidentBodyLimitFilterTest {
    private final ProblemDetailFactory problems = mock(ProblemDetailFactory.class);
    private final IncidentBodyLimitFilter filter = new IncidentBodyLimitFilter(problems);

    @Test
    void unknownLengthOrFalseLengthCannotBypassPostAndPatchLimit() throws Exception {
        for (long declared : new long[] { -1, 0 }) {
            for (String method : new String[] { "POST", "PATCH" }) {
                var request = new MockHttpServletRequest() {
                    @Override
                    public long getContentLengthLong() {
                        return declared;
                    }

                    @Override
                    public int getContentLength() {
                        return (int) declared;
                    }
                };
                request.setMethod(method);
                request.setContextPath("/app");
                request.setRequestURI("/app/api/v1/incidents" + (method.equals("PATCH") ? "/id" : ""));
                request.setContent(new byte[65_537]);
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, (req, res) -> {
                    throw new AssertionError("No debe llegar a MVC");
                });
                verify(problems).write(eq(request), eq(response), eq(HttpStatus.valueOf(413)),
                        eq("PAYLOAD_TOO_LARGE"), any());
            }
        }
    }

    @Test
    void exactByteLimitIsPreservedForDownstreamReaders() throws Exception {
        var request = new MockHttpServletRequest("PATCH", "/api/v1/incidents/id");
        byte[] content = " ".repeat(65_536).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        request.setContent(content);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(req.getInputStream().readAllBytes()).isEqualTo(content);
            assertThat(req.getReader().readLine()).hasSize(65_536);
        });
        verifyNoInteractions(problems);
    }

    @Test
    void otherRoutesAndMethodsKeepTheirOwnPolicies() throws Exception {
        for (var request : new MockHttpServletRequest[] {
                new MockHttpServletRequest("POST", "/api/v1/auth/register"),
                new MockHttpServletRequest("GET", "/api/v1/incidents"),
                new MockHttpServletRequest("POST", "/api/v1/incidents/id/reviews"),
                new MockHttpServletRequest("PATCH", "/api/v1/categories/id") }) {
            request.setContent(new byte[65_537]);
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> assertThat(req).isSameAs(request));
        }
        verifyNoInteractions(problems);
    }
}
