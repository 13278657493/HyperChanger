# iOS Notification Center Extraction Audit

Branch: adapt/xiaomi-17-pro-max

## Scope

Extract only the iOS-style notification center into a standalone LSPosed module/APK. Do not modify main. Keep the original HyperChanger implementation intact while the new module is built alongside it.

## Verified entry points and coupling

- The current libxposed initialization list is app/src/main/resources/META-INF/xposed/java_init.list; it currently registers btm.m.os4.systemuihook.hypermusiccover.Main among the original module entry points.
- hypermusiccover/Main.java invokes IosNotificationCenterHooks.install(cl) from its SystemUI package-load path. It also owns unrelated music-cover, wallpaper, diagnostics, and shade features, so it must not be copied wholesale as the new module entry point.
- newnotificationcenter/IosNotificationCenterHooks.java installs notification backdrop, stack, clock, shortcut, dismiss-button, keyguard, header, raw expansion, native switch-progress, and panel-ownership hooks. It directly depends on Xp, ShadeLayer, and the notification presentation/state classes.
- hypermusiccover/ShadeLayer.java is 2,119 lines and combines two feature areas. Its iOS state/controller section starts near the top (iOS flags and IosShadeState, then raw expansion, notification/control-center ownership, switching, progress rendering, and preference binding). The same class also owns the legacy music-cover shade, album-art bitmap, home/lock wallpaper requests, card blur, shade content shifting, and diagnostics.
- The iOS section is not yet a drop-in independent controller: it calls Main.keyguardLocked(), Main.requestShadeWallpaper(), Main.invalidateShadeWallpaper(), Main.setCardBlurActive(), and other legacy shade APIs. The legacy and iOS paths share view/layer fields and lifecycle code.
- IosNotificationCenterPresentation.java has direct references to ShadeLayer.iosEnabled(), iosNotificationPanelEnabled(), and keyguardLocked(); these must be redirected to the new controller interface during extraction.
- IosNotificationStackHooks.java and IosShadeBackdropBlur.java use Xp extensively. Xp.java itself imports the original module's ModuleLog and routes logging through HookModuleLogger, so the new module needs a small independent hook/log adapter.
- Notification settings stores use the original REMOTE_PREFERENCE_GROUP and XposedService; header settings also load a drawable from the original app's R. These settings and resources must be made module-local, with preference keys preserved where useful.
- The existing manifest and Gradle configuration belong to the full HyperChanger app (many unrelated permissions, providers, dependencies, and native build configuration). They are not a suitable standalone-module template without pruning.

## Initial file disposition

### Port with package/import adaptation

- newnotificationcenter/IosNotificationCenterHooks.java
- newnotificationcenter/IosNotificationCenterPresentation.java
- newnotificationcenter/IosNotificationStackHooks.java
- newnotificationcenter/IosNotificationAnimationHooks.java
- Notification-only state, clear-action, gesture, wallpaper, material, glass, blur, and shortcut classes under newnotificationcenter/

### Rewrite or extract before porting

- hypermusiccover/ShadeLayer.java → new IosShadeController: extract notification expansion, switch state, settle animation, active-state and wallpaper/material lifecycle. Replace calls into Main with explicit notification-module services. Do not copy legacy cover/card-blur/content-shift code.
- hypermusiccover/Xp.java → minimal module-local hook/reflection/log adapter; remove original ModuleLog and HookModuleLogger dependencies.
- IosNotificationCenterPresentation.java: replace direct ShadeLayer references with IosShadeController API.
- Settings stores: replace REMOTE_PREFERENCE_GROUP and original resource references with standalone module-local preferences/resources.

### Do not port

- Album-art composition and music-cover lifecycle.
- Legacy shade card blur and shade-content shifting.
- Music playback, lyrics, media controls, unrelated SystemUI hooks and original app providers/services.

## Next implementation sequence

1. Finish the dependency map for the controller's shared view/layer lifecycle and every Main call used by the iOS path.
2. Create a separate standalone Android/LSPosed module skeleton with its own package, manifest, java_init.list, minimal libxposed entry class, and local settings/logging.
3. Add the minimal hook/reflection adapter and compile the empty module.
4. Extract IosShadeController behind a narrow interface, then redirect presentation and hook calls.
5. Port notification-only classes in small groups; compile after each group.
6. Add a compact settings screen and module-local preferences.
7. Produce a debug APK and test on the target Xiaomi device before claiming compatibility.

## Validation status

This document is based on source inspection on the branch above. No business source files have been changed by this audit, and no APK build or device test has been run as part of it.
