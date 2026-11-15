-- Reference query from MapIncidentRepository at 8704ac1, before the P1 fix.
-- Test-only substitution of FILTERS reproduces the original optional predicates.
WITH visible AS MATERIALIZED (
    SELECT i.id, i.category_id, i.status, i.priority, i.created_at, true AS exact,
        i.latitude, i.longitude, map_xy_v1(i.latitude, i.longitude) AS p
    FROM incidents i WHERE i.reporter_user_id = :actor
        AND map_in_malaga_v1(i.latitude, i.longitude)
        AND map_xy_v1(i.latitude, i.longitude)
            <@ box(map_xy_v1(:minLat, :minLon), map_xy_v1(:maxLat, :maxLon)) /*FILTERS*/
    UNION ALL
    SELECT i.id, i.category_id, i.status, i.priority, i.created_at, false AS exact,
        NULL::float8 AS latitude, NULL::float8 AS longitude,
        map_public_point_v1(i.latitude, i.longitude) AS p
    FROM incidents i WHERE i.reporter_user_id <> :actor
        AND EXISTS (SELECT 1 FROM incident_reviews r WHERE r.incident_id=i.id AND r.decision='VALIDATE')
        AND map_in_malaga_v1(i.latitude, i.longitude)
        AND map_public_point_v1(i.latitude, i.longitude)
            <@ box(map_xy_v1(:minLat, :minLon), map_xy_v1(:maxLat, :maxLon)) /*FILTERS*/
), bucketed AS (
    SELECT id, CASE WHEN :cell = 0 THEN 'i:' || id::text
        ELSE 'g1:' || :zoom || ':' || floor(p[0] / NULLIF(:cell, 0))::bigint
            || ':' || floor(p[1] / NULLIF(:cell, 0))::bigint END AS bucket FROM visible
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
