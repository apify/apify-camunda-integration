package io.camunda.connector.apify.outbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.actuate.autoconfigure.endpoint.jmx.JmxEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.ssl.SslObservabilityAutoConfiguration;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;

@SpringBootApplication(exclude = {
    JmxEndpointAutoConfiguration.class,
    SslObservabilityAutoConfiguration.class,
    HttpClientAutoConfiguration.class,
    RestClientAutoConfiguration.class
})
public class LocalConnectorRuntime {

  public static void main(String[] args) {
    SpringApplication.run(LocalConnectorRuntime.class, args);
  }
}
