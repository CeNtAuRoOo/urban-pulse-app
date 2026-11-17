import http from "k6/http";
import { check } from "k6";
import { baseUrl, timeoutSeconds } from "./config.js";
import { semanticFailures, unexpectedResponses } from "./metrics.js";
import { header } from "./headers.js";
export { header } from "./headers.js";

export function request(
  actor,
  method,
  path,
  template,
  operation,
  validate,
  { status = 200, body, headers = {}, traffic = "nominal", noStore = true, extraChecks = {} } = {}
) {
  const tags = { name: `${method} /api/v1${template}`, operation, traffic };
  const response = http.request(
    method,
    `${baseUrl}/api/v1${path}`,
    body === undefined ? null : JSON.stringify(body),
    {
      headers: {
        Authorization: `Bearer ${actor.token}`,
        Accept: "application/json",
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...headers,
      },
      timeout: `${timeoutSeconds}s`,
      redirects: 0,
      tags,
      responseCallback: http.expectedStatuses(status),
    }
  );
  let data = null;
  try {
    data = response.json();
  } catch (_) {
    /* El check conserva el fallo HTTP/JSON sin lanzar otra excepción. */
  }
  let semantic = false;
  try {
    semantic = data !== null && Boolean(validate(data));
  } catch (_) {
    semantic = false;
  }
  const checks = {
    "HTTP esperado": () => response.status === status,
    "Content-Type JSON": () => /^application\/json(?:;|$)/i.test(header(response, "Content-Type")),
    "estructura y semántica válidas": () => semantic,
    ...(noStore
      ? {
          "respuesta privada no-store": () =>
            header(response, "Cache-Control")
              .split(",")
              .some((directive) => directive.trim().toLowerCase() === "no-store"),
        }
      : {}),
  };
  for (const [label, predicate] of Object.entries(extraChecks)) {
    checks[label] = () => {
      try {
        return Boolean(predicate(response, data));
      } catch (_) {
        return false;
      }
    };
  }
  const ok = check(response, checks, tags);
  if (response.status >= 400) {
    check(
      response,
      {
        "error con ProblemDetail": () =>
          /^application\/problem\+json(?:;|$)/i.test(header(response, "Content-Type")) &&
          data &&
          data.status === response.status &&
          typeof data.title === "string",
        ...(response.status === 429
          ? { "429 con Retry-After positivo": () => /^[1-9][0-9]*$/.test(header(response, "Retry-After")) }
          : {}),
      },
      { ...tags, traffic: "diagnostic" }
    );
  }
  if (traffic === "nominal") {
    semanticFailures.add(!ok, tags);
    if (response.status !== status) {
      const kind =
        response.status === 429
          ? "rate_limit"
          : response.status === 412
            ? "precondition"
            : response.status === 503
              ? "unavailable"
              : "other";
      unexpectedResponses.add(1, { operation, kind });
    }
  }
  return { response, data, ok };
}
