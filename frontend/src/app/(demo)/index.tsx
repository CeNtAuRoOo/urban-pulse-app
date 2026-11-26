import { Image, Text, View, useWindowDimensions } from "react-native";
import { createIncidentHref } from "../../services/navigation";
import { AppLink as Link } from "../../components/AppLink";
import Feather from "@expo/vector-icons/Feather";
import { Body, NavLink, Panel, QueryState, styles } from "../../components/ui";
import { useSession } from "../../services/session";
import { permissions } from "../../services/permissions";
import { useResource } from "../../services/hooks";
import { colors as c, displayFont } from "../../design/tokens";
import type { IncidentSummary, Page } from "../../types/api";
import { IncidentRows } from "../../features/Incidents";
export default function Home() {
  const { session } = useSession(),
    p = permissions(session?.user),
    wide = useWindowDimensions().width >= 1000;
  const recent = useResource<Page<IncidentSummary>>(p.staff ? "/incidents" : "/incidents/mine", {
    page: 1,
    pageSize: 5,
    ...(p.staff ? { status: "REPORTED" } : {}),
  });
  return (
    <>
      <View
        style={{ flexDirection: wide ? "row" : "column", gap: 24, alignItems: wide ? "center" : "stretch" }}
      >
        <View style={{ flex: 1, gap: 14 }}>
          <Text
            role="heading"
            aria-level={1}
            style={{
              fontFamily: displayFont,
              fontWeight: "700",
              fontSize: wide ? 52 : 38,
              lineHeight: wide ? 56 : 42,
              letterSpacing: -1.5,
              color: c.ink,
            }}
          >
            {p.staff ? "Una ciudad que avanza." : "Tu ciudad, en movimiento."}
          </Text>
          <Body muted>
            {p.staff
              ? "Revisa los avisos, toma decisiones y da el siguiente paso."
              : "Comunica lo que necesita atención y sigue cada paso."}
          </Body>
        </View>
        <Link
          href={createIncidentHref("/")}
          style={{
            backgroundColor: c.coral,
            borderRadius: 22,
            padding: 24,
            color: c.ink,
            fontWeight: "700",
            fontSize: 17,
            minWidth: 210,
            textAlign: "center",
          }}
        >
          <Feather name="volume-2" size={25} color={c.ink} />
          {"  "}Comunicar incidencia
        </Link>
      </View>
      <View
        style={{
          flexDirection: "row",
          minHeight: wide ? 190 : 160,
          backgroundColor: "#ECF0E8",
          borderRadius: 24,
          overflow: "hidden",
          alignItems: "center",
          padding: wide ? 30 : 22,
        }}
      >
        <View style={{ width: wide ? "55%" : "60%", gap: 12, zIndex: 1 }}>
          <Feather name="map" size={28} color={c.primary} />
          <Text
            style={{
              fontFamily: displayFont,
              color: c.ink,
              fontSize: wide ? 29 : 23,
              lineHeight: 32,
              fontWeight: "700",
            }}
          >
            Otra forma de mirar tu ciudad.
          </Text>
          <NavLink href="/map">Explorar el mapa →</NavLink>
        </View>
        <Image
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          source={require("../../../assets/images/city.png")}
          resizeMode="contain"
          style={{
            position: "absolute",
            right: wide ? -10 : -70,
            bottom: -25,
            width: wide ? "52%" : 260,
            height: wide ? 250 : 190,
          }}
        />
      </View>
      <View style={{ gap: 18 }}>
        <View style={[styles.row, { justifyContent: "space-between" }]}>
          <Text role="heading" aria-level={2} style={[styles.h2, { fontSize: 27 }]}>
            {p.staff ? "Pendientes de revisión" : "Tus últimos avisos"}
          </Text>
          <NavLink href={p.staff ? "/incidents/all" : "/incidents"}>Ver todos →</NavLink>
        </View>
        <Panel>
          <QueryState {...recent} />
          {recent.result?.data.data.length ? (
            <IncidentRows items={recent.result.data.data} origin="/" />
          ) : recent.result ? (
            <View style={{ alignItems: "center", gap: 14, paddingVertical: 24 }}>
              <Feather name={p.staff ? "check-circle" : "message-square"} size={36} color={c.primary} />
              <Text style={[styles.text, { fontWeight: "600" }]}>
                {p.staff ? "No hay avisos pendientes de revisión." : "Todavía no has comunicado incidencias."}
              </Text>
              <Body muted>
                {p.staff
                  ? "Los nuevos avisos aparecerán aquí."
                  : "Cuando comuniques una incidencia, podrás seguirla desde aquí."}
              </Body>
              {!p.staff ? (
                <NavLink href={createIncidentHref("/")}>Comunicar tu primera incidencia →</NavLink>
              ) : null}
            </View>
          ) : null}
        </Panel>
      </View>
    </>
  );
}
