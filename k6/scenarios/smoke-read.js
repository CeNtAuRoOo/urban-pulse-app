import { actor } from "../lib/auth.js";
import { mapQuery, staffQuery } from "../lib/config.js";
import { optionsFor } from "../lib/metrics.js";
import { preflight, smoke } from "../lib/reads.js";

const identity = actor(300, true);
const viewport = mapQuery();
const search = identity.role === "CITIZEN" ? undefined : staffQuery();
export const options = optionsFor(
  { smoke: { executor: "shared-iterations", vus: 1, iterations: 1, maxDuration: "3m" } },
  [
    "profile",
    "categories",
    "departments",
    "mine",
    "detail",
    "history",
    "review_outcome",
    "map",
    ...(identity.role === "CITIZEN" ? [] : ["staff_list"]),
  ],
  {},
  { smoke: true }
);
export function setup() {
  return preflight(identity, true);
}
export default function (data) {
  smoke({ ...identity, reporterId: data.reporterId }, viewport, search);
}
