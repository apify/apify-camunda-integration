package io.camunda.connector.apify.inbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.actuate.autoconfigure.endpoint.jmx.JmxEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.ssl.SslObservabilityAutoConfiguration;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;

/**
 * Local connector runtime for testing.
 */
@SpringBootApplication(exclude = {
    JmxEndpointAutoConfiguration.class,
    SslObservabilityAutoConfiguration.class,
    HttpClientAutoConfiguration.class,
    RestClientAutoConfiguration.class
})
public class LocalConnectorRuntime {

  /**
   * Main method for running the connector.
   */
  public static void main(String[] args) {
    SpringApplication.run(LocalConnectorRuntime.class, args);
  }
}
