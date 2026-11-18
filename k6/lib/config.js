import { isUuid, states } from "./contracts.js";
export { isUuid, states } from "./contracts.js";

export function number(name, fallback, min, max, integer = true) {
  if (__ENV[name] !== undefined && !__ENV[name].trim()) throw new Error(`${name}: no puede estar vacío.`);
  const value = __ENV[name] === undefined ? fallback : Number(__ENV[name]);
  if (!Number.isFinite(value) || value < min || value > max || (integer && !Number.isInteger(value))) {
    throw new Error(`${name}: valor fuera de ${min}..${max}`);
  }
  return value;
}

export function choice(name, fallback, values) {
  const value = __ENV[name] === undefined ? fallback : __ENV[name];
  if (!value.trim()) throw new Error(`${name}: no puede estar vacío.`);
  if (!values.includes(value)) throw new Error(`${name}: usar ${values.join(", ")}`);
  return value;
}

export function requireFlag(name) {
  if (__ENV[name] !== "1") throw new Error(`Se requiere ${name}=1 en un entorno de pruebas autorizado.`);
}

export function uuid(name, required = true) {
  const value = __ENV[name];
  if (value === undefined && !required) return undefined;
  if (!isUuid(value)) throw new Error(`${name}: se requiere el UUID real del fixture de pruebas.`);
  return value.toLowerCase();
}

export const baseUrl = (__ENV.BASE_URL === undefined ? "http://127.0.0.1:8080" : __ENV.BASE_URL).replace(
  /\/$/,
  ""
);
// Solo orígenes completos: sin usuario, ruta, query ni fragmento que alteren el destino.
const origin =
  /^(https?):\/\/(localhost|127\.0\.0\.1|\[::1\]|[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?)(?::([0-9]{1,5}))?$/i.exec(
    baseUrl
  );
if (!origin || (origin[3] && (Number(origin[3]) < 1 || Number(origin[3]) > 65535))) {
  throw new Error("BASE_URL debe ser un origen HTTP(S) sin ruta, credenciales ni parámetros.");
}
if (__ENV.K6_ENVIRONMENT !== "isolated-test") {
  throw new Error(
    "Confirmar aislamiento de API y BD mediante K6_ENVIRONMENT=isolated-test. Producción está excluida."
  );
}
const local = ["localhost", "127.0.0.1", "[::1]"].includes(origin[2].toLowerCase());
if (
  !local &&
  (__ENV.K6_ALLOW_REMOTE !== "1" || __ENV.K6_REMOTE_ORIGIN !== baseUrl || origin[1] !== "https")
) {
  throw new Error(
    "Destino remoto bloqueado. Solo pruebas autorizadas con HTTPS, K6_ALLOW_REMOTE=1 y K6_REMOTE_ORIGIN idéntico a BASE_URL."
  );
}

export const timeoutSeconds = number("HTTP_TIMEOUT_SECONDS", 10, 1, 30);
export const page = number("PAGE", 1, 1, 10000);
export const pageSize = number("PAGE_SIZE", 20, 1, 100);
export const pagination = `page=${page}&pageSize=${pageSize}`;
export const pauseSeconds = number("PAUSE_SECONDS", 1, 0.5, 30, false);

export function query(parameters) {
  return Object.entries(parameters)
    .filter(([, value]) => value !== undefined)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join("&");
}

export function staffQuery() {
  const status = choice("INCIDENT_STATUS", "REPORTED", states);
  // Los valores se contrastan con el enum del backend; no se aceptan filtros futuros.
  const priority =
    __ENV.INCIDENT_PRIORITY !== undefined
      ? choice("INCIDENT_PRIORITY", "", ["LOW", "MEDIUM", "HIGH", "CRITICAL"])
      : undefined;
  return query({
    page,
    pageSize,
    status,
    categoryId: uuid("CATEGORY_ID", false),
    priority,
    sort: choice("INCIDENT_SORT", "priority,desc", ["priority,asc", "priority,desc"]),
  });
}

export function mapQuery() {
  const minLat = number("MAP_MIN_LAT", 36, -90, 90, false);
  const minLon = number("MAP_MIN_LON", -6, -180, 180, false);
  const maxLat = number("MAP_MAX_LAT", 38, -90, 90, false);
  const maxLon = number("MAP_MAX_LON", -3, -180, 180, false);
  const zoom = number("MAP_ZOOM", 8, 0, 22);
  const maxSpan = Math.min(360, 1440 / Math.pow(2, zoom));
  if (minLat >= maxLat || minLon >= maxLon || maxLat - minLat > maxSpan || maxLon - minLon > maxSpan) {
    throw new Error("Viewport MAP_* incompatible con WGS84 o con MAP_ZOOM.");
  }
  const expectedId = uuid("MAP_EXPECTED_INCIDENT_ID", false);
  if (expectedId && zoom < 18)
    throw new Error("MAP_EXPECTED_INCIDENT_ID exige zoom >=18 para comprobar un marcador individual.");
  return { search: query({ minLat, minLon, maxLat, maxLon, zoom }), expectedId };
}
