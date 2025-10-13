package io.camunda.connector.apify.inbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.actuate.autoconfigure.endpoint.jmx.JmxEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.ssl.SslObservabilityAutoConfiguration;

@SpringBootApplication(exclude = {
    JmxEndpointAutoConfiguration.class,
    SslObservabilityAutoConfiguration.class
})
public class LocalConnectorRuntime {

  public static void main(String[] args) {
    SpringApplication.run(LocalConnectorRuntime.class, args);
  }
}
