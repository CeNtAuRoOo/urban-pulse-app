import { useState } from "react";
import {
  Badge,
  Button,
  date,
  Details,
  Feedback,
  Field,
  Pager,
  Panel,
  QueryState,
  roleNames,
  Select,
  statusNames,
} from "../components/ui";
import { useAction, useResource } from "../services/hooks";
import { useSession } from "../services/session";
import type { Query } from "../services/http";
import type { Department, Invitation, Page } from "../types/api";
const roles = ["ADMIN", "OPERATOR", "TECHNICIAN", "DEPARTMENT_COORDINATOR"];
export function Invitations() {
  const { api } = useSession();
  const action = useAction();
  const departments = useResource<Department[]>("/departments");
  const [page, setPage] = useState(1);
  const [filters, setFilters] = useState<Query>({});
  const [selected, setSelected] = useState("");
  const [revision, setRevision] = useState(0);
  const list = useResource<Page<Invitation>>("/staff-invitations", { page, pageSize: 20, ...filters });
  const [creating, setCreating] = useState(false);
  const [role, setRole] = useState("OPERATOR");
  const [email, setEmail] = useState("");
  const [departmentId, setDepartmentId] = useState("");
  const departmental = role === "TECHNICIAN" || role === "DEPARTMENT_COORDINATOR";
  const changed = () => {
    list.reload();
    setRevision((value) => value + 1);
  };
  return (
    <>
      <div className="toolbar">
        <h1>Invitaciones al personal</h1>
        <Button onClick={() => setCreating((value) => !value)}>
          {creating ? "Cerrar formulario" : "Crear invitación"}
        </Button>
        <Button secondary disabled={list.loading || action.busy} onClick={changed}>
          Actualizar
        </Button>
      </div>
      <Feedback error={action.error} success={action.success} />
      <QueryState {...departments} />
      {creating && (
        <Panel title="Emitir invitación">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void action.run(async () => {
                if (
                  departmental &&
                  !departments.result?.data.some((item) => item.id === departmentId && item.active)
                )
                  throw new Error("Selecciona un departamento activo.");
                const response = await api<Invitation>("/staff-invitations", {
                  method: "POST",
                  body: { email: email.trim(), role, ...(departmental ? { departmentId } : {}) },
                });
                setSelected(response.data.id);
                changed();
                setCreating(false);
                setEmail("");
                action.setSuccess(
                  `Invitación creada · HTTP ${response.status}. Se ha solicitado la entrega por correo. El código solo se consulta en el correo o en Mailpit.`
                );
              });
            }}
          >
            <fieldset disabled={action.busy}>
              <Field
                label="Correo del destinatario"
                type="email"
                required
                maxLength={254}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
              <Select
                label="Rol invitado"
                value={role}
                onChange={(e) => {
                  setRole(e.target.value);
                  setDepartmentId("");
                }}
              >
                {roles.map((value) => (
                  <option value={value} key={value}>
                    {roleNames[value]}
                  </option>
                ))}
              </Select>
              {departmental && (
                <Select
                  label="Departamento activo"
                  required
                  value={departmentId}
                  onChange={(e) => setDepartmentId(e.target.value)}
                  disabled={!departments.result}
                >
                  <option value="">Selecciona un departamento</option>
                  {departments.result?.data
                    .filter((item) => item.active)
                    .map((item) => (
                      <option value={item.id} key={item.id}>
                        {item.name}
                      </option>
                    ))}
                </Select>
              )}
              <Button type="submit">Solicitar invitación por correo</Button>
            </fieldset>
          </form>
        </Panel>
      )}
      <Panel>
        <form
          className="filters"
          onSubmit={(e) => {
            e.preventDefault();
            const data = new FormData(e.currentTarget);
            setPage(1);
            setFilters(
              Object.fromEntries(
                ["status", "email", "role", "departmentId"].map((key) => [
                  key,
                  String(data.get(key) || "").trim(),
                ])
              )
            );
          }}
        >
          <Select name="status" label="Estado">
            <option value="">Todos</option>
            {["ACTIVE", "REDEEMED", "REVOKED", "EXPIRED"].map((value) => (
              <option value={value} key={value}>
                {statusNames[value]}
              </option>
            ))}
          </Select>
          <Field name="email" label="Correo" type="email" maxLength={254} />
          <Select name="role" label="Rol">
            <option value="">Todos</option>
            {roles.map((value) => (
              <option value={value} key={value}>
                {roleNames[value]}
              </option>
            ))}
          </Select>
          <Select name="departmentId" label="Departamento" disabled={!departments.result}>
            <option value="">Todos</option>
            {departments.result?.data.map((item) => (
              <option value={item.id} key={item.id}>
                {item.name}
                {item.active ? "" : " (inactivo)"}
              </option>
            ))}
          </Select>
          <Button type="submit">Aplicar filtros</Button>
        </form>
        <QueryState {...list} empty={list.result?.data.data.length === 0} />
        {list.result && (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Correo</th>
                    <th>Rol</th>
                    <th>Estado</th>
                    <th>Caducidad</th>
                    <th>Consulta</th>
                  </tr>
                </thead>
                <tbody>
                  {list.result.data.data.map((item) => (
                    <tr key={item.id}>
                      <td>{item.email}</td>
                      <td>{roleNames[item.role]}</td>
                      <td>
                        <Badge>{statusNames[item.status]}</Badge>
                      </td>
                      <td>{date(item.expiresAt)}</td>
                      <td>
                        <Button secondary onClick={() => setSelected(item.id)}>
                          Ver detalle
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager pagination={list.result.data.pagination} onPage={setPage} />
          </>
        )}
      </Panel>
      {selected && (
        <InvitationDetail
          key={`${selected}-${revision}`}
          id={selected}
          onChanged={(status) => {
            changed();
            action.setSuccess(`Invitación revocada · HTTP ${status}`);
          }}
        />
      )}
    </>
  );
}
function InvitationDetail({ id, onChanged }: { id: string; onChanged: (status: number) => void }) {
  const { api } = useSession();
  const resource = useResource<Invitation>(`/staff-invitations/${id}`);
  const action = useAction();
  const [revoked, setRevoked] = useState(false);
  const item = resource.result?.data;
  return (
    <Panel title="Metadatos de la invitación">
      <QueryState {...resource} />
      <Feedback error={action.error} success={action.success} />
      {item && (
        <>
          <Details
            entries={[
              ["Identificador", item.id],
              ["Correo", item.email],
              ["Rol", roleNames[item.role]],
              ["Departamento", item.departmentId || "Ámbito global"],
              ["Estado", statusNames[item.status]],
              ["Creada por", item.createdBy],
              ["Creación", date(item.createdAt)],
              ["Caducidad", date(item.expiresAt)],
              ...(item.redeemedAt
                ? ([
                    ["Canje", date(item.redeemedAt)],
                    ["Canjeada por", item.redeemedBy || "—"],
                  ] as [string, string][])
                : []),
              ...(item.revokedAt
                ? ([
                    ["Revocación", date(item.revokedAt)],
                    ["Revocada por", item.revokedBy || "—"],
                  ] as [string, string][])
                : []),
            ]}
          />
          {item.status === "ACTIVE" && !revoked && (
            <p>
              <Button
                disabled={action.busy}
                onClick={() =>
                  void action.run(async () => {
                    const response = await api(`/staff-invitations/${id}/revocations`, { method: "POST" });
                    setRevoked(true);
                    action.setSuccess(`Invitación revocada · HTTP ${response.status}`);
                    onChanged(response.status);
                  })
                }
              >
                Revocar invitación activa
              </Button>
            </p>
          )}
        </>
      )}
    </Panel>
  );
}
export function RedeemInvitation() {
  const { api, refresh, session } = useSession();
  const action = useAction();
  const [code, setCode] = useState("");
  const [redeemed, setRedeemed] = useState(false);
  return (
    <>
      <h1>Canjear invitación</h1>
      <Panel>
        <p>
          Introduce el código recibido por correo para <strong>{session?.user.email}</strong>. La invitación
          debe estar dirigida al correo de tu cuenta.
        </p>
        <Feedback error={action.error} success={action.success} />
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void action.run(async () => {
              try {
                const response = await api("/users/me/staff-invitation-redemptions", {
                  method: "POST",
                  body: { invitationCode: code.trim() },
                });
                setRedeemed(true);
                action.setSuccess(`Invitación canjeada · HTTP ${response.status}. Actualizando permisos.`);
                await refresh();
                action.setSuccess(
                  `Invitación canjeada · HTTP ${response.status}. Perfil y permisos actualizados.`
                );
              } finally {
                setCode("");
              }
            });
          }}
        >
          <fieldset disabled={action.busy || redeemed}>
            <Field
              label="Código de invitación"
              type="password"
              autoComplete="off"
              required
              pattern="[A-Za-z0-9_\-]{42}[AEIMQUYcgkosw048]"
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
            <Button type="submit">Canjear invitación</Button>
          </fieldset>
        </form>
        {redeemed && (
          <Button
            secondary
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                await refresh();
                action.setSuccess("Perfil y permisos actualizados.");
              })
            }
          >
            Actualizar perfil y permisos
          </Button>
        )}
      </Panel>
    </>
  );
}
