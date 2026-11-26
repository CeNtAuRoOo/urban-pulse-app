import { type Href } from "expo-router";
import { AppLink as Link } from "./AppLink";
import { useId, useState, type ReactNode } from "react";
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  useWindowDimensions,
  type TextInputProps,
  type StyleProp,
  type ViewStyle,
} from "react-native";
import Feather from "@expo/vector-icons/Feather";
import { colors as c, displayFont } from "../design/tokens";
import type { Pagination } from "../types/api";
import { ApiError } from "../services/http";
export const styles = StyleSheet.create({
  stack: { gap: 18 },
  row: {
    flexDirection: "row",
    alignItems: "center",
    flexWrap: "wrap",
    gap: 12,
  },
  panel: {
    backgroundColor: c.surface,
    borderWidth: 1,
    borderColor: c.border,
    borderRadius: 20,
    padding: 24,
    gap: 18,
  },
  text: { color: c.ink, fontSize: 15, lineHeight: 23 },
  muted: { color: c.muted, fontSize: 14, lineHeight: 22 },
  h1: {
    color: c.ink,
    fontSize: 38,
    lineHeight: 44,
    fontFamily: displayFont,
    fontWeight: "700",
    letterSpacing: -0.7,
  },
  h2: { color: c.ink, fontSize: 21, lineHeight: 29, fontWeight: "700" },
  input: {
    backgroundColor: c.surface,
    color: c.ink,
    borderWidth: 1,
    borderColor: "#A9BCC5",
    borderRadius: 12,
    paddingHorizontal: 13,
    paddingVertical: 12,
    minHeight: 46,
    fontSize: 15,
  },
  button: {
    backgroundColor: c.primary,
    borderWidth: 1,
    borderColor: c.primary,
    minHeight: 46,
    borderRadius: 12,
    paddingHorizontal: 17,
    paddingVertical: 11,
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    justifyContent: "center",
  },
  divider: {
    borderBottomWidth: 1,
    borderBottomColor: c.border,
    paddingVertical: 18,
    gap: 10,
  },
});
export function Body({ children, muted = false }: { children: ReactNode; muted?: boolean }) {
  return (
    <Text selectable style={muted ? styles.muted : styles.text}>
      {children}
    </Text>
  );
}
export function Heading({ children, level = 2 }: { children: ReactNode; level?: 1 | 2 | 3 }) {
  return (
    <Text role="heading" aria-level={level} style={level === 1 ? styles.h1 : styles.h2}>
      {children}
    </Text>
  );
}
export function Stack({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return <View style={[styles.stack, style]}>{children}</View>;
}
export function Row({ children }: { children: ReactNode }) {
  return <View style={styles.row}>{children}</View>;
}
export function Panel({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <View style={styles.panel}>
      {title ? <Heading>{title}</Heading> : null}
      {children}
    </View>
  );
}
export function PageHeading({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children?: ReactNode;
}) {
  return (
    <View style={[styles.row, { justifyContent: "space-between", marginBottom: 12 }]}>
      <View style={{ flexGrow: 1, flexBasis: 320, gap: 8 }}>
        <Heading level={1}>{title}</Heading>
        {description ? <Body muted>{description}</Body> : null}
      </View>
      {children}
    </View>
  );
}
export function Button({
  children,
  onPress,
  disabled = false,
  secondary = false,
  danger = false,
  icon,
}: {
  children: ReactNode;
  onPress: () => void;
  disabled?: boolean;
  secondary?: boolean;
  danger?: boolean;
  icon?: React.ComponentProps<typeof Feather>["name"];
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={typeof children === "string" ? children : undefined}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
        styles.button,
        secondary && { backgroundColor: c.surface, borderColor: c.border },
        danger && { backgroundColor: c.danger, borderColor: c.danger },
        (pressed || hovered) && { opacity: 0.8 },
        disabled && { opacity: 0.5 },
      ]}
    >
      {icon ? <Feather name={icon} size={18} color={secondary ? c.ink : "#fff"} /> : null}
      <Text
        style={{
          fontSize: 14,
          fontWeight: "600",
          color: secondary ? c.ink : "#fff",
        }}
      >
        {children}
      </Text>
    </Pressable>
  );
}
export function NavLink({
  href,
  children,
  button = false,
}: {
  href: string;
  children: ReactNode;
  button?: boolean;
}) {
  return (
    <Link
      href={href as Href}
      style={
        button
          ? [
              styles.button,
              {
                color: "#fff",
                fontSize: 14,
                fontWeight: "600",
                textAlign: "center",
              },
            ]
          : {
              color: c.primaryDark,
              fontSize: 15,
              fontWeight: "600",
              paddingVertical: 10,
            }
      }
    >
      {children}
    </Link>
  );
}
export function Field({
  label,
  hint,
  error,
  multiline,
  ...props
}: TextInputProps & { label: string; hint?: string; error?: string }) {
  const id = useId();
  return (
    <View style={{ gap: 7, minWidth: 0 }}>
      <Text nativeID={`${id}-label`} style={[styles.text, { fontWeight: "600" }]}>
        {label}
      </Text>
      <TextInput
        accessibilityLabel={label}
        aria-labelledby={`${id}-label`}
        aria-describedby={hint || error ? `${id}-hint` : undefined}
        aria-invalid={!!error}
        placeholderTextColor={c.muted}
        autoCapitalize="none"
        {...props}
        multiline={multiline}
        style={[styles.input, multiline && { minHeight: 120, textAlignVertical: "top" }, props.style]}
      />
      {hint || error ? (
        <Text nativeID={`${id}-hint`} style={[styles.muted, error ? { color: c.danger } : {}]}>
          {error || hint}
        </Text>
      ) : null}
    </View>
  );
}
export function Select({
  label,
  value,
  onChange,
  options,
  disabled = false,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  return (
    <View style={{ gap: 7 }}>
      <Text style={[styles.text, { fontWeight: "600" }]}>{label}</Text>
      <Pressable
        disabled={disabled}
        accessibilityRole="button"
        accessibilityLabel={`${label}: ${options.find((x) => x.value === value)?.label || "Seleccionar"}`}
        accessibilityState={{ expanded: open, disabled }}
        onPress={() => setOpen(true)}
        style={[styles.input, styles.row, { justifyContent: "space-between", opacity: disabled ? 0.5 : 1 }]}
      >
        <Text style={[styles.text, { flex: 1 }]}>
          {options.find((x) => x.value === value)?.label || "Seleccionar"}
        </Text>
        <Feather name="chevron-down" size={18} color={c.muted} />
      </Pressable>
      <Modal visible={open} transparent animationType="none" onRequestClose={() => setOpen(false)}>
        <View
          style={{
            flex: 1,
            backgroundColor: "#102C3A88",
            justifyContent: "center",
            alignItems: "center",
            padding: 20,
          }}
        >
          <View
            accessibilityViewIsModal
            style={[styles.panel, { width: "100%", maxWidth: 520, maxHeight: "80%" }]}
          >
            <Heading>{label}</Heading>
            <ScrollView>
              {options.map((o) => (
                <Pressable
                  accessibilityRole="radio"
                  accessibilityState={{ checked: o.value === value }}
                  key={o.value}
                  onPress={() => {
                    onChange(o.value);
                    setOpen(false);
                  }}
                  style={[
                    styles.divider,
                    {
                      paddingHorizontal: 12,
                      backgroundColor: o.value === value ? c.tint : c.surface,
                    },
                  ]}
                >
                  <Text style={styles.text}>{o.label}</Text>
                </Pressable>
              ))}
            </ScrollView>
            <Button secondary onPress={() => setOpen(false)}>
              Cerrar
            </Button>
          </View>
        </View>
      </Modal>
    </View>
  );
}
export function Confirm({
  title,
  message,
  onConfirm,
  children,
  danger = false,
  disabled = false,
}: {
  title: string;
  message: string;
  onConfirm: () => void;
  children: ReactNode;
  danger?: boolean;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button secondary={!danger} danger={danger} disabled={disabled} onPress={() => setOpen(true)}>
        {children}
      </Button>
      <ConfirmationDialog
        visible={open}
        title={title}
        message={message}
        danger={danger}
        onCancel={() => setOpen(false)}
        onConfirm={() => {
          setOpen(false);
          onConfirm();
        }}
      />
    </>
  );
}
export function ConfirmationDialog({
  visible,
  title,
  message,
  onConfirm,
  onCancel,
  danger = false,
  confirmLabel = "Confirmar",
  cancelLabel = "Cancelar",
}: {
  visible: boolean;
  title: string;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
  danger?: boolean;
  confirmLabel?: string;
  cancelLabel?: string;
}) {
  return (
    <Modal visible={visible} transparent onRequestClose={() => onCancel()}>
      <View
        style={{
          flex: 1,
          backgroundColor: "#102C3A88",
          justifyContent: "center",
          alignItems: "center",
          padding: 20,
        }}
      >
        <View
          role="dialog"
          accessibilityLabel={title}
          accessibilityViewIsModal
          style={[styles.panel, { width: "100%", maxWidth: 500 }]}
        >
          <Heading>{title}</Heading>
          <Body>{message}</Body>
          <Row>
            <Button
              danger={danger}
              onPress={() => {
                onConfirm();
              }}
            >
              {confirmLabel}
            </Button>
            <Button secondary onPress={() => onCancel()}>
              {cancelLabel}
            </Button>
          </Row>
        </View>
      </View>
    </Modal>
  );
}
export function Feedback({ error, success }: { error?: unknown; success?: string }) {
  return (
    <>
      {error != null ? (
        <View
          role="alert"
          style={{
            padding: 16,
            borderRadius: 12,
            backgroundColor: c.dangerTint,
            gap: 4,
          }}
        >
          <Text style={[styles.text, { color: c.danger }]}>
            {error instanceof Error ? error.message : "No se pudo completar la operación."}
          </Text>
          {error instanceof ApiError && error.retryAfter ? (
            <Body>Espera {error.retryAfter} segundos antes de reintentar.</Body>
          ) : null}
        </View>
      ) : null}
      {success ? (
        <View
          role="status"
          accessibilityLiveRegion="polite"
          style={{ padding: 16, borderRadius: 12, backgroundColor: c.tint }}
        >
          <Text style={styles.text}>{success}</Text>
        </View>
      ) : null}
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
  error?: unknown;
  empty?: boolean;
  reload: () => void;
}) {
  return (
    <>
      {loading ? (
        <Row>
          <ActivityIndicator color={c.primary} />
          <Text role="status" style={styles.muted}>
            Cargando…
          </Text>
        </Row>
      ) : null}
      <Feedback error={error} />
      {error != null ? (
        <Button secondary onPress={reload}>
          Reintentar
        </Button>
      ) : null}
      {!loading && !error && empty ? (
        <View style={{ paddingVertical: 32, gap: 8 }}>
          <Heading>No hay resultados</Heading>
          <Body muted>Prueba con otros filtros o vuelve más tarde.</Body>
        </View>
      ) : null}
    </>
  );
}
export function Pager({ pagination, onPage }: { pagination: Pagination; onPage: (v: number) => void }) {
  return (
    <View style={[styles.row, { justifyContent: "space-between", paddingTop: 16 }]}>
      <Body muted>
        {pagination.totalItems.toLocaleString("es-ES")} resultados · {pagination.page} /{" "}
        {Math.max(1, pagination.totalPages)}
      </Body>
      <Row>
        <Button secondary disabled={pagination.page <= 1} onPress={() => onPage(pagination.page - 1)}>
          Anterior
        </Button>
        <Button
          secondary
          disabled={pagination.page >= pagination.totalPages}
          onPress={() => onPage(pagination.page + 1)}
        >
          Siguiente
        </Button>
      </Row>
    </View>
  );
}
export function Badge({
  children,
  tone = "normal",
}: {
  children: ReactNode;
  tone?: "normal" | "danger" | "warning";
}) {
  return (
    <View
      style={{
        alignSelf: "flex-start",
        paddingVertical: 5,
        paddingHorizontal: 10,
        borderRadius: 6,
        backgroundColor: tone === "danger" ? c.dangerTint : tone === "warning" ? c.warningTint : c.tint,
      }}
    >
      <Text
        style={{
          fontSize: 12,
          fontWeight: "700",
          color: tone === "danger" ? c.danger : tone === "warning" ? c.warning : c.primaryDark,
        }}
      >
        {children}
      </Text>
    </View>
  );
}
export function Details({ entries }: { entries: [string, ReactNode][] }) {
  return (
    <View style={{ gap: 14 }}>
      {entries.map(([label, value]) => (
        <View key={label} style={{ gap: 3 }}>
          <Text style={styles.muted}>{label}</Text>
          {typeof value === "object" && value !== null ? value : <Body>{value ?? "—"}</Body>}
        </View>
      ))}
    </View>
  );
}
export function DataList<T extends { id: string | number }>({
  items,
  columns,
  expanded,
}: {
  expanded?: (item: T) => ReactNode;
  items: T[];
  columns: { label: string; render: (item: T) => ReactNode; grow?: number }[];
}) {
  const wide = useWindowDimensions().width >= 1100;
  return (
    <View>
      {wide ? (
        <View style={[styles.row, { backgroundColor: c.canvas, padding: 14, borderRadius: 8 }]}>
          {columns.map((col) => (
            <Text key={col.label} style={[styles.text, { fontWeight: "600", flex: col.grow || 1 }]}>
              {col.label}
            </Text>
          ))}
        </View>
      ) : null}
      {items.map((item) => (
        <View key={item.id}>
          <View
            key={item.id}
            style={[
              styles.divider,
              wide
                ? {
                    flexDirection: "row",
                    alignItems: "center",
                    paddingHorizontal: 14,
                    gap: 12,
                  }
                : {},
            ]}
          >
            {columns.map((col) => (
              <View key={col.label} style={wide ? { flex: col.grow || 1, minWidth: 0 } : { gap: 2 }}>
                {!wide ? <Text style={[styles.muted, { fontSize: 12 }]}>{col.label}</Text> : null}
                {col.render(item)}
              </View>
            ))}
          </View>
          {expanded?.(item)}
        </View>
      ))}
    </View>
  );
}
export const date = (value?: string | null) =>
  value
    ? new Intl.DateTimeFormat("es-ES", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
export const roleNames: Record<string, string> = {
  CITIZEN: "Ciudadano",
  ADMIN: "Administrador",
  OPERATOR: "Operador",
  TECHNICIAN: "Técnico",
  DEPARTMENT_COORDINATOR: "Coordinador departamental",
};
export const statusNames: Record<string, string> = {
  REPORTED: "Reportada",
  AWAITING_INFORMATION: "Información pendiente",
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
};
export const priorities: Record<string, string> = {
  LOW: "Baja",
  MEDIUM: "Media",
  HIGH: "Alta",
  CRITICAL: "Crítica",
};
export const options = (values: Record<string, string>, all?: string) => [
  ...(all ? [{ value: "", label: all }] : []),
  ...Object.entries(values).map(([value, label]) => ({ value, label })),
];
