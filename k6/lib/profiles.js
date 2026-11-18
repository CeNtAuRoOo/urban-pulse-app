import { choice, number, requireFlag } from "./config.js";

export function readProfile() {
  const kind = choice("READ_PROFILE", "load", ["load", "stress", "spike", "soak"]);
  const vus = number("VUS", 3, 1, 50);
  const seconds = number("DURATION_SECONDS", kind === "soak" ? 900 : 60, 10, kind === "soak" ? 1800 : 300);
  if (kind !== "load" || vus > 10 || seconds > 300) requireFlag("K6_ALLOW_HEAVY");
  const common = { gracefulStop: "15s" };
  if (kind === "load" || kind === "soak") {
    return {
      kind,
      maxSeconds: seconds + 15,
      scenario: { executor: "constant-vus", vus, duration: `${seconds}s`, ...common },
    };
  }
  const peak = number("PEAK_VUS", 10, 3, 50);
  const hold = number("STAGE_SECONDS", 30, 10, 120);
  const stages =
    kind === "stress"
      ? [2, 2, Math.max(2, Math.floor(peak / 2)), Math.max(2, Math.floor(peak / 2)), peak, peak, 0].map(
          (target) => ({ duration: `${hold}s`, target })
        )
      : [
          { duration: `${hold}s`, target: 1 },
          { duration: "5s", target: peak },
          { duration: `${hold}s`, target: peak },
          { duration: "5s", target: 1 },
          { duration: `${hold}s`, target: 1 },
          { duration: "5s", target: 0 },
        ];
  return {
    kind,
    maxSeconds: (kind === "stress" ? hold * 7 : hold * 3 + 15) + 15,
    scenario: {
      executor: "ramping-vus",
      startVUs: kind === "spike" ? 1 : 0,
      stages,
      gracefulRampDown: "15s",
      ...common,
    },
  };
}

export function readWeights(role) {
  const raw =
    __ENV.READ_WEIGHTS === undefined
      ? role === "CITIZEN"
        ? "40,30,15,10,5,0"
        : "25,25,15,10,5,20"
      : __ENV.READ_WEIGHTS;
  const weights = raw.split(",").map(Number);
  if (
    weights.length !== 6 ||
    raw.split(",").some((value) => !value.trim()) ||
    weights.some((value) => !Number.isInteger(value) || value < 0) ||
    weights.reduce((sum, value) => sum + value, 0) !== 100 ||
    (role === "CITIZEN" && weights[5] !== 0)
  ) {
    throw new Error("READ_WEIGHTS: seis enteros >=0, suma 100; último peso 0 para CITIZEN.");
  }
  return weights;
}
