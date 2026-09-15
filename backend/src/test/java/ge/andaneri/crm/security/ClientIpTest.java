package ge.andaneri.crm.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    @AfterEach
    void reset() {
        ClientIp.useHeader(null);
    }

    private static MockHttpServletRequest request(String remote, String realIp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (realIp != null) {
            request.addHeader("X-Real-IP", realIp);
        }
        return request;
    }

    @Test
    void withoutAConfiguredHeaderTheHeaderIsIgnored() {
        assertThat(ClientIp.of(request("10.0.0.5", "203.0.113.7"))).isEqualTo("10.0.0.5");
    }

    @Test
    void theConfiguredHeaderGivesTheVisitor() {
        ClientIp.useHeader("X-Real-IP");
        assertThat(ClientIp.of(request("10.0.0.5", "203.0.113.7"))).isEqualTo("203.0.113.7");
        assertThat(ClientIp.of(request("10.0.0.5", "203.0.113.7, 10.1.1.1"))).isEqualTo("203.0.113.7");
        assertThat(ClientIp.of(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
    }

    @Test
    void loopbackIsNeverTakenFromAHeader() {
        ClientIp.useHeader("X-Real-IP");
        assertThat(ClientIp.of(request("10.0.0.5", "127.0.0.1"))).isEqualTo("10.0.0.5");
        assertThat(ClientIp.of(request("10.0.0.5", "::1"))).isEqualTo("10.0.0.5");
        assertThat(ClientIp.of(request("0:0:0:0:0:0:0:1", null))).isEqualTo("::1");
    }
}
