import { useState } from "react";
import {
  Badge,
  Button,
  Details,
  Feedback,
  Field,
  Panel,
  QueryState,
  Select,
  Textarea,
} from "../components/ui";
import { useAction, useResource } from "../services/hooks";
import { useSession } from "../services/session";
import { nonEmptyPatch, textLength } from "../services/validation";
import type { Category, Department } from "../types/api";
type CatalogItem = Category & { code?: string };
export function Catalog({ department = false }: { department?: boolean }) {
  const path = department ? "/departments" : "/categories";
  const { session } = useSession();
  const admin = !!session?.user.roles.includes("ADMIN");
  const resource = useResource<CatalogItem[]>(path);
  const [selected, setSelected] = useState("");
  const [creating, setCreating] = useState(false);
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState("");
  const changed = (item: CatalogItem, status: number) => {
    setNotice(`Guardado: ${item.name} · HTTP ${status}`);
    setCreating(false);
    setSelected(item.id);
    setRevision((value) => value + 1);
    resource.reload();
  };
  return (
    <>
      <div className="toolbar">
        <h1>{department ? "Departamentos" : "Categorías"}</h1>
        {admin && (
          <Button
            onClick={() => {
              setCreating(true);
              setSelected("");
              setNotice("");
            }}
          >
            Crear {department ? "departamento" : "categoría"}
          </Button>
        )}
        <Button secondary disabled={resource.loading} onClick={resource.reload}>
          Actualizar
        </Button>
      </div>
      <Feedback success={notice} />
      {creating && admin && (
        <Panel title="Alta de catálogo">
          <CatalogForm department={department} onSaved={changed} onCancel={() => setCreating(false)} />
        </Panel>
      )}
      <Panel>
        <QueryState {...resource} empty={resource.result?.data.length === 0} />
        {resource.result && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Nombre</th>
                  {department && <th>Código</th>}
                  <th>Estado</th>
                  <th>Consulta</th>
                </tr>
              </thead>
              <tbody>
                {resource.result.data.map((item) => (
                  <tr key={item.id}>
                    <td>{item.name}</td>
                    {department && <td>{item.code}</td>}
                    <td>
                      <Badge>{item.active ? "Activo" : "Inactivo"}</Badge>
                    </td>
                    <td>
                      <Button
                        secondary
                        onClick={() => {
                          setSelected(item.id);
                          setCreating(false);
                        }}
                      >
                        Ver detalle
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
      {selected && (
        <CatalogDetail
          key={`${selected}-${revision}`}
          id={selected}
          department={department}
          admin={admin}
          onSaved={changed}
        />
      )}
    </>
  );
}
function CatalogDetail({
  id,
  department,
  admin,
  onSaved,
}: {
  id: string;
  department: boolean;
  admin: boolean;
  onSaved: (item: CatalogItem, status: number) => void;
}) {
  const resource = useResource<CatalogItem>(`${department ? "/departments" : "/categories"}/${id}`);
  const [editing, setEditing] = useState(false);
  return (
    <Panel title="Detalle">
      <QueryState {...resource} />
      {resource.result && (
        <>
          <Details
            entries={[
              ["Identificador", resource.result.data.id],
              ["Nombre", resource.result.data.name],
              ...(department ? [["Código", resource.result.data.code] as [string, string | undefined]] : []),
              ["Descripción", resource.result.data.description],
              ["Estado", resource.result.data.active ? "Activo" : "Inactivo"],
            ]}
          />
          {admin &&
            (editing ? (
              <CatalogForm
                department={department}
                original={resource.result.data}
                onSaved={onSaved}
                onCancel={() => setEditing(false)}
              />
            ) : (
              <p>
                <Button onClick={() => setEditing(true)}>Editar</Button>
              </p>
            ))}
        </>
      )}
    </Panel>
  );
}
function CatalogForm({
  original,
  department,
  onSaved,
  onCancel,
}: {
  original?: CatalogItem;
  department: boolean;
  onSaved: (item: CatalogItem, status: number) => void;
  onCancel: () => void;
}) {
  const { api } = useSession();
  const action = useAction();
  const [name, setName] = useState(original?.name || "");
  const [code, setCode] = useState(original?.code || "");
  const [description, setDescription] = useState(original?.description || "");
  const [active, setActive] = useState(original?.active ?? true);
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        void action.run(async () => {
          const values: Partial<Department> = {
            name: textLength(name, 2, department ? 120 : 100, "Nombre"),
            description: textLength(description, 0, 500, "Descripción", false),
            active,
          };
          if (department) {
            if (!/^[A-Za-z0-9_-]{2,20}$/.test(code.trim()))
              throw new Error("Código: entre 2 y 20 letras ASCII, números, guion o guion bajo.");
            values.code = code.trim();
          }
          const body = original
            ? Object.fromEntries(
                Object.entries(values).filter(
                  ([key, value]) =>
                    value !== (original[key as keyof CatalogItem] ?? (key === "description" ? "" : undefined))
                )
              )
            : values;
          nonEmptyPatch(body);
          const path = department ? "/departments" : "/categories";
          const response = await api<CatalogItem>(original ? `${path}/${original.id}` : path, {
            method: original ? "PATCH" : "POST",
            body,
          });
          onSaved(response.data, response.status);
        });
      }}
    >
      <Feedback error={action.error} />
      <fieldset disabled={action.busy}>
        <div className="grid">
          <Field label="Nombre" required value={name} onChange={(e) => setName(e.target.value)} />
          {department && (
            <Field
              label="Código (ASCII, 2–20 caracteres)"
              required
              pattern="[A-Za-z0-9_\-]{2,20}"
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
          )}
        </div>
        <Textarea
          label="Descripción (opcional, hasta 500 caracteres)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <Select label="Estado" value={String(active)} onChange={(e) => setActive(e.target.value === "true")}>
          <option value="true">Activo</option>
          <option value="false">Inactivo</option>
        </Select>
        <p className="hint">Desactivar conserva el registro y su consulta en el catálogo.</p>
        <Button type="submit">Guardar</Button>
        <Button secondary onClick={onCancel}>
          Cancelar
        </Button>
      </fieldset>
    </form>
  );
}
