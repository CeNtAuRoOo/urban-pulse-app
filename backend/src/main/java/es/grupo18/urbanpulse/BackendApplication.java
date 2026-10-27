package es.grupo18.urbanpulse;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;

/**
 * Clase principal de UrbanPulse encargada de iniciar la aplicación Spring Boot.
 * Activa la configuración automática y el descubrimiento de los componentes
 * necesarios para ejecutar el backend de la API REST.
 */
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
