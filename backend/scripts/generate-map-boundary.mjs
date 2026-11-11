// Offline, deterministic generation. Never replace an applied migration.
import { readFileSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("../", import.meta.url));
const source = readFileSync(root + "src/main/resources/maps/malaga-ign.geojson");
const expected = "eb15c7b0de3b648bedc52202a79af47c8887841dce2914986cfca774d8d101ba";
if (createHash("sha256").update(source).digest("hex") !== expected)
  throw Error("Unexpected boundary checksum");
const feature = JSON.parse(source);
if (
  feature.properties.nationalcode !== "34012900000" ||
  feature.geometry.type !== "MultiPolygon" ||
  feature.geometry.coordinates.length !== 1 ||
  feature.geometry.coordinates[0].length !== 1
)
  throw Error("Review changed geometry; do not drop islands or holes");
// Local equidistant cylindrical projection, R=6371008.8 m, parallel 36.75 N.
const scaleY = (Math.PI * 6371008.8) / 180;
const scaleX = scaleY * Math.cos((36.75 * Math.PI) / 180);
const vertices = feature.geometry.coordinates[0][0].map(
  ([lon, lat]) => `(${(lon + 4.5) * scaleX},${(lat - 36.75) * scaleY})`
);
const template = readFileSync(root + "scripts/maps-functions.sql.template", "utf8");
const sql = template
  .replaceAll("@SCALE_X@", String(scaleX))
  .replaceAll("@SCALE_Y@", String(scaleY))
  .replace("@POLYGON@", "(" + vertices.join(",") + ")")
  .replace("@VERTICES@", "{" + vertices.map((p) => '"' + p + '"').join(",") + "}");
const target = root + "src/main/resources/db/migration/V5__maps_geography.sql";
if (process.argv.includes("--check")) {
  if (readFileSync(target, "utf8") !== sql) throw Error("Generated migration differs");
} else writeFileSync(target, sql);
console.log("Verified IGN province and generated geography: " + vertices.length + " vertices");
