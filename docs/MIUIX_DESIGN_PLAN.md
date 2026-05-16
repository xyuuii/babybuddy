# Miuix Design Plan

Miuix can be used as a visual and interaction reference for BabyBuddy, especially because the target test device is a Xiaomi/HyperOS phone.

## Approach

Do not rewrite the whole app at once. BabyBuddy should first keep Material 3 as the stable base, then introduce a small design adapter layer that can gradually adopt Miuix-like components and motion.

## Recommended Migration Order

1. Settings and NAS configuration
   - Use denser preference-style rows, grouped cards, and clearer connection states.
   - This area benefits most from Miuix preference patterns.

2. AI assistant
   - Keep the current conversation logic, but refine the input bar, message surfaces, and keyboard transitions.
   - Avoid heavy blur or large recomposition during streaming text.

3. Home, feeding, and vaccine dashboards
   - Convert repeated cards to a shared BabyBuddy card/token system.
   - Keep animation short and spring-light so tab switches stay smooth.

4. Media gallery
   - Use stable thumbnails, progressive loading, and host-independent remote paths.
   - Avoid host-specific URLs as item identity.

## Design Tokens To Add

- App surface colors for warm light mode and calm dark mode.
- Card radius, list radius, and bottom bar radius tokens.
- Shared elevation policy that avoids shadow seams around bottom input bars.
- Motion durations for tab transitions, sheet entrance, and message streaming.

## Adoption Rule

Only add the Miuix dependency when we are ready to replace a specific screen or component family. Until then, use Miuix as a design reference so the app remains easy to build and publish.
