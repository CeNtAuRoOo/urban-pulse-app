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
  Panel,
  QueryState,
  Row,
  Select,
} from "../components/ui";
import { useAction, useResource } from "../services/hooks";
import { useSession } from "../services/session";
import { permissions } from "../services/permissions";
import { nonEmptyPatch, textLength } from "../services/validation";
import type { Category } from "../types/api";
type Item = Category & { code?: string };
export function Catalog({ department = false }: { department?: boolean }) {
  const navigation = useFormNavigation();
  const path = department ? "/departments" : "/categories";
  const resource = useResource<Item[]>(path),
    { session } = useSession();
  const admin = permissions(session?.user).admin;
  const [selected, setSelected] = useState(""),
    [revision, setRevision] = useState(0);
  return (
    <>
      <PageHeading
        title={department ? "Departamentos" : "Categorías"}
        description={
          department
            ? "Organización municipal y áreas de servicio."
            : "Una clasificación común para cada incidencia."
        }
      >
        {admin ? (
          <Button icon="plus" onPress={() => navigation.request(() => setSelected("new"))}>
            Crear {department ? "departamento" : "categoría"}
          </Button>
        ) : null}
      </PageHeading>
      <Panel>
        <QueryState {...resource} empty={resource.result?.data.length === 0} />
        {resource.result ? (
          <DataList
            items={resource.result.data}
            expanded={(i) =>
              selected === i.id ? (
                <CatalogDetail
                  key={`${selected}-${revision}`}
                  id={selected}
                  path={path}
                  department={department}
                  admin={admin}
                  cancel={() => setSelected("")}
                  changed={() => {
                    resource.reload();
                    setRevision((v) => v + 1);
                    setSelected("");
                  }}
                />
              ) : null
            }
            columns={[
              {
                label: "Nombre",
                grow: 2,
                render: (i) => (
                  <>
                    <Body>{i.name}</Body>
                    {i.code ? <Body muted>{i.code}</Body> : null}
                  </>
                ),
              },
              {
                label: "Estado",
                render: (i) => <Badge>{i.active ? "Activo" : "Inactivo"}</Badge>,
              },
              {
                label: "Acciones",
                render: (i) => (
                  <Button
                    secondary
                    onPress={() => navigation.request(() => setSelected(selected === i.id ? "" : i.id))}
                  >
                    {selected === i.id ? "Cerrar detalle" : "Ver detalle"}
                  </Button>
                ),
              },
            ]}
          />
        ) : null}
      </Panel>
      {selected === "new" ? (
        <CatalogDetail
          key={`${selected}-${revision}`}
          id={selected}
          path={path}
          department={department}
          admin={admin}
          cancel={() => setSelected("")}
          changed={() => {
            resource.reload();
            setRevision((v) => v + 1);
            setSelected("");
          }}
        />
      ) : null}
    </>
  );
}
function CatalogDetail({
  id,
  path,
  department,
  admin,
  cancel,
  changed,
}: {
  id: string;
  path: string;
  department: boolean;
  admin: boolean;
  cancel: () => void;
  changed: () => void;
}) {
  const resource = useResource<Item>(id === "new" ? null : `${path}/${id}`);
  if (id === "new")
    return (
      <Panel title="Nueva ficha">
        <CatalogForm department={department} path={path} cancel={cancel} changed={changed} />
      </Panel>
    );
  return (
    <Panel title="Detalle">
      <QueryState {...resource} />
      {resource.result ? (
        <>
          <Details
            entries={[
              ["Nombre", resource.result.data.name],
              ["Descripción", resource.result.data.description],
              ["Estado", resource.result.data.active ? "Activo" : "Inactivo"],
            ]}
          />
          {admin ? (
            <CatalogForm
              original={resource.result.data}
              department={department}
              path={path}
              cancel={cancel}
              changed={changed}
            />
          ) : (
            <Button secondary onPress={cancel}>
              Cerrar detalle
            </Button>
          )}
        </>
      ) : null}
    </Panel>
  );
}
function CatalogForm({
  original,
  department,
  path,
  cancel,
  changed,
}: {
  original?: Item;
  department: boolean;
  path: string;
  cancel: () => void;
  changed: () => void;
}) {
  const { api } = useSession(),
    action = useAction();
  const [name, setName] = useState(original?.name || ""),
    [code, setCode] = useState(original?.code || ""),
    [description, setDescription] = useState(original?.description || ""),
    [active, setActive] = useState(original?.active !== false);
  const guard = useUnsavedChanges(
    name !== (original?.name || "") ||
      code !== (original?.code || "") ||
      description !== (original?.description || "") ||
      active !== (original?.active !== false)
  );
  const save = () =>
    void action.run(async () => {
      const body: Record<string, unknown> = {
        name: textLength(name, 2, department ? 120 : 100, "Nombre"),
        description: textLength(description, 0, 500, "Descripción"),
        active,
      };
      if (department) {
        if (!/^[A-Za-z0-9_-]{2,20}$/.test(code.trim()))
          throw new Error("El código debe tener de 2 a 20 letras, números, guiones o guiones bajos.");
        body.code = code.trim().toUpperCase();
      }
      if (original) {
        for (const key of Object.keys(body)) if (body[key] === original[key as keyof Item]) delete body[key];
        nonEmptyPatch(body);
      }
      await api(original ? `${path}/${original.id}` : path, {
        method: original ? "PATCH" : "POST",
        body,
      });
      guard.saved();
      changed();
    });
  return (
    <>
      <Feedback error={action.error} />
      <Field label="Nombre" value={name} onChangeText={setName} />
      {department ? <Field label="Código" value={code} onChangeText={setCode} maxLength={20} /> : null}
      <Field label="Descripción" multiline value={description} onChangeText={setDescription} />
      <Select
        label="Estado"
        value={String(active)}
        onChange={(v) => setActive(v === "true")}
        options={[
          { value: "true", label: "Activo" },
          { value: "false", label: "Inactivo" },
        ]}
      />
      <Row>
        {original?.active && !active ? (
          <Confirm
            title="Desactivar ficha"
            message="Dejará de estar disponible para nuevas selecciones; se conservará en el historial."
            danger
            disabled={action.busy}
            onConfirm={save}
          >
            Guardar cambios
          </Confirm>
        ) : (
          <Button disabled={action.busy} onPress={save}>
            {action.busy ? "Guardando…" : "Guardar ficha"}
          </Button>
        )}
        <Button secondary disabled={action.busy} onPress={() => guard.discard(cancel)}>
          Cancelar
        </Button>
      </Row>
    </>
  );
}
