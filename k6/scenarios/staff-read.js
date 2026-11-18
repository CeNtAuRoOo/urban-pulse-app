import { sleep } from "k6";
import { actor } from "../lib/auth.js";
import { choice, number, uuid } from "../lib/config.js";
import { duplicateCandidates, processingItem } from "../lib/checks.js";
import { request } from "../lib/http.js";
import { optionsFor } from "../lib/metrics.js";
import { internalPage, preflight, reviewOutcome } from "../lib/reads.js";

const flow = choice("STAFF_FLOW", "processing", ["processing", "duplicates"]);
const seconds = number("DURATION_SECONDS", 30, 10, 120);
const identity = actor(seconds + 105, true, "OPERATOR");
if (identity.role === "CITIZEN") throw new Error("staff-read exige ACTOR=OPERATOR o ADMIN.");
const expectedCandidate = flow === "duplicates" ? uuid("DUPLICATE_CANDIDATE_ID", false) : undefined;
export const options = optionsFor(
  { [flow]: { executor: "constant-vus", vus: 1, duration: `${seconds}s`, gracefulStop: "15s" } },
  flow === "processing"
    ? ["reviews", "priority_decisions", "information_responses", "review_outcome"]
    : ["duplicate_candidates"]
);
export function setup() {
  preflight(identity, true);
}
export default function () {
  if (flow === "processing") {
    for (const resource of ["reviews", "priority-decisions", "information-responses"]) {
      internalPage(identity, resource, (item) => processingItem(resource, item));
    }
    reviewOutcome(identity);
  } else {
    request(
      identity,
      "GET",
      `/incidents/${identity.incidentId}/duplicate-candidates`,
      "/incidents/{incidentId}/duplicate-candidates",
      "duplicate_candidates",
      (data) => duplicateCandidates(data, identity.incidentId, expectedCandidate)
    );
  }
  sleep(5);
}
