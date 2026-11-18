import { actor } from "../lib/auth.js";
import { number, requireFlag } from "../lib/config.js";
import { optionsFor } from "../lib/metrics.js";
import { mine, preflight } from "../lib/reads.js";

const rate = number("RATE", 2, 1, 20);
const seconds = number("DURATION_SECONDS", 60, 10, 300);
const preAllocatedVUs = number("PREALLOCATED_VUS", 2, 1, 50);
const maxVUs = number("MAX_VUS", 5, preAllocatedVUs, 50);
if (rate > 5 || maxVUs > 10) requireFlag("K6_ALLOW_HEAVY");
const identity = actor(seconds + 105);
export const options = optionsFor(
  {
    arrival: {
      executor: "constant-arrival-rate",
      rate,
      timeUnit: "1s",
      duration: `${seconds}s`,
      preAllocatedVUs,
      maxVUs,
      gracefulStop: "15s",
    },
  },
  ["mine"],
  { dropped_iterations: ["count==0"] }
);
export function setup() {
  preflight(identity);
}
// Exactamente una petición por iteración, sin sleep, login, redirecciones ni reintentos.
export default function () {
  mine(identity);
}
