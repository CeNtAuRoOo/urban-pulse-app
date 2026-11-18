import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { createContext, SourceTextModule, SyntheticModule } from "node:vm";

const root = new URL("../", import.meta.url);

// Solo módulos locales y dobles explícitos. El contexto no dispone de fetch, process ni sockets.
export async function offline(entry, env = {}, responder) {
  const calls = [],
    samples = [],
    checks = [],
    sleeps = [];
  const context = createContext({ __ENV: { K6_ENVIRONMENT: "isolated-test", ...env }, __VU: 1, __ITER: 0 });
  class Metric {
    constructor(name) {
      this.name = name;
    }
    add(value, tags) {
      samples.push({ name: this.name, value, tags: { ...tags } });
    }
  }
  const stubs = {
    k6: {
      check(value, predicates, tags) {
        const results = Object.values(predicates).map((predicate) => Boolean(predicate(value)));
        checks.push({ results, tags: { ...tags } });
        return results.every(Boolean);
      },
      group(_name, callback) {
        return callback();
      },
      sleep(seconds) {
        sleeps.push(seconds);
      },
    },
    "k6/http": {
      default: {
        expectedStatuses(...statuses) {
          return { statuses };
        },
        request(method, url, body, params) {
          const call = { method, url, body, params };
          calls.push(call);
          if (!responder) throw new Error("HTTP offline no preparado: no existe transporte de red.");
          return responder(call, calls.length);
        },
      },
    },
    "k6/metrics": { Rate: Metric, Counter: Metric },
    "k6/execution": {
      default: {
        test: {
          abort(message) {
            throw new Error(`ABORT: ${message}`);
          },
        },
      },
    },
    "k6/encoding": {
      default: {
        b64decode(value, encoding, format) {
          if (encoding !== "rawurl" || format !== "s") throw new Error("Decodificación inesperada.");
          return Buffer.from(value, "base64url").toString("utf8");
        },
      },
    },
  };
  const cache = new Map();
  function moduleFor(specifier, parent = root.href) {
    const key = Object.hasOwn(stubs, specifier) ? specifier : new URL(specifier, parent).href;
    if (cache.has(key)) return cache.get(key);
    let module;
    if (Object.hasOwn(stubs, key)) {
      module = new SyntheticModule(
        Object.keys(stubs[key]),
        function () {
          for (const [name, value] of Object.entries(stubs[key])) this.setExport(name, value);
        },
        { context, identifier: key }
      );
    } else {
      if (!key.startsWith(root.href) || !key.endsWith(".js"))
        throw new Error(`Import offline prohibido: ${specifier}`);
      module = new SourceTextModule(readFileSync(fileURLToPath(key), "utf8"), { context, identifier: key });
    }
    cache.set(key, module);
    return module;
  }
  const module = moduleFor(entry);
  await module.link((specifier, parent) => moduleFor(specifier, parent.identifier));
  await module.evaluate();
  return { api: module.namespace, context, calls, samples, checks, sleeps };
}

export function response(data, status = 200, headers = {}) {
  return {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store", ...headers },
    json() {
      return structuredClone(data);
    },
  };
}

export function syntheticToken(subject, overrides = {}) {
  const now = Math.floor(Date.now() / 1000);
  const claims = { sub: subject, ver: 0, iat: now - 60, exp: now + 7200, ...overrides };
  // No se firma ni se acepta por la API: solo prueba decodificación/configuración offline.
  return ["offline", Buffer.from(JSON.stringify(claims)).toString("base64url"), "unsigned"].join(".");
}
