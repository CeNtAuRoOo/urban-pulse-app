import type { Incident, User } from "../types/api";
export function permissions(user?: User, incident?: Incident) {
  const authenticated = !!user?.active && !!user.emailVerified && user.roles.includes("CITIZEN");
  const admin = authenticated && user!.roles.includes("ADMIN");
  const staff = authenticated && (admin || user!.roles.includes("OPERATOR"));
  const owner = authenticated && incident?.reporter?.id === user!.id;
  const visible = authenticated && (owner || staff);
  return {
    authenticated,
    admin,
    staff,
    owner,
    visible,
    edit: visible && incident?.status === "REPORTED",
    attachments: visible && ["REPORTED", "AWAITING_INFORMATION"].includes(incident?.status || ""),
    review: staff && !owner && ["REPORTED", "AWAITING_INFORMATION"].includes(incident?.status || ""),
    respond: owner && incident?.status === "AWAITING_INFORMATION",
    prioritize: staff && incident?.status === "VALIDATED" && !incident.assignedDepartment,
  };
}
