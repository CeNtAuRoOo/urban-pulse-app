import { Slot } from "expo-router";
import { SessionProvider } from "../services/session";
import "../global.css";

export default function RootLayout() {
  return (
    <SessionProvider>
      <Slot />
    </SessionProvider>
  );
}
