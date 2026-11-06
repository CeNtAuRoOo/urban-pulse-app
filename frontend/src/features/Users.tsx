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
} from "../components/ui";
import { useAction, useResource } from "../services/hooks";
import { useSession } from "../services/session";
import { displayName } from "../services/validation";
import type { Query } from "../services/http";
import type { Page, User } from "../types/api";
import { PasswordForm } from "./Auth";
function UserDetails({ user }: { user: User }) {
  return (
    <>
      <Details
        entries={[
          ["Identificador", user.id],
          ["Nombre", user.displayName],
          ["Correo", user.email],
          ["Correo verificado", user.emailVerified ? "Sí" : "No"],
          ["Estado", user.active ? "Activo" : "Inactivo"],
          ["Creación", date(user.createdAt)],
          ["Roles globales", user.roles.map((role) => roleNames[role]).join(", ") || "Sin roles globales"],
        ]}
      />
      <h3>Adscripciones departamentales</h3>
      {user.departmentMemberships.length === 0 ? (
        <p className="muted">Sin adscripciones departamentales.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Departamento</th>
                <th>Rol departamental</th>
                <th>Alta</th>
                <th>Baja</th>
              </tr>
            </thead>
            <tbody>
              {user.departmentMemberships.map((membership, index) => (
                <tr key={`${membership.department.id}-${membership.role}-${index}`}>
                  <td>{membership.department.name}</td>
                  <td>{roleNames[membership.role]}</td>
                  <td>{date(membership.joinedAt)}</td>
                  <td>{date(membership.leftAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
export function Profile() {
  const { session, api, refresh } = useSession();
  const action = useAction();
  const [name, setName] = useState(session?.user.displayName || "");
  return (
    <>
      <div className="toolbar">
        <h1>Mi perfil</h1>
        <Button
          secondary
          disabled={action.busy}
          onClick={() =>
            void action.run(async () => {
              await refresh();
              action.setSuccess("Perfil actualizado · HTTP 200");
            })
          }
        >
          Actualizar perfil
        </Button>
      </div>
      <Feedback error={action.error} success={action.success} />
      {session && (
        <Panel title="Datos de cuenta">
          <UserDetails user={session.user} />
        </Panel>
      )}
      <Panel title="Editar nombre público">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void action.run(async () => {
              const normalized = displayName(name);
              if (normalized === session?.user.displayName) throw new Error("No hay cambios para guardar.");
              const response = await api<User>("/users/me", {
                method: "PATCH",
                body: { displayName: normalized },
              });
              action.setSuccess(`Nombre guardado · HTTP ${response.status}`);
              await refresh();
              setName(response.data.displayName);
            });
          }}
        >
          <fieldset disabled={action.busy}>
            <Field label="Nombre público" required value={name} onChange={(e) => setName(e.target.value)} />
            <Button type="submit">Guardar nombre</Button>
          </fieldset>
        </form>
      </Panel>
      <PasswordForm />
    </>
  );
}
export function Users() {
  const [page, setPage] = useState(1);
  const [filters, setFilters] = useState<Query>({});
  const [selected, setSelected] = useState("");
  const resource = useResource<Page<User>>("/users", { page, pageSize: 20, ...filters });
  return (
    <>
      <div className="toolbar">
        <h1>Usuarios</h1>
        <Button secondary disabled={resource.loading} onClick={resource.reload}>
          Actualizar
        </Button>
      </div>
      <Panel>
        <form
          className="filters"
          onSubmit={(e) => {
            e.preventDefault();
            const form = new FormData(e.currentTarget);
            setPage(1);
            setFilters({ role: String(form.get("role")), active: String(form.get("active")) });
          }}
        >
          <Select label="Rol" name="role">
            <option value="">Todos</option>
            {Object.entries(roleNames).map(([role, label]) => (
              <option key={role} value={role}>
                {label}
              </option>
            ))}
          </Select>
          <Select label="Estado" name="active">
            <option value="">Todos</option>
            <option value="true">Activo</option>
            <option value="false">Inactivo</option>
          </Select>
          <Button type="submit">Aplicar filtros</Button>
        </form>
        <QueryState {...resource} empty={resource.result?.data.data.length === 0} />
        {resource.result && (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Nombre y correo</th>
                    <th>Roles globales</th>
                    <th>Estado</th>
                    <th>Consulta</th>
                  </tr>
                </thead>
                <tbody>
                  {resource.result.data.data.map((user) => (
                    <tr key={user.id}>
                      <td>
                        {user.displayName}
                        <small className="muted">{user.email}</small>
                      </td>
                      <td>{user.roles.map((role) => roleNames[role]).join(", ")}</td>
                      <td>
                        <Badge>{user.active ? "Activo" : "Inactivo"}</Badge>
                      </td>
                      <td>
                        <Button secondary onClick={() => setSelected(user.id)}>
                          Ver detalle
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager pagination={resource.result.data.pagination} onPage={setPage} />
          </>
        )}
      </Panel>
      {selected && <AdminUser key={selected} id={selected} onChanged={resource.reload} />}
    </>
  );
}
function AdminUser({ id, onChanged }: { id: string; onChanged: () => void }) {
  const { api, session, logout } = useSession();
  const resource = useResource<User>(`/users/${id}`);
  const action = useAction();
  return (
    <Panel title="Detalle de usuario">
      <QueryState {...resource} />
      <Feedback error={action.error} success={action.success} />
      {resource.result && (
        <>
          <UserDetails user={resource.result.data} />
          <p className="hint">
            Los permisos de personal se conceden mediante invitaciones. Aquí solo se modifica el estado de la
            cuenta.
          </p>
          {id === session?.user.id && (
            <p className="hint">Si desactivas tu propia cuenta, se cerrará tu sesión.</p>
          )}
          <Button
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                const active = !resource.result!.data.active;
                const response = await api<User>(`/users/${id}`, { method: "PATCH", body: { active } });
                if (id === session?.user.id && !active) {
                  logout(`Cuenta desactivada · HTTP ${response.status}`);
                  return;
                }
                resource.setResult(response);
                action.setSuccess(
                  `Cuenta ${response.data.active ? "activada" : "desactivada"} · HTTP ${response.status}`
                );
                onChanged();
              })
            }
          >
            {resource.result.data.active ? "Desactivar cuenta" : "Activar cuenta"}
          </Button>
        </>
      )}
    </Panel>
  );
}
