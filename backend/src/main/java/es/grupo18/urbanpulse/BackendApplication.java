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

    /**
     * Inicia la aplicación UrbanPulse mediante Spring Boot.
     * Crea el contexto de aplicación y arranca los componentes
     * configurados para ejecutar el backend.
     *
     * @param args argumentos de línea de comandos de la aplicación
     */
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
