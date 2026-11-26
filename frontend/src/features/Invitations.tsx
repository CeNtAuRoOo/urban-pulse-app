import { useRouter } from "expo-router";
import { useFormNavigation, useUnsavedChanges } from "../services/form-guard";
import { useState } from "react";
import {
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
  statusNames,
} from "../components/ui";
import { useAction, useResource } from "../services/hooks";
import { useSession } from "../services/session";
import type { Department, Invitation, Page } from "../types/api";
const invited = Object.fromEntries(Object.entries(roleNames).filter(([k]) => k !== "CITIZEN"));
export function Invitations() {
  const navigation = useFormNavigation();
  const [page, setPage] = useState(1),
    [filters, setFilters] = useState({
      email: "",
      role: "",
      status: "",
      departmentId: "",
    }),
    [selected, setSelected] = useState(""),
    [creating, setCreating] = useState(false);
  const list = useResource<Page<Invitation>>("/staff-invitations", {
      page,
      pageSize: 20,
      ...filters,
    }),
    departments = useResource<Department[]>("/departments");
  const filter = (key: keyof typeof filters, value: string) => {
    setPage(1);
    setFilters((v) => ({ ...v, [key]: value }));
  };
  return (
    <>
      <PageHeading
        title="Invitaciones"
        description="Incorpora personal sin crear cuentas ni compartir contraseñas."
      >
        <Button icon="plus" onPress={() => navigation.request(() => setCreating(!creating))}>
          {creating ? "Cerrar formulario" : "Nueva invitación"}
        </Button>
      </PageHeading>
      {creating ? (
        <InvitationForm
          cancel={() => setCreating(false)}
          departments={departments.result?.data || []}
          saved={(id) => {
            setSelected(id);
            setCreating(false);
            list.reload();
          }}
        />
      ) : null}
      <Panel>
        <Field
          label="Correo exacto"
          value={filters.email}
          onChangeText={(v) => filter("email", v)}
          keyboardType="email-address"
        />
        <Select
          label="Rol"
          value={filters.role}
          onChange={(v) => filter("role", v)}
          options={options(invited, "Todos")}
        />
        <Select
          label="Estado"
          value={filters.status}
          onChange={(v) => filter("status", v)}
          options={options(
            {
              ACTIVE: "Activa",
              REDEEMED: "Canjeada",
              REVOKED: "Revocada",
              EXPIRED: "Caducada",
            },
            "Todos"
          )}
        />
        <Select
          label="Departamento"
          value={filters.departmentId}
          onChange={(v) => filter("departmentId", v)}
          options={[
            { value: "", label: "Todos" },
            ...(departments.result?.data || []).map((d) => ({
              value: d.id,
              label: d.name,
            })),
          ]}
        />
        <QueryState {...list} empty={list.result?.data.data.length === 0} />
        {list.result ? (
          <>
            <DataList
              items={list.result.data.data}
              columns={[
                {
                  label: "Destinatario",
                  grow: 2,
                  render: (i) => <Body>{i.email}</Body>,
                },
                {
                  label: "Rol",
                  render: (i) => <Body>{roleNames[i.role]}</Body>,
                },
                {
                  label: "Estado",
                  render: (i) => <Badge>{statusNames[i.status]}</Badge>,
                },
                {
                  label: "Acciones",
                  render: (i) => (
                    <Button secondary onPress={() => setSelected(i.id)}>
                      Ver invitación
                    </Button>
                  ),
                },
              ]}
            />
            <Pager pagination={list.result.data.pagination} onPage={setPage} />
          </>
        ) : null}
      </Panel>
      {selected ? <InvitationDetail key={selected} id={selected} changed={list.reload} /> : null}
    </>
  );
}
function InvitationForm({
  departments,
  saved,
  cancel,
}: {
  departments: Department[];
  saved: (id: string) => void;
  cancel: () => void;
}) {
  const { api } = useSession(),
    action = useAction();
  const [email, setEmail] = useState(""),
    [role, setRole] = useState("OPERATOR"),
    [departmentId, setDepartment] = useState("");
  const guard = useUnsavedChanges(!!email || role !== "OPERATOR" || !!departmentId);
  const departmental = ["TECHNICIAN", "DEPARTMENT_COORDINATOR"].includes(role);
  return (
    <Panel title="Nueva invitación">
      <Feedback error={action.error} />
      <Field
        label="Correo del destinatario"
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        autoComplete="email"
      />
      <Select label="Rol" value={role} onChange={setRole} options={options(invited)} />
      {departmental ? (
        <Select
          label="Departamento activo"
          value={departmentId}
          onChange={setDepartment}
          options={[
            { value: "", label: "Selecciona un departamento" },
            ...departments.filter((d) => d.active).map((d) => ({ value: d.id, label: d.name })),
          ]}
        />
      ) : null}
      <Body muted>
        El código se enviará al correo del destinatario. La creación de la invitación no confirma la entrega
        del mensaje.
      </Body>
      <Confirm
        title="Emitir invitación"
        message={`Se concederá el rol ${roleNames[role]} cuando ${email} canjee el código con su cuenta verificada.`}
        disabled={action.busy}
        onConfirm={() =>
          void action.run(async () => {
            if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()))
              throw new Error("Revisa el correo del destinatario.");
            if (departmental && !departments.some((d) => d.id === departmentId && d.active))
              throw new Error("Selecciona un departamento activo.");
            const r = await api<Invitation>("/staff-invitations", {
              method: "POST",
              body: {
                email: email.trim(),
                role,
                ...(departmental ? { departmentId } : {}),
              },
            });
            guard.saved();
            saved(r.data.id);
          })
        }
      >
        Enviar invitación
      </Confirm>
      <Button secondary disabled={action.busy} onPress={() => guard.discard(cancel)}>
        Cancelar
      </Button>
    </Panel>
  );
}
function InvitationDetail({ id, changed }: { id: string; changed: () => void }) {
  const resource = useResource<Invitation>(`/staff-invitations/${id}`),
    action = useAction(),
    { api } = useSession();
  const item = resource.result?.data;
  return (
    <Panel title="Detalle de invitación">
      <QueryState {...resource} />
      <Feedback error={action.error} success={action.success} />
      {item ? (
        <>
          <Details
            entries={[
              ["Correo", item.email],
              ["Rol", roleNames[item.role]],
              ["Estado", statusNames[item.status]],
              ["Departamento", item.departmentId],
              ["Creada", date(item.createdAt)],
              ["Vence", date(item.expiresAt)],
              ["Canjeada", date(item.redeemedAt)],
              ["Revocada", date(item.revokedAt)],
            ]}
          />
          {item.status === "ACTIVE" ? (
            <Confirm
              title="Revocar invitación"
              message="El código dejará de permitir el acceso. Esta acción no se puede deshacer."
              danger
              disabled={action.busy}
              onConfirm={() =>
                void action.run(async () => {
                  await api(`/staff-invitations/${id}/revocations`, {
                    method: "POST",
                  });
                  resource.reload();
                  changed();
                  action.setSuccess("Invitación revocada.");
                })
              }
            >
              Revocar invitación
            </Confirm>
          ) : null}
        </>
      ) : null}
    </Panel>
  );
}
export function Redeem() {
  const router = useRouter();
  const { api, refresh } = useSession(),
    action = useAction();
  const [code, setCode] = useState("");
  const guard = useUnsavedChanges(!!code);
  const cancel = () => guard.discard(() => router.dismissTo("/profile"));
  return (
    <>
      <Button secondary icon="arrow-left" onPress={cancel}>
        Volver a mi cuenta
      </Button>
      <PageHeading
        title="Incorporarte al equipo"
        description="Conserva tu cuenta y añade los permisos de tu invitación."
      />
      <Panel>
        <Feedback error={action.error} success={action.success} />
        <Field label="Código de invitación" value={code} onChangeText={setCode} autoCorrect={false} />
        <Button
          disabled={action.busy}
          onPress={() =>
            void action.run(async () => {
              if (!/^[A-Za-z0-9_-]{43}$/.test(code.trim()))
                throw new Error("Introduce el código de 43 caracteres del correo.");
              await api("/users/me/staff-invitation-redemptions", {
                method: "POST",
                body: { invitationCode: code.trim() },
              });
              guard.saved();
              setCode("");
              await refresh();
              action.setSuccess("Invitación canjeada. Tus permisos se han actualizado.");
            })
          }
        >
          Canjear invitación
        </Button>
        <Button secondary disabled={action.busy} onPress={cancel}>
          Cancelar
        </Button>
      </Panel>
    </>
  );
}
