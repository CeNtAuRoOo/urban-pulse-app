import { useUnsavedChanges } from "../services/form-guard";
import { useState } from "react";
import {
  NavLink,
  Badge,
  Body,
  Button,
  Confirm,
  DataList,
  Details,
  Feedback,
  Field,
  PageHeading,
  Pager,
  Panel,
  QueryState,
  Select,
  date,
  options,
  roleNames,
} from "../components/ui";
import { useAction, useResource } from "../services/hooks";
import { useSession } from "../services/session";
import { displayName } from "../services/validation";
import type { Page, User } from "../types/api";
import { PasswordForm } from "./Auth";

export function UserDetails({ user }: { user: User }) {
  return (
    <>
      <Details
        entries={[
          ["Nombre", user.displayName],
          ["Correo", user.email],
          ["Verificación", user.emailVerified ? "Correo verificado" : "Pendiente"],
          ["Estado", user.active ? "Activo" : "Inactivo"],
          ["Alta", date(user.createdAt)],
          ["Roles globales", user.roles.map((x) => roleNames[x]).join(", ")],
        ]}
      />
      <Body muted>Pertenencias departamentales</Body>
      {user.departmentMemberships.length ? (
        user.departmentMemberships.map((m, i) => (
          <Panel key={`${m.department.id}-${i}`}>
            <Body>
              {m.department.name} · {roleNames[m.role]}
            </Body>
            <Body muted>
              {date(m.joinedAt)} — {m.leftAt ? date(m.leftAt) : "Vigente"}
            </Body>
          </Panel>
        ))
      ) : (
        <Body muted>No hay pertenencias departamentales.</Body>
      )}
    </>
  );
}
export function Profile() {
  const { session, api, refresh } = useSession(),
    action = useAction();
  const [name, setName] = useState(session?.user.displayName || "");
  const guard = useUnsavedChanges(name !== (session?.user.displayName || ""));
  return (
    <>
      <PageHeading title="Mi cuenta" description="Tu identidad y tus accesos en UrbanPulse." />
      <Panel title="Perfil">
        <Feedback error={action.error} success={action.success} />
        {session ? <UserDetails user={session.user} /> : null}
        <Field label="Nombre público" value={name} onChangeText={setName} autoComplete="name" />
        <Button
          disabled={action.busy}
          onPress={() =>
            void action.run(async () => {
              await api("/users/me", {
                method: "PATCH",
                body: { displayName: displayName(name) },
              });
              guard.saved();
              setName(displayName(name));
              await refresh();
              action.setSuccess("Nombre actualizado.");
            })
          }
        >
          Guardar nombre
        </Button>
        <Button
          secondary
          disabled={action.busy}
          onPress={() => guard.discard(() => setName(session?.user.displayName || ""))}
        >
          Cancelar cambios del perfil
        </Button>
        <NavLink href="/redeem">Canjear invitación de personal</NavLink>
      </Panel>
      <PasswordForm />
    </>
  );
}
export function Users() {
  const [page, setPage] = useState(1),
    [role, setRole] = useState(""),
    [active, setActive] = useState(""),
    [selected, setSelected] = useState("");
  const resource = useResource<Page<User>>("/users", {
    page,
    pageSize: 20,
    role,
    active,
  });
  return (
    <>
      <PageHeading title="Usuarios" description="Consulta las cuentas y administra su estado." />
      <Panel>
        <Select
          label="Rol"
          value={role}
          onChange={(v) => {
            setRole(v);
            setPage(1);
          }}
          options={options(roleNames, "Todos")}
        />
        <Select
          label="Estado"
          value={active}
          onChange={(v) => {
            setActive(v);
            setPage(1);
          }}
          options={[
            { value: "", label: "Todos" },
            { value: "true", label: "Activo" },
            { value: "false", label: "Inactivo" },
          ]}
        />
        <QueryState {...resource} empty={resource.result?.data.data.length === 0} />
        {resource.result ? (
          <>
            <DataList
              items={resource.result.data.data}
              columns={[
                {
                  label: "Persona",
                  grow: 2,
                  render: (u) => (
                    <>
                      <Body>{u.displayName}</Body>
                      <Body muted>{u.email}</Body>
                    </>
                  ),
                },
                {
                  label: "Roles",
                  render: (u) => <Body>{u.roles.map((x) => roleNames[x]).join(", ")}</Body>,
                },
                {
                  label: "Estado",
                  render: (u) => <Badge>{u.active ? "Activo" : "Inactivo"}</Badge>,
                },
                {
                  label: "Acciones",
                  render: (u) => (
                    <Button secondary onPress={() => setSelected(u.id)}>
                      Ver cuenta
                    </Button>
                  ),
                },
              ]}
            />
            <Pager pagination={resource.result.data.pagination} onPage={setPage} />
          </>
        ) : null}
      </Panel>
      {selected ? <AdminUser key={selected} id={selected} changed={resource.reload} /> : null}
    </>
  );
}
function AdminUser({ id, changed }: { id: string; changed: () => void }) {
  const resource = useResource<User>(`/users/${id}`),
    action = useAction(),
    { api, session, logout } = useSession();
  const user = resource.result?.data;
  return (
    <Panel title="Detalle de cuenta">
      <QueryState {...resource} />
      <Feedback error={action.error} success={action.success} />
      {user ? (
        <>
          <UserDetails user={user} />
          <Body muted>
            Los permisos se conceden por invitación. Las pertenencias históricas se conservan.
          </Body>
          <Confirm
            title={user.active ? "Desactivar cuenta" : "Activar cuenta"}
            message={
              id === session?.user.id
                ? "Esta operación cerrará tu sesión si desactivas tu cuenta."
                : "El cambio afecta al acceso de esta persona a UrbanPulse."
            }
            danger={user.active}
            disabled={action.busy}
            onConfirm={() =>
              void action.run(async () => {
                const result = await api<User>(`/users/${id}`, {
                  method: "PATCH",
                  body: { active: !user.active },
                });
                if (id === session?.user.id && !result.data.active) {
                  logout("Cuenta desactivada.");
                  return;
                }
                resource.setResult(result);
                changed();
                action.setSuccess("Estado actualizado.");
              })
            }
          >
            {user.active ? "Desactivar cuenta" : "Activar cuenta"}
          </Confirm>
        </>
      ) : null}
    </Panel>
  );
}
