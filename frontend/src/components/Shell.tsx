import { Redirect, Slot, usePathname } from "expo-router";
import { useEffect, useState } from "react";
import { useSession } from "../services/session";
import { Badge, Button, Feedback, NavLink, roleNames } from "./ui";
export function Shell() {
  const { session, ready, logout, refresh } = useSession();
  const path = usePathname();
  const [profileCheck, setProfileCheck] = useState<{ identity: string; error?: unknown }>();
  const identity = session?.user.id;
  const verified = session?.user.emailVerified;
  const checked = profileCheck?.identity === identity && !profileCheck?.error;
  const profileError = profileCheck?.identity === identity ? profileCheck?.error : undefined;
  useEffect(() => {
    let live = true;
    if (identity && verified)
      refresh()
        .then(() => {
          if (live) setProfileCheck({ identity });
        })
        .catch((error) => {
          if (live) setProfileCheck({ identity, error });
        });
    return () => {
      live = false;
    };
  }, [identity, verified, refresh]);
  if (!ready)
    return (
      <main className="auth">
        <p>Cargando sesión…</p>
      </main>
    );
  if (!session)
    return path === "/login" || path === "/register" ? (
      <main className="auth">
        <div className="brand">
          UrbanPulse <span>· Demo</span>
        </div>
        <Slot />
      </main>
    ) : (
      <Redirect href="/login" />
    );
  const user = session.user;
  const admin = user.roles.includes("ADMIN");
  const staff = admin || user.roles.includes("OPERATOR");
  const header = (
    <header>
      <NavLink href="/" className="brand">
        UrbanPulse <span>· Demo</span>
      </NavLink>
      <div className="account">
        <strong>{user.displayName}</strong>
        <small>{user.email}</small>
      </div>
      <div className="roles">
        {user.roles.map((role) => (
          <Badge key={role}>{roleNames[role]}</Badge>
        ))}
      </div>
      <Button secondary onClick={() => logout()}>
        Cerrar sesión
      </Button>
    </header>
  );
  if (!user.emailVerified)
    return path === "/verify" ? (
      <>
        {header}
        <main className="verification">
          <Slot />
        </main>
      </>
    ) : (
      <Redirect href="/verify" />
    );
  if (!checked)
    return (
      <>
        {header}
        <main className="verification">
          <Feedback error={profileError} />
          {profileError ? (
            <Button
              onClick={() => {
                setProfileCheck(undefined);
                void refresh()
                  .then(() => setProfileCheck({ identity: user.id }))
                  .catch((error) => setProfileCheck({ identity: user.id, error }));
              }}
            >
              Reintentar cargar perfil
            </Button>
          ) : (
            <p>Cargando perfil y permisos…</p>
          )}
        </main>
      </>
    );
  if (["/login", "/register", "/verify"].includes(path)) return <Redirect href="/" />;
  if (
    ((path === "/users" || path === "/staff-invitations") && !admin) ||
    (path === "/incidents/all" && !staff) ||
    (path === "/redeem" && !user.roles.includes("CITIZEN"))
  )
    return <Redirect href="/" />;
  const items = [
    ["/", "Inicio"],
    ["/incidents", "Mis incidencias"],
    ["/incidents/new", "Crear incidencia"],
    ...(staff ? [["/incidents/all", "Todas las incidencias"]] : []),
    ["/categories", "Categorías"],
    ["/departments", "Departamentos"],
    ...(admin
      ? [
          ["/staff-invitations", "Invitaciones"],
          ["/users", "Usuarios"],
        ]
      : []),
    ...(user.roles.includes("CITIZEN") ? [["/redeem", "Canjear invitación"]] : []),
    ["/profile", "Mi perfil"],
  ];
  return (
    <>
      {header}
      <div className="workspace">
        <aside>
          <div className="nav-label">PANEL DE DEMOSTRACIÓN</div>
          <nav>
            {items.map(([href, label]) => (
              <NavLink key={href} href={href} className={path === href ? "selected" : ""}>
                {label}
              </NavLink>
            ))}
          </nav>
          <p className="sidebar-note">Operaciones conectadas a la API de UrbanPulse.</p>
        </aside>
        <main className="content" key={user.id}>
          <Slot />
        </main>
      </div>
    </>
  );
}
