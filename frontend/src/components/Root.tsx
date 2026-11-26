import { FormGuardProvider } from "./FormGuardProvider";
import { Slot } from "expo-router";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { SessionProvider } from "../services/session";
export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <SessionProvider>
        <FormGuardProvider>
          <Slot />
        </FormGuardProvider>
      </SessionProvider>
    </SafeAreaProvider>
  );
}
