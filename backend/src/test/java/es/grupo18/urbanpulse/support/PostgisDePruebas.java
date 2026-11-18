package es.grupo18.urbanpulse.support;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Imagen fijada de PostgreSQL con PostGIS que utiliza el entorno de desarrollo.
 */
public final class PostgisDePruebas {
    private static final DockerImageName IMAGEN = DockerImageName.parse(
            "postgis/postgis:18-3.6-alpine@sha256:c53a483fc274072dfa02cd491efa79f3c8ae0a10774a0ece6c9c53b6a632cdef")
            .asCompatibleSubstituteFor("postgres");

    private PostgisDePruebas() {
    }

    /** @return un contenedor independiente cuyo ciclo de vida controla la suite */
    public static PostgreSQLContainer nuevo() {
        return new PostgreSQLContainer(IMAGEN);
    }
}
