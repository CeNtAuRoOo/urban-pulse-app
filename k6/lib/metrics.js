import { Counter, Rate } from "k6/metrics";
import { number } from "./config.js";

export const semanticFailures = new Rate("semantic_failures");
export const unexpectedResponses = new Counter("unexpected_responses");

export function optionsFor(scenarios, operations, extraThresholds = {}, { smoke = false } = {}) {
  const p95 = number("P95_MS", 500, 1, 60000);
  const errorRate = number("ERROR_RATE", 0.01, 0.000001, 1, false);
  const checkRate = number("CHECK_RATE", 0.99, 0, 1, false);
  // Smoke exige fiabilidad completa; con una iteración el p95 no es una estimación de capacidad.
  const thresholds = smoke
    ? {
        "http_req_failed{traffic:nominal}": ["rate==0"],
        "checks{traffic:nominal}": ["rate==1"],
        semantic_failures: ["rate==0"],
        ...extraThresholds,
      }
    : {
        "http_req_duration{traffic:nominal}": [`p(95)<${p95}`],
        "http_req_failed{traffic:nominal}": [`rate<${errorRate}`],
        "checks{traffic:nominal}": [`rate>${checkRate}`],
        semantic_failures: [`rate<${errorRate}`],
        ...extraThresholds,
      };
  for (const operation of smoke ? [] : operations) {
    thresholds[`http_req_duration{traffic:nominal,operation:${operation}}`] = [`p(95)<${p95}`];
  }
  return {
    scenarios,
    thresholds,
    maxRedirects: 0,
    setupTimeout: "90s",
    // Excluir url evita UUID y queries dinámicas en las series exportadas.
    systemTags: ["status", "method", "name", "group", "check", "scenario", "expected_response", "error_code"],
    summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
  };
}
