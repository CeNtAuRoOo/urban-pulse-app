import encoding from "k6/encoding";
import { choice, isUuid, uuid } from "./config.js";

export function actor(maxSeconds, needsIncident = false, defaultRole = "CITIZEN") {
  const role = choice("ACTOR", defaultRole, ["CITIZEN", "OPERATOR", "ADMIN"]);
  const token = __ENV[`${role}_TOKEN`];
  let claims;
  try {
    if (!token || !/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(token)) throw new Error();
    claims = JSON.parse(encoding.b64decode(token.split(".")[1], "rawurl", "s"));
  } catch (_) {
    throw new Error(`${role}_TOKEN: JWT de prueba ausente o malformado (no incluir Bearer).`);
  }
  const now = Date.now() / 1000;
  if (
    !claims ||
    !isUuid(claims.sub) ||
    !Number.isInteger(claims.ver) ||
    claims.ver < 0 ||
    !Number.isFinite(claims.iat) ||
    claims.iat > now ||
    !Number.isFinite(claims.exp) ||
    claims.exp <= now + maxSeconds + 60
  ) {
    throw new Error(
      `${role}_TOKEN: claims inválidos o vigencia insuficiente para duración máxima + margen de 60 s.`
    );
  }
  // Decodificar no valida firma, revocación ni roles: la API los revalida en PostgreSQL.
  return {
    role,
    token,
    subject: claims.sub.toLowerCase(),
    incidentId: needsIncident ? uuid(`${role}_INCIDENT_ID`) : undefined,
  };
}
