package ar.cuentas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CuentasApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CuentasApiApplication.class, args);
    }
}
