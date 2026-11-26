import { useSession } from "../services/session";
import { useFormNavigation } from "../services/form-guard";
import { Button } from "./ui";
import { Slot } from "expo-router";
import {
  Image,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Text,
  View,
  useWindowDimensions,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import Feather from "@expo/vector-icons/Feather";
import { colors as c, displayFont } from "../design/tokens";
export function Brand({ light = false }: { light?: boolean }) {
  return (
    <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
      <Text
        style={{
          fontFamily: displayFont,
          fontSize: 27,
          fontWeight: "700",
          letterSpacing: -1,
          color: light ? "#fff" : c.ink,
        }}
      >
        UrbanPulse
      </Text>
      <Feather name="activity" size={29} color={light ? c.lime : c.primary} />
    </View>
  );
}
export function AuthLayout() {
  const { width, height } = useWindowDimensions(),
    wide = width >= 900;
  const compact = height < 760;
  const { session, logout } = useSession();
  const guard = useFormNavigation();
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: c.canvas }}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView
          keyboardShouldPersistTaps="handled"
          automaticallyAdjustKeyboardInsets
          contentContainerStyle={{ flexGrow: 1 }}
        >
          <View
            style={{
              width: "100%",
              flexGrow: 1,
              flexDirection: wide ? "row" : "column",
              overflow: "hidden",
              backgroundColor: c.surface,
            }}
          >
            <View
              style={{
                width: wide ? "54%" : "100%",
                backgroundColor: c.ink,
                padding: wide ? (compact ? 32 : 48) : 24,
                minHeight: wide ? undefined : 250,
                overflow: "hidden",
              }}
            >
              <Brand light />
              <View
                style={{
                  marginTop: wide ? (compact ? 32 : 56) : 25,
                  gap: 18,
                  zIndex: 1,
                  maxWidth: wide ? 490 : 250,
                }}
              >
                <Text
                  role="heading"
                  aria-level={1}
                  style={{
                    fontFamily: displayFont,
                    fontSize: wide ? (compact ? 54 : 66) : 34,
                    lineHeight: wide ? (compact ? 57 : 68) : 37,
                    fontWeight: "700",
                    letterSpacing: -1.5,
                    color: "#fff",
                  }}
                >
                  La ciudad cambia contigo.
                </Text>
                <View style={{ width: 78, height: 6, borderRadius: 3, backgroundColor: c.lime }} />
                {wide && height >= 700 ? (
                  <Text style={{ color: "#E5ECEF", fontSize: 20, lineHeight: 28, maxWidth: 390 }}>
                    Tu mirada. Una mejora. Un lugar mejor para todos.
                  </Text>
                ) : null}
              </View>
              <Image
                accessibilityElementsHidden
                importantForAccessibility="no-hide-descendants"
                source={require("../../assets/images/city.png")}
                resizeMode="contain"
                style={
                  wide
                    ? {
                        position: "absolute",
                        bottom: -10,
                        left: 0,
                        width: "100%",
                        height: Math.min(350, height * 0.36),
                      }
                    : {
                        position: "absolute",
                        bottom: -12,
                        right: -15,
                        width: 230,
                        height: 150,
                        opacity: 0.85,
                      }
                }
              />
            </View>
            <View
              style={{
                width: wide ? "46%" : "100%",
                justifyContent: "center",
                padding: wide ? (width < 1200 ? 32 : 54) : 28,
                gap: 30,
              }}
            >
              <View style={{ width: "100%", maxWidth: 520, alignSelf: "center", gap: 30 }}>
                <Slot />
                <View style={{ height: 1, backgroundColor: c.border }} />
                <Text style={{ color: c.muted, fontSize: 13, lineHeight: 21 }}>
                  Un espacio compartido para cuidar tu ciudad.
                </Text>
                {session ? (
                  <Button secondary onPress={() => guard.request(() => logout())}>
                    Cerrar sesión
                  </Button>
                ) : null}
              </View>
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
