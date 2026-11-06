import { useRouter, type Href } from "expo-router";
import type { ComponentProps, ReactNode } from "react";
import { ApiError } from "../services/http";
import type { Pagination } from "../types/api";
export function NavLink({
  href,
  children,
  className = "",
}: {
  href: string;
  children: ReactNode;
  className?: string;
}) {
  const router = useRouter();
  return (
    <a
      href={href}
      className={className}
      onClick={(event) => {
        if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        router.push(href as Href);
      }}
    >
      {children}
    </a>
  );
}
export function Panel({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="panel">
      {title && <h2>{title}</h2>}
      {children}
    </section>
  );
}
export function Button({
  children,
  secondary,
  ...props
}: ComponentProps<"button"> & { secondary?: boolean }) {
  return (
    <button type="button" className={secondary ? "button secondary" : "button"} {...props}>
      {children}
    </button>
  );
}
export function Field({ label, ...props }: ComponentProps<"input"> & { label: string }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input {...props} />
    </label>
  );
}
export function Textarea({ label, ...props }: ComponentProps<"textarea"> & { label: string }) {
  return (
    <label className="field">
      <span>{label}</span>
      <textarea rows={5} {...props} />
    </label>
  );
}
export function Select({ label, children, ...props }: ComponentProps<"select"> & { label: string }) {
  return (
    <label className="field">
      <span>{label}</span>
      <select {...props}>{children}</select>
    </label>
  );
}
export function Feedback({ error, success }: { error?: unknown; success?: string }) {
  return (
    <>
      {error != null && (
        <div role="alert" className="feedback error">
          {error instanceof Error ? error.message : "No se pudo completar la operación."}
          {error instanceof ApiError && (
            <small>
              {error.status ? `HTTP ${error.status} · ` : ""}
              {error.code}
              {error.retryAfter ? ` · Reintentar después de: ${error.retryAfter}` : ""}
            </small>
          )}
        </div>
      )}
      {success && (
        <div role="status" className="feedback success">
          {success}
        </div>
      )}
    </>
  );
}
export function QueryState({
  loading,
  error,
  empty,
  reload,
}: {
  loading: boolean;
  error: unknown;
  empty?: boolean;
  reload: () => void;
}) {
  return (
    <>
      {loading && (
        <p role="status" className="muted">
          Cargando…
        </p>
      )}
      <Feedback error={error} />
      {error != null && (
        <Button secondary onClick={reload}>
          Reintentar consulta
        </Button>
      )}
      {!loading && error == null && empty && <p className="empty">No hay resultados.</p>}
    </>
  );
}
export function Pager({
  pagination,
  onPage,
  disabled,
}: {
  pagination: Pagination;
  onPage: (page: number) => void;
  disabled?: boolean;
}) {
  return (
    <div className="pager">
      <span>
        {pagination.totalItems} resultados · Página {pagination.page} de {Math.max(1, pagination.totalPages)}
      </span>
      <Button
        secondary
        disabled={disabled || pagination.page <= 1}
        onClick={() => onPage(pagination.page - 1)}
      >
        Anterior
      </Button>
      <Button
        secondary
        disabled={disabled || pagination.page >= pagination.totalPages}
        onClick={() => onPage(pagination.page + 1)}
      >
        Siguiente
      </Button>
    </div>
  );
}
export function Badge({ children }: { children: ReactNode }) {
  return <span className="badge">{children}</span>;
}
export const date = (value?: string | null) => (value ? new Date(value).toLocaleString("es-ES") : "—");
export const roleNames: Record<string, string> = {
  CITIZEN: "Ciudadano",
  ADMIN: "Administrador",
  OPERATOR: "Operador",
  TECHNICIAN: "Técnico",
  DEPARTMENT_COORDINATOR: "Coordinador departamental",
};
export const statusNames: Record<string, string> = {
  REPORTED: "Reportada",
  VALIDATED: "Validada",
  REJECTED: "Rechazada",
  ASSIGNED_TO_DEPARTMENT: "Asignada a departamento",
  WORK_ASSIGNED: "Trabajo asignado",
  IN_PROGRESS: "En curso",
  RESOLVED: "Resuelta",
  CLOSED: "Cerrada",
  ACTIVE: "Activa",
  REDEEMED: "Canjeada",
  REVOKED: "Revocada",
  EXPIRED: "Caducada",
  CREATED: "Creación",
  UPDATED: "Corrección",
};
export function Details({ entries }: { entries: [string, ReactNode][] }) {
  return (
    <dl>
      {entries.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value ?? "—"}</dd>
        </div>
      ))}
    </dl>
  );
}
