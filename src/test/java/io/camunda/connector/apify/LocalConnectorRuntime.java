package io.camunda.connector.apify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Local connector runtime for testing both inbound and outbound Apify connectors.
 *
 * <p>This runtime starts a Spring Boot application that loads both:
 * <ul>
 *   <li>Outbound connector (ApifyFunction) - for executing Apify tasks from BPMN processes</li>
 *   <li>Inbound connector (ApifyInboundExecutable) - for receiving webhooks from Apify</li>
 * </ul>
 *
 * <p>Connectors are discovered via SPI (Service Provider Interface) from META-INF/services.
 */
@SpringBootApplication(excludeName = {
    "org.springframework.boot.actuate.autoconfigure.endpoint.jmx.JmxEndpointAutoConfiguration",
    "org.springframework.boot.actuate.autoconfigure.ssl.SslObservabilityAutoConfiguration",
    "org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration",
    "org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration"
})
public class LocalConnectorRuntime {

  /**
   * Starts the local connector runtime.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(LocalConnectorRuntime.class, args);
  }
}
