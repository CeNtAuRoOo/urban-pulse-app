import { Platform } from "react-native";
export const colors = {
  ink: "#172C42",
  muted: "#52616F",
  primary: "#00695F",
  primaryDark: "#00594F",
  canvas: "#F7F8F5",
  surface: "#FFFFFF",
  border: "#DCE3DF",
  tint: "#EAF2EB",
  lime: "#D4EF75",
  coral: "#FFB59D",
  danger: "#A52E37",
  dangerTint: "#FFF0F0",
  warning: "#78530B",
  warningTint: "#FFF5D9",
};
export const displayFont = Platform.select({
  web: "Georgia, 'Times New Roman', serif",
  ios: "Georgia",
  default: "serif",
});
export const space = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32, xxl: 48 };
export const radius = { control: 12, panel: 20 };
