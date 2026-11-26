import { type Href, Redirect, Slot, useGlobalSearchParams, usePathname } from "expo-router";
import { useEffect, useState } from "react";
import { Pressable, ScrollView, Text, View, useWindowDimensions } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import Feather from "@expo/vector-icons/Feather";
import { useSession } from "../services/session";
import { permissions } from "../services/permissions";
import { routeKey } from "../services/navigation";
import { Body, Button, Feedback, Heading, NavLink, Row, styles } from "./ui";
import { AuthLayout, Brand } from "./AuthLayout";
import { ScreenScroll } from "./ScreenScroll";
import { colors as c } from "../design/tokens";
import { AppLink as Link } from "./AppLink";
import { useFormNavigation } from "../services/form-guard";
type NavItem = [string, string, React.ComponentProps<typeof Feather>["name"]];
export function Shell() {
  const guard = useFormNavigation();
  const { session, ready, logout, refresh } = useSession(),
    path = usePathname(),
    params = useGlobalSearchParams<Record<string, string>>(),
    wide = useWindowDimensions().width >= 1000;
  const [menuPath, setMenuPath] = useState<string | null>(null),
    [check, setCheck] = useState<{ id: string; error?: unknown }>();
  const menu = menuPath === path;
  const identity = session?.user.id,
    verified = session?.user.emailVerified;
  useEffect(() => {
    let live = true;
    if (identity && verified)
      void refresh()
        .then(() => {
          if (live) setCheck({ id: identity });
        })
        .catch((error) => {
          if (live) setCheck({ id: identity, error });
        });
    return () => {
      live = false;
    };
  }, [identity, verified, refresh]);
  if (!ready)
    return (
      <View style={{ padding: 32 }}>
        <Body>Cargando sesión…</Body>
      </View>
    );
  if (!session) return ["/login", "/register"].includes(path) ? <AuthLayout /> : <Redirect href="/login" />;
  const p = permissions(session.user);
  if (!verified && path !== "/verify") return <Redirect href="/verify" />;
  if (verified && ["/login", "/register", "/verify"].includes(path)) return <Redirect href="/" />;
  if (!verified && path === "/verify") return <AuthLayout />;
  const forbidden =
    (["/users", "/staff-invitations", "/categories", "/departments"].includes(path) && !p.admin) ||
    (path === "/incidents/all" && !p.staff);
  const main: NavItem[] = [
    ["/", "Inicio", "home"],
    ["/incidents", "Mis incidencias", "file-text"],
    ...(p.staff ? [["/incidents/all", "Gestión municipal", "briefcase"] as NavItem] : []),
    ["/map", "Mapa provincial", "map"],
    ...(p.staff ? [["/urban-assets", "Activos urbanos", "layers"] as NavItem] : []),
  ];
  const admin: NavItem[] = [
    ["/categories", "Categorías", "tag"],
    ["/departments", "Departamentos", "grid"],
    ["/users", "Usuarios", "users"],
    ["/staff-invitations", "Invitaciones", "mail"],
  ];
  const links = (items: NavItem[]) =>
    items.map(([href, label, icon]) => {
      const active = path === href;
      return (
        <Link key={href} href={href as Href} asChild>
          <Pressable
            onPress={() => setMenuPath(null)}
            accessibilityLabel={label}
            accessibilityState={{ selected: active }}
            style={{
              flexDirection: "row",
              alignItems: "center",
              gap: 12,
              paddingHorizontal: 16,
              minHeight: 48,
              borderRadius: 13,
              backgroundColor: active ? c.lime : "transparent",
            }}
          >
            <Feather name={icon} size={20} color={active ? c.ink : "#DCE9ED"} />
            <Text
              style={{ fontSize: 14, fontWeight: active ? "700" : "500", color: active ? c.ink : "#fff" }}
            >
              {label}
            </Text>
          </Pressable>
        </Link>
      );
    });
  const nav = (
    <View style={{ gap: 8 }}>
      {links(main)}
      {p.admin ? (
        <>
          <Text
            style={{
              marginTop: 20,
              marginBottom: 4,
              marginLeft: 16,
              color: "#AFC3CB",
              fontSize: 11,
              letterSpacing: 1.2,
              fontWeight: "600",
            }}
          >
            ADMINISTRACIÓN
          </Text>
          {links(admin)}
        </>
      ) : null}
      <View style={{ height: 1, backgroundColor: "#395266", marginVertical: 16 }} />
      {links([["/profile", "Mi cuenta", "user"]])}
    </View>
  );
  const screenKey = `${session.user.id}:${routeKey(path, params)}`;
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: c.canvas }}>
      <View style={{ flex: 1, flexDirection: wide ? "row" : "column" }}>
        {wide ? (
          <View style={{ width: 250, backgroundColor: c.ink, padding: 22, gap: 36 }}>
            <Brand light />
            <ScrollView>{nav}</ScrollView>
            <Text style={{ color: "#AFC3CB", fontSize: 12, lineHeight: 20 }}>
              Cuidamos lo que compartimos.
            </Text>
          </View>
        ) : (
          <View style={{ backgroundColor: c.ink, paddingHorizontal: 18, paddingVertical: 12, gap: 12 }}>
            <Row>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={menu ? "Cerrar navegación" : "Abrir navegación"}
                accessibilityState={{ expanded: menu }}
                onPress={() => setMenuPath(menu ? null : path)}
                style={{ padding: 10 }}
              >
                <Feather name={menu ? "x" : "menu"} size={24} color="#fff" />
              </Pressable>
              <Brand light />
            </Row>
            {menu ? <ScrollView style={{ maxHeight: 500 }}>{nav}</ScrollView> : null}
          </View>
        )}
        <View style={{ flex: 1, minWidth: 0 }}>
          <View
            style={{
              paddingHorizontal: wide ? 40 : 20,
              paddingVertical: 14,
              borderBottomWidth: 1,
              borderBottomColor: c.border,
              backgroundColor: c.surface,
            }}
          >
            <Row>
              <View style={{ flex: 1 }}>
                <Text style={[styles.text, { fontWeight: "600" }]}>{session.user.displayName}</Text>
                <Text style={[styles.muted, { fontSize: 12 }]}>
                  {p.staff ? "Espacio municipal" : "Espacio ciudadano"}
                </Text>
              </View>
              <Button secondary icon="log-out" onPress={() => guard.request(() => logout())}>
                Cerrar sesión
              </Button>
            </Row>
          </View>
          <ScreenScroll
            screenKey={screenKey}
            contentContainerStyle={{ padding: wide ? 40 : 20, paddingBottom: 64 }}
          >
            <View style={{ width: "100%", maxWidth: 1300, alignSelf: "center", gap: 24 }}>
              {verified && (check?.id !== identity || check?.error) ? (
                <>
                  <Feedback error={check?.error} />
                  {check?.error ? (
                    <Button
                      onPress={() =>
                        void refresh()
                          .then(() => setCheck({ id: identity! }))
                          .catch((error) => setCheck({ id: identity!, error }))
                      }
                    >
                      Reintentar cargar permisos
                    </Button>
                  ) : (
                    <Body>Cargando perfil y permisos…</Body>
                  )}
                </>
              ) : forbidden ? (
                <>
                  <Heading level={1}>Acceso no disponible</Heading>
                  <Body>Tu cuenta no dispone de permisos para esta sección.</Body>
                  <NavLink href="/">Volver al inicio</NavLink>
                </>
              ) : (
                <View style={{ gap: 24 }}>
                  <Slot />
                </View>
              )}
            </View>
          </ScreenScroll>
        </View>
      </View>
    </SafeAreaView>
  );
}
