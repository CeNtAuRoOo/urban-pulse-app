import { useUnsavedChanges } from "../services/form-guard";
import { Text, View, useWindowDimensions } from "react-native";
import { colors as c, displayFont } from "../design/tokens";
import { useState } from "react";
import { Body, Button, Feedback, Field, NavLink, Panel, Row, Stack } from "../components/ui";
import { useAction } from "../services/hooks";
import { request } from "../services/http";
import { useSession } from "../services/session";
import { displayName, password } from "../services/validation";
import type { TokenDTO } from "../types/api";
export function Auth({ register = false }: { register?: boolean }) {
  const wide = useWindowDimensions().width >= 900;
  const { accept, notice } = useSession(),
    action = useAction();
  const [name, setName] = useState(""),
    [email, setEmail] = useState(""),
    [secret, setSecret] = useState("");
  const guard = useUnsavedChanges(register && (!!name || !!email || !!secret));
  const submit = () =>
    void action.run(async () => {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) || email.length > 254)
        throw new Error("Introduce un correo electrónico válido.");
      const body = register
        ? {
            displayName: displayName(name),
            email: email.trim(),
            password: password(secret),
          }
        : { email: email.trim(), password: secret };
      const result = await request<TokenDTO>(`/auth/${register ? "register" : "login"}`, {
        method: "POST",
        body,
      });
      guard.saved();
      setSecret("");
      accept(result.data);
    });
  return (
    <View style={{ gap: 22 }}>
      <Text
        role="heading"
        aria-level={2}
        style={{
          fontFamily: displayFont,
          color: c.ink,
          fontSize: wide ? 46 : 34,
          lineHeight: wide ? 49 : 38,
          fontWeight: "700",
          letterSpacing: -1,
        }}
      >
        {register ? "Tu ciudad te necesita." : "Qué bueno verte."}
      </Text>
      <Body muted>
        {register ? "Comunica incidencias y sigue su evolución." : "Accede a tu espacio en UrbanPulse."}
      </Body>
      <Feedback success={notice} error={action.error} />
      {register ? (
        <Field
          label="Nombre público"
          value={name}
          onChangeText={setName}
          autoComplete="name"
          editable={!action.busy}
        />
      ) : null}
      <Field
        label="Correo electrónico"
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        autoComplete="email"
        autoCorrect={false}
        editable={!action.busy}
      />
      <Field
        label="Contraseña"
        value={secret}
        onChangeText={setSecret}
        secureTextEntry
        autoComplete={register ? "new-password" : "current-password"}
        onSubmitEditing={submit}
        editable={!action.busy}
        hint={register ? "Al menos 8 caracteres y hasta 72 bytes. Utiliza una contraseña única." : undefined}
      />
      <Button disabled={action.busy} onPress={submit}>
        {action.busy ? "Procesando…" : register ? "Crear cuenta" : "Entrar"}
      </Button>
      <NavLink href={register ? "/login" : "/register"}>
        {register ? "Ya tengo una cuenta" : "Crear una cuenta ciudadana"}
      </NavLink>
    </View>
  );
}
export function PasswordForm() {
  const { api, logout } = useSession(),
    action = useAction();
  const [current, setCurrent] = useState(""),
    [next, setNext] = useState("");
  const guard = useUnsavedChanges(!!current || !!next);
  return (
    <Panel title="Cambiar contraseña">
      <Body muted>El cambio cerrará tus sesiones. Tendrás que acceder de nuevo.</Body>
      <Feedback error={action.error} />
      <Field
        label="Contraseña actual"
        secureTextEntry
        value={current}
        onChangeText={setCurrent}
        autoComplete="current-password"
      />
      <Field
        label="Nueva contraseña"
        secureTextEntry
        value={next}
        onChangeText={setNext}
        autoComplete="new-password"
      />
      <Button
        disabled={action.busy}
        onPress={() =>
          void action.run(async () => {
            password(next);
            await api("/users/me/password-changes", {
              method: "POST",
              body: { currentPassword: current, newPassword: next },
            });
            guard.saved();
            setCurrent("");
            setNext("");
            logout("Contraseña actualizada. Inicia sesión de nuevo.");
          })
        }
      >
        {action.busy ? "Guardando…" : "Cambiar contraseña"}
      </Button>
      <Button
        secondary
        disabled={action.busy}
        onPress={() =>
          guard.discard(() => {
            setCurrent("");
            setNext("");
          })
        }
      >
        Cancelar cambio de contraseña
      </Button>
    </Panel>
  );
}
export function Verification() {
  const { api, refresh, session } = useSession(),
    action = useAction();
  const [code, setCode] = useState("");
  return (
    <Stack>
      <Panel title="Verifica tu correo">
        <Body>Introduce el código enviado a {session?.user.email}. Caduca a los diez minutos.</Body>
        <Feedback error={action.error} success={action.success} />
        <Field
          label="Código de ocho dígitos"
          value={code}
          onChangeText={setCode}
          keyboardType="number-pad"
          maxLength={8}
          autoComplete="one-time-code"
        />
        <Row>
          <Button
            disabled={action.busy}
            onPress={() =>
              void action.run(async () => {
                if (!/^[0-9]{8}$/.test(code)) throw new Error("El código debe contener ocho dígitos.");
                await api("/auth/email-verifications/confirm", {
                  method: "POST",
                  body: { code },
                });
                setCode("");
                await refresh();
              })
            }
          >
            Confirmar correo
          </Button>
          <Button
            secondary
            disabled={action.busy}
            onPress={() =>
              void action.run(async () => {
                const r = await api("/auth/email-verifications/resend", {
                  method: "POST",
                });
                if (r.status === 204) await refresh();
                else action.setSuccess("Envío solicitado. Revisa tu bandeja de entrada.");
              })
            }
          >
            Reenviar código
          </Button>
        </Row>
      </Panel>
      <PasswordForm />
    </Stack>
  );
}
