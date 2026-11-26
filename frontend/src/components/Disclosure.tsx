import { useState, type ReactNode } from "react";
import { Pressable, Text, View } from "react-native";
import Feather from "@expo/vector-icons/Feather";
import { colors as c } from "../design/tokens";
import { styles } from "./ui";
export function Disclosure({ title, children }: { title: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <View style={{ borderTopWidth: 1, borderColor: c.border, paddingTop: 10, gap: 16 }}>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        onPress={() => setOpen(!open)}
        style={[styles.row, { minHeight: 44, justifyContent: "space-between" }]}
      >
        <Text style={[styles.text, { fontWeight: "600" }]}>{title}</Text>
        <Feather name={open ? "chevron-up" : "chevron-down"} size={18} color={c.ink} />
      </Pressable>
      {open ? children : null}
    </View>
  );
}
