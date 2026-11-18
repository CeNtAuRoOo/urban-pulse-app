import { sleep } from "k6";
import { actor } from "../lib/auth.js";
import { mapQuery, number } from "../lib/config.js";
import { optionsFor } from "../lib/metrics.js";
import { mapRead, preflight } from "../lib/reads.js";

const seconds = number("DURATION_SECONDS", 60, 10, 300);
const interval = number("MAP_INTERVAL_SECONDS", 5, 2, 60);
const identity = actor(seconds + 105);
const viewport = mapQuery();
export const options = optionsFor(
  { map: { executor: "constant-vus", vus: 1, duration: `${seconds}s`, gracefulStop: "15s" } },
  ["map"]
);
export function setup() {
  preflight(identity);
}
export default function () {
  mapRead(identity, viewport);
  sleep(interval);
}
