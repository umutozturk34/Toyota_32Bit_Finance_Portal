package com.finance.common.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
    }

    @Test
    void doFilterInternal_invokesChain_andLogsSuccessfulRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stocks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_logsAtWarnLevel_when4xxResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stocks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_logsAtErrorLevel_when5xxResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/stocks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldNotFilter_returnsTrue_forActuatorPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        boolean skip = filter.shouldNotFilter(request);

        assertThat(skip).isTrue();
    }

    @Test
    void shouldNotFilter_returnsTrue_forHealthPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");

        boolean skip = filter.shouldNotFilter(request);

        assertThat(skip).isTrue();
    }

    @Test
    void shouldNotFilter_returnsFalse_forApplicationPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stocks");

        boolean skip = filter.shouldNotFilter(request);

        assertThat(skip).isFalse();
    }
}
