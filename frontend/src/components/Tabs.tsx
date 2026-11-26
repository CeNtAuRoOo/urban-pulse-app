import { Pressable, ScrollView, Text, View } from "react-native";
import { colors as c } from "../design/tokens";
export function Tabs({
  value,
  onChange,
  items,
}: {
  value: string;
  onChange: (v: string) => void;
  items: { value: string; label: string }[];
}) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flexGrow: 0 }}>
      <View
        role="tablist"
        style={{
          flexDirection: "row",
          gap: 6,
          padding: 6,
          backgroundColor: c.surface,
          borderRadius: 16,
          borderWidth: 1,
          borderColor: c.border,
        }}
      >
        {items.map((item) => (
          <Pressable
            key={item.value}
            role="tab"
            accessibilityState={{ selected: value === item.value }}
            onPress={() => onChange(item.value)}
            style={({ hovered }) => ({
              minHeight: 44,
              paddingVertical: 12,
              paddingHorizontal: 20,
              borderRadius: 11,
              backgroundColor: value === item.value ? c.ink : hovered ? c.tint : "transparent",
            })}
          >
            <Text style={{ color: value === item.value ? "#fff" : c.muted, fontWeight: "600", fontSize: 14 }}>
              {item.label}
            </Text>
          </Pressable>
        ))}
      </View>
    </ScrollView>
  );
}
