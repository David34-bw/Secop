package co.secopradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SECOP RADAR: licitaciones del Estado colombiano (SECOP II) filtradas
 * para cada persona. Punto de entrada: ejecuta este main desde IntelliJ y
 * abre http://localhost:8080
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SecopRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecopRadarApplication.class, args);
    }
}
