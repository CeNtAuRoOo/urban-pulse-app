package es.grupo18.urbanpulse.maps.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import es.grupo18.urbanpulse.incidents.model.IncidentPriority;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.maps.service.MapQuery;
import es.grupo18.urbanpulse.maps.service.MapVisibilityPolicy.Access;

/** PostgreSQL does authorization, spatial filtering and aggregation before returning bounded projections. */
@Repository
public class MapIncidentRepository {
    public static final int MAX_FEATURES = 1000;
    private final NamedParameterJdbcTemplate jdbc;

    public MapIncidentRepository(DataSource dataSource) {
        // Dedicated template: never change timeout/maxRows for other modules.
        var template = new JdbcTemplate(dataSource);
        template.setQueryTimeout(5);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    public List<Row> find(MapQuery query, Access access) {
        var args = new MapSqlParameterSource()
                .addValue("actor", access.actor())
                .addValue("minLat", query.minLat()).addValue("minLon", query.minLon())
                .addValue("maxLat", query.maxLat()).addValue("maxLon", query.maxLon())
                .addValue("cell", query.cellMeters()).addValue("zoom", query.zoom());
        String filters = "";
        if (query.status() != null) {
            filters += " AND i.status = :status";
            args.addValue("status", query.status().name());
        }
        if (query.categoryId() != null) {
            filters += " AND i.category_id = :category";
            args.addValue("category", query.categoryId());
        }
        String exactScope = access.global() ? "true" : "i.reporter_user_id = :actor";
        String exact = branch("map_xy_v1(i.latitude, i.longitude)", exactScope, filters, true);
        String visible = access.global() ? exact : exact + " UNION ALL " + branch(
                "map_public_point_v1(i.latitude, i.longitude)",
                "i.reporter_user_id <> :actor AND EXISTS (SELECT 1 FROM incident_reviews r "
                        + "WHERE r.incident_id = i.id AND r.decision = 'VALIDATE')", filters, false);
        String sql = """
                WITH visible AS MATERIALIZED (%s),
                bucketed AS (
                    SELECT id, CASE WHEN :cell = 0 THEN 'i:' || id::text
                        ELSE 'g1:' || :zoom || ':' || floor(p[0] / NULLIF(:cell, 0))::bigint
                            || ':' || floor(p[1] / NULLIF(:cell, 0))::bigint END AS bucket
                    FROM visible
                ), groups AS (
                    SELECT bucket, count(*) AS incident_count, min(id::text)::uuid AS representative
                    FROM bucketed GROUP BY bucket
                )
                SELECT g.bucket, g.incident_count, v.id, v.category_id, c.name AS category_name,
                    v.status, v.priority, v.created_at, v.exact,
                    CASE WHEN v.exact THEN v.latitude ELSE map_lat_v1(v.p) END AS latitude,
                    CASE WHEN v.exact THEN v.longitude ELSE map_lon_v1(v.p) END AS longitude
                FROM groups g JOIN visible v ON v.id = g.representative
                LEFT JOIN categories c ON c.id = v.category_id
                ORDER BY g.bucket COLLATE "C"
                LIMIT 1001
                """.formatted(visible);
        return jdbc.query(sql, args, (rs, n) -> new Row(rs.getString("bucket"), rs.getLong("incident_count"),
                rs.getObject("id", UUID.class), rs.getObject("category_id", UUID.class),
                rs.getString("category_name"), IncidentStatus.valueOf(rs.getString("status")),
                rs.getString("priority") == null ? null : IncidentPriority.valueOf(rs.getString("priority")),
                rs.getTimestamp("created_at").toInstant(), rs.getBoolean("exact"),
                rs.getDouble("latitude"), rs.getDouble("longitude")));
    }

    private static String branch(String point, String scope, String filters, boolean exact) {
        // These strings are internal constants. All user input is bound through named parameters.
        return "SELECT i.id, i.category_id, i.status, i.priority, i.created_at, " + exact + " AS exact, "
                + (exact ? "i.latitude, i.longitude, " : "NULL::float8 AS latitude, NULL::float8 AS longitude, ")
                + point + " AS p FROM incidents i WHERE (" + scope + ") "
                + "AND map_in_malaga_v1(i.latitude, i.longitude) AND " + point
                + " <@ box(map_xy_v1(:minLat, :minLon), map_xy_v1(:maxLat, :maxLon))" + filters;
    }

    /** Coordinates in this projection are already authorized; no original title or personal fields. */
    public record Row(String bucket, long incidentCount, UUID id, UUID categoryId, String categoryName,
            IncidentStatus status, IncidentPriority priority, Instant createdAt, boolean exact,
            double latitude, double longitude) { }
}
