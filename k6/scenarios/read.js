import { actor } from "../lib/auth.js";
import { staffQuery } from "../lib/config.js";
import { optionsFor } from "../lib/metrics.js";
import { readProfile, readWeights } from "../lib/profiles.js";
import { preflight, weightedRead } from "../lib/reads.js";

const profile = readProfile();
const identity = actor(profile.maxSeconds + 90, true);
const weights = readWeights(identity.role);
const search = identity.role === "CITIZEN" ? undefined : staffQuery();
const operations = ["mine", "detail", "history", "categories", "departments", "staff_list"];
export const options = optionsFor(
  { [profile.kind]: profile.scenario },
  operations.filter((_, index) => weights[index] > 0)
);
export function setup() {
  return preflight(identity, true);
}
export default function (data) {
  weightedRead({ ...identity, reporterId: data.reporterId }, weights, search);
}
