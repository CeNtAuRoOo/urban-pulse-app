import { Link, useRouter } from "expo-router";
import type { ComponentProps } from "react";
import { useFormNavigation } from "../services/form-guard";

/** Keeps real links (including open-in-new-tab) while guarding current-screen navigation. */
export function AppLink({ onPress, ...props }: ComponentProps<typeof Link>) {
  const guard = useFormNavigation(),
    router = useRouter();
  return (
    <Link
      {...props}
      onPress={(event) => {
        onPress?.(event);
        if (event.defaultPrevented) return;
        if ("metaKey" in event && (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey)) return;
        if (props.target === "_blank" || !guard.changes.has()) return;
        event.preventDefault();
        guard.request(() => (props.replace ? router.replace(props.href) : router.push(props.href)));
      }}
    />
  );
}
