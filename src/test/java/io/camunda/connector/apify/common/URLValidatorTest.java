package io.camunda.connector.apify.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class URLValidatorTest {

    // -------------------------------------------------------------------------
    // Null / empty
    // -------------------------------------------------------------------------

    @Test
    void shouldThrowWhenUrlIsNull() {
        assertThatThrownBy(() -> URLValidator.validateUrl(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void shouldThrowWhenUrlIsEmpty() {
        assertThatThrownBy(() -> URLValidator.validateUrl("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or empty");
    }

    // -------------------------------------------------------------------------
    // Scheme validation
    // -------------------------------------------------------------------------

    @Test
    void shouldThrowWhenSchemeIsMissing() {
        assertThatThrownBy(() -> URLValidator.validateUrl("api.apify.com/v2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenSchemeIsNotHttpOrHttps() {
        assertThatThrownBy(() -> URLValidator.validateUrl("ftp://api.apify.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ftp");
    }

    @Test
    void shouldThrowWhenSchemeIsFile() {
        assertThatThrownBy(() -> URLValidator.validateUrl("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Partial / malformed URLs (no scheme)
    // -------------------------------------------------------------------------

    @Test
    void shouldThrowForHostOnlyWithNoScheme() {
        // "api.apify.com" has no scheme; parsed as a relative URI path
        assertThatThrownBy(() -> URLValidator.validateUrl("api.apify.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowForHostAndPathWithNoScheme() {
        assertThatThrownBy(() -> URLValidator.validateUrl("api.apify.com/v2/datasets"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowForProtocolRelativeUrl() {
        // "//api.apify.com" is protocol-relative; no scheme is present
        assertThatThrownBy(() -> URLValidator.validateUrl("//api.apify.com/v2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowForHostWithPortButNoScheme() {
        assertThatThrownBy(() -> URLValidator.validateUrl("api.apify.com:443/v2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowForHostWithQueryButNoScheme() {
        assertThatThrownBy(() -> URLValidator.validateUrl("api.apify.com?format=json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Valid public URLs
    // -------------------------------------------------------------------------

    @Test
    void shouldPassForValidHttpsUrl() {
        assertThatCode(() -> URLValidator.validateUrl("https://api.apify.com/v2/datasets"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPassForValidHttpUrl() {
        assertThatCode(() -> URLValidator.validateUrl("http://api.apify.com/v2/datasets"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPassForUrlWithQueryParameters() {
        assertThatCode(() -> URLValidator.validateUrl("https://api.apify.com/v2/datasets/abc123/items?format=json&limit=100"))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Loopback addresses
    // -------------------------------------------------------------------------

    @Test
    void shouldThrowForLoopbackIpv4() {
        assertThatThrownBy(() -> URLValidator.validateUrl("http://127.0.0.1/admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("127.0.0.1");
    }

    @Test
    void shouldThrowForLoopbackHostname() {
        assertThatThrownBy(() -> URLValidator.validateUrl("http://localhost/admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("localhost");
    }

    @Test
    void shouldThrowForLoopbackIpv6() {
        assertThatThrownBy(() -> URLValidator.validateUrl("http://[::1]/admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("::1");
    }

    // -------------------------------------------------------------------------
    // RFC 1918 private ranges
    // -------------------------------------------------------------------------

    @Test
    void shouldThrowForPrivateClass10Address() {
        assertThatThrownBy(() -> URLValidator.validateUrl("http://10.0.0.1/internal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10.0.0.1");
    }

    @Test
    void shouldThrowForPrivateClass172Address() {
        assertThatThrownBy(() -> URLValidator.validateUrl("http://172.16.0.1/internal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("172.16.0.1");
    }

    @Test
    void shouldThrowForPrivateClass192Address() {
        assertThatThrownBy(() -> URLValidator.validateUrl("http://192.168.1.1/router"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("192.168.1.1");
    }

    // -------------------------------------------------------------------------
    // Link-local addresses (SSRF classic targets)
    // -------------------------------------------------------------------------

    @Test
    void shouldThrowForAwsMetadataEndpoint() {
        // 169.254.169.254 is the AWS EC2 instance metadata endpoint; a common SSRF target
        assertThatThrownBy(() -> URLValidator.validateUrl("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("169.254.169.254");
    }

    @Test
    void shouldThrowForLinkLocalAddress() {
        assertThatThrownBy(() -> URLValidator.validateUrl("http://169.254.1.1/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("169.254.1.1");
    }

    // -------------------------------------------------------------------------
    // Any-local (0.0.0.0)
    // -------------------------------------------------------------------------

    @Test
    void shouldThrowForAnyLocalAddress() {
        assertThatThrownBy(() -> URLValidator.validateUrl("http://0.0.0.0/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.0.0.0");
    }

    // -------------------------------------------------------------------------
    // Unresolvable host; should NOT throw (fail-open)
    // -------------------------------------------------------------------------

    @Test
    void shouldAllowUnresolvableHostToFailAtConnectionTime() {
        // An unresolvable host should pass validation and fail only at request time.
        // Blocking here would cause false positives under transient DNS failures.
        assertThatCode(() -> URLValidator.validateUrl("https://this-host-does-not-exist.invalid/path"))
                .doesNotThrowAnyException();
    }
}
