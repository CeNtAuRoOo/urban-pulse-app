import { useEffect, useLayoutEffect, useRef, type ReactNode } from "react";
import { ScrollView, type StyleProp, type ViewStyle } from "react-native";
import { scrollPositions } from "../services/navigation";
/** Restore each URL/account without remounting the nested Expo navigator. */
export function ScreenScroll({
  screenKey,
  children,
  contentContainerStyle,
}: {
  screenKey: string;
  children: ReactNode;
  contentContainerStyle: StyleProp<ViewStyle>;
}) {
  const view = useRef<ScrollView>(null),
    target = useRef(scrollPositions.get(screenKey) || 0),
    restored = useRef((scrollPositions.get(screenKey) || 0) === 0),
    height = useRef(0),
    content = useRef(0);
  useLayoutEffect(() => {
    target.current = scrollPositions.get(screenKey) || 0;
    restored.current = target.current === 0;
    if (restored.current) view.current?.scrollTo({ y: 0, animated: false });
  }, [screenKey]);
  useEffect(() => {
    const timer = setTimeout(() => {
      if (!restored.current) {
        view.current?.scrollTo({
          y: Math.min(target.current, Math.max(0, content.current - height.current)),
          animated: false,
        });
        restored.current = true;
      }
    }, 1600);
    return () => clearTimeout(timer);
  }, [screenKey]);
  return (
    <ScrollView
      ref={view}
      nativeID="main-content"
      contentContainerStyle={contentContainerStyle}
      scrollEventThrottle={100}
      onLayout={(e) => {
        height.current = e.nativeEvent.layout.height;
      }}
      onContentSizeChange={(_w, h) => {
        content.current = h;
        if (!restored.current && h >= target.current + height.current) {
          view.current?.scrollTo({ y: target.current, animated: false });
          restored.current = true;
        }
      }}
      onScroll={(e) => {
        if (restored.current) scrollPositions.set(screenKey, e.nativeEvent.contentOffset.y);
      }}
    >
      {children}
    </ScrollView>
  );
}
