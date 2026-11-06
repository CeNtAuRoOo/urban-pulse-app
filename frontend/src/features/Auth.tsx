import { useState } from "react";
import { Button, Feedback, Field, NavLink, Panel } from "../components/ui";
import { useAction } from "../services/hooks";
import { request } from "../services/http";
import { useSession } from "../services/session";
import { displayName, password } from "../services/validation";
import type { TokenDTO } from "../types/api";
export function Auth({ register = false }: { register?: boolean }) {
  const { accept, notice } = useSession();
  const action = useAction();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [secret, setSecret] = useState("");
  return (
    <Panel title={register ? "Crear una cuenta ciudadana" : "Iniciar sesión"}>
      <p className="muted">Accede con tu cuenta de UrbanPulse para realizar operaciones reales.</p>
      <Feedback success={notice} error={action.error} />
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void action.run(async () => {
            const body = register
              ? { displayName: displayName(name), email: email.trim(), password: password(secret) }
              : { email: email.trim(), password: secret };
            try {
              const response = await request<TokenDTO>(`/auth/${register ? "register" : "login"}`, {
                method: "POST",
                body,
              });
              accept(response.data);
            } finally {
              setSecret("");
            }
          });
        }}
      >
        <fieldset disabled={action.busy}>
          {register && (
            <Field
              label="Nombre público"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              autoComplete="name"
            />
          )}
          <Field
            label="Correo electrónico"
            type="email"
            maxLength={254}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
            required
          />
          <Field
            label="Contraseña"
            type="password"
            value={secret}
            onChange={(e) => setSecret(e.target.value)}
            autoComplete={register ? "new-password" : "current-password"}
            required
          />
          <Button type="submit">{action.busy ? "Procesando…" : register ? "Registrarse" : "Entrar"}</Button>
        </fieldset>
      </form>
      <p>
        <NavLink href={register ? "/login" : "/register"}>
          {register ? "Ya tengo una cuenta" : "Registrar una cuenta ciudadana"}
        </NavLink>
      </p>
    </Panel>
  );
}
export function PasswordForm() {
  const { api, logout } = useSession();
  const action = useAction();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  return (
    <Panel title="Cambiar contraseña">
      <p className="muted">
        El cambio invalida las sesiones anteriores. Tendrás que iniciar sesión otra vez.
      </p>
      <Feedback error={action.error} />
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            password(next);
            try {
              const response = await api("/users/me/password-changes", {
                method: "POST",
                body: { currentPassword: current, newPassword: next },
              });
              logout(`Contraseña cambiada (HTTP ${response.status}). Inicia sesión de nuevo.`);
            } finally {
              setCurrent("");
              setNext("");
            }
          });
        }}
      >
        <fieldset disabled={action.busy}>
          <Field
            label="Contraseña actual"
            type="password"
            required
            value={current}
            autoComplete="current-password"
            onChange={(e) => setCurrent(e.target.value)}
          />
          <Field
            label="Nueva contraseña"
            type="password"
            required
            value={next}
            autoComplete="new-password"
            onChange={(e) => setNext(e.target.value)}
          />
          <Button type="submit">Cambiar contraseña</Button>
        </fieldset>
      </form>
    </Panel>
  );
}
export function Verification() {
  const { api, refresh, session } = useSession();
  const action = useAction();
  const [code, setCode] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  return (
    <>
      <Panel title="Verifica tu correo">
        <p>
          Introduce el código enviado a <strong>{session?.user.email}</strong>. Hasta verificarlo, el panel no
          está disponible.
        </p>
        <Feedback error={action.error} success={action.success} />
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void action.run(async () => {
              try {
                const result = await api("/auth/email-verifications/confirm", {
                  method: "POST",
                  body: { code },
                });
                setConfirmed(true);
                action.setSuccess(`Correo confirmado (HTTP ${result.status}).`);
                await refresh();
              } finally {
                setCode("");
              }
            });
          }}
        >
          <fieldset disabled={action.busy || confirmed}>
            <Field
              label="Código de ocho dígitos"
              type="text"
              inputMode="numeric"
              pattern="[0-9]{8}"
              maxLength={8}
              autoComplete="one-time-code"
              required
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
            <Button type="submit">Confirmar correo</Button>
            <Button
              secondary
              onClick={() =>
                void action.run(async () => {
                  const result = await api("/auth/email-verifications/resend", { method: "POST" });
                  action.setSuccess(
                    result.status === 204
                      ? "El correo ya está verificado (HTTP 204)."
                      : `Envío solicitado (HTTP ${result.status}). Consulta el correo o Mailpit.`
                  );
                  if (result.status === 204) {
                    setConfirmed(true);
                    await refresh();
                  }
                })
              }
            >
              Reenviar código
            </Button>
          </fieldset>
        </form>
        {confirmed && (
          <Button disabled={action.busy} onClick={() => void action.run(refresh)}>
            Cargar perfil verificado
          </Button>
        )}
      </Panel>
      <PasswordForm />
    </>
  );
}
