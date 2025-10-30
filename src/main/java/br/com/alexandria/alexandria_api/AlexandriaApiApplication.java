package br.com.alexandria.alexandria_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.TimeZone;

@SpringBootApplication
@EnableTransactionManagement
@ConfigurationPropertiesScan
public class AlexandriaApiApplication {

  private static final Logger log = LoggerFactory.getLogger(AlexandriaApiApplication.class);

  public static void main(String[] args) {
    // Garante coerência com o application.yml (Hibernate timezone = UTC)
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(AlexandriaApiApplication.class, args);
  }

  /** Log inicial útil no Koyeb (sem vazar segredos). */
  @Bean
  ApplicationRunner onStart(Environment env) {
    return args -> {
      log.info("Alexandria API iniciada.");
      log.info("Perfis ativos: {}", Arrays.toString(env.getActiveProfiles()));
      log.info("Flyway.enabled = {}", env.getProperty("spring.flyway.enabled"));

      String dbUrl = env.getProperty("spring.datasource.url");
      if (dbUrl != null) {
        // obscure credenciais se presentes na URL
        String masked = dbUrl.replaceAll("://([^:@/]+)(:([^@/]*))?@", "://***:***@");
        log.info("Datasource URL: {}", masked);
      }
    };
  }
}
