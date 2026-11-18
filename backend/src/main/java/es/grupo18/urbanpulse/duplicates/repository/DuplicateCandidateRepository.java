package es.grupo18.urbanpulse.duplicates.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import es.grupo18.urbanpulse.duplicates.service.DuplicateScorer;
import es.grupo18.urbanpulse.incidents.model.Incident;

/**
 * Selección completa en SQL; solo las veinte mejores proyecciones cruzan JDBC.
 */
@Repository
public class DuplicateCandidateRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DuplicateCandidateRepository(DataSource source) {
        var template = new JdbcTemplate(source);
        template.setQueryTimeout(5);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    /**
     * La banda de latitud es conservadora en toda la esfera, incluso polos y antimeridiano. Haversine decide la
     * distancia final; ningún LIMIT precede al filtro y ranking. El índice temporal existente acota el intervalo
     * inicial.
     */
    public List<Candidate> bestCandidates(Incident source) {
        var point = source.getLocation().getCoordinates();
        var args = new MapSqlParameterSource().addValue("id", source.getId())
                .addValue("time", Timestamp.from(source.getCreatedAt()))
                .addValue("since", Timestamp.from(source.getCreatedAt().minus(DuplicateScorer.MAX_TIME_DIFFERENCE)))
                .addValue("until", Timestamp.from(source.getCreatedAt().plus(DuplicateScorer.MAX_TIME_DIFFERENCE)))
                .addValue("lat", point.getLatitude()).addValue("lon", point.getLongitude())
                .addValue("band", Math.toDegrees(250.0 / 6_371_000.0) + 1e-10).addValue("category",
                        source.getCategory() == null ? null : source.getCategory().getName(), java.sql.Types.VARCHAR);
        return jdbc.query("""
                WITH nearby AS MATERIALIZED (
                    SELECT i.id, i.latitude, i.longitude, i.created_at, c.name AS category,
                        least(1.0, greatest(0.0,
                            power(sin(radians(i.latitude - :lat) / 2), 2)
                            + cos(radians(:lat)) * cos(radians(i.latitude))
                            * power(sin(radians(i.longitude - :lon) / 2), 2))) AS haversine,
                        floor(abs(extract(epoch FROM (i.created_at - :time))) * 1000) / 259200000.0 AS age
                    FROM incidents i LEFT JOIN categories c ON c.id = i.category_id
                    WHERE i.id <> :id AND i.created_at BETWEEN :since AND :until
                        AND i.latitude BETWEEN :lat - :band AND :lat + :band
                        AND NOT EXISTS (SELECT 1 FROM duplicate_links d
                            WHERE (d.incident_id = :id AND d.candidate_incident_id = i.id)
                               OR (d.candidate_incident_id = :id AND d.incident_id = i.id))
                ), measured AS MATERIALIZED (
                    SELECT *, 12742000.0 * atan2(sqrt(haversine), sqrt(1 - haversine)) AS distance
                    FROM nearby
                ), scored AS (
                    SELECT *, CASE WHEN category IS NOT NULL AND :category IS NOT NULL
                        THEN (1 - distance / 250.0) * 0.6 + (1 - age) * 0.3
                            + CASE WHEN category = :category THEN 0.1 ELSE 0 END
                        ELSE (1 - distance / 250.0) * (2.0 / 3.0) + (1 - age) * (1.0 / 3.0)
                        END AS score
                    FROM measured WHERE distance <= 250.0
                )
                SELECT id, latitude, longitude, created_at, category FROM scored
                ORDER BY score DESC, id ASC LIMIT 20
                """, args, (rs, n) -> new Candidate(rs.getObject("id", UUID.class), rs.getDouble("latitude"),
                rs.getDouble("longitude"), rs.getTimestamp("created_at").toInstant(), rs.getString("category")));
    }

    public record Candidate(UUID id, double latitude, double longitude, Instant createdAt, String category) {
    }
}
