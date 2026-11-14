# iOS Notification Center Porting Map

Branch: adapt/xiaomi-17-pro-max

## Current standalone project

The separate Gradle project is included as :ios-notification-center. Its application ID is btm.m.os4.iosnotificationcenter, with a libxposed API 102 entry point and SystemUI-only static scope.

The new module has its own Xp helper. It is adapted from the original reflection/hook helper, but its logger now uses Android Log and it has no imports from the original HyperChanger package. The entry point attaches this helper only after the SystemUI package callback.

The module now installs the independent gesture observer, notification-stack hooks, and presentation lifecycle hooks only in `com.android.systemui`. The presentation bridge attaches when `NotificationShadeWindowView` is captured; native rows are claimed only while the independent presentation reports active ownership. The module is not yet build-verified.

## Notification package dependency map

### Core hook and presentation classes

- IosNotificationCenterHooks: direct dependencies on ShadeLayer and Xp. Calls the shade controller for raw expansion, deferred dismiss, native switch progress, control-center appearance, and notification appearance.
- IosNotificationCenterPresentation: depends on Xp and directly queries ShadeLayer for enabled state, panel ownership, and keyguard state. Redirect these queries to IosShadeController.
- IosNotificationAnimationHooks: depends on ShadeLayer for notification-panel interception and on Xp for hooks. Redirect the state query to IosShadeController.
- IosNotificationStackHooks: depends on Xp only.
- IosShadeBackdropBlur, IosShadeBackdropCapture, IosShadeMaterial, IosNotificationEntryState, IosNotificationExitState, IosNotificationShortcutHost, IosNotificationShortcutLaunch, IosShadeGlassView, and IosShadeLiquidLens: depend on Xp; port after the local helper is in place.
- IosShadeClearAction, IosShadeClearGesture, IosShadeWallpaper, IosShadeWallpaperReveal, IosShadeRevealState, IosShadeState, and IosShadeBlurEffects: no direct original-package dependency was found in their imports/references; still verify resources and call sites during port.

### Settings classes

All seven settings classes use the original REMOTE_PREFERENCE_GROUP; most also use XposedService remote preferences. These are not portable as-is to a standalone module.

- IosNotificationCenterSettings
- IosNotificationBottomOffsetSettings
- IosNotificationClockSettings
- IosNotificationExpansionSettings
- IosNotificationHeaderSettings
- IosNotificationRefractionSettings
- IosNotificationShortcutsSettings

Required changes:
1. Replace the shared HyperChanger preference group with a standalone preference file/group.
2. Preserve existing keys and normalization behavior where possible to ease migration.
3. Replace remote preference synchronization with local settings storage and a clear settings-to-SystemUI propagation mechanism.
4. Remove IosNotificationClockSettings dependencies on the original SoftGlassSettings/softGlassParameters unless the corresponding feature is intentionally retained.
5. Move the notification_center drawable into the standalone module and update its generated R reference.

## ShadeLayer-to-host bridge discovered

ShadeLayer contains 2,119 lines. Its iOS controller path and the legacy cover/shade feature share fields and lifecycle methods, so it must be split by responsibility rather than copied by line range.

All direct Main calls found in ShadeLayer:

- Potential notification-controller host services: keyguardLocked, screenOnCached, requestShadeWallpaper, invalidateShadeWallpaper.
- Legacy-only or mixed cover/shade services: setShadeArt, setShadeWallpaper, coverModeOn, releasing, verbose, shadeRow, setCardBlurActive, setShadeContentShift, setShadeBias, shadeBiasDelta, clockContainerShown, shadePushDescribe.

The new controller should receive a narrow host interface for screen/keyguard state and wallpaper acquisition/invalidation. It must not depend on Main or retain music-cover state. Card-blur and shade-content-shift methods are legacy features and should not be pulled into the standalone runtime.

## Next implementation units

1. Define IosShadeHost and IosShadeController state contracts; move notification/control-center ownership and progress state behind that API.
2. Replace ShadeLayer references in IosNotificationCenterHooks, IosNotificationCenterPresentation, and IosNotificationAnimationHooks with the controller API.
3. Port the notification-only utility classes and resources in small batches.
4. Implement local settings storage and a settings-to-SystemUI update channel.
5. Only after these compile independently, install the hooks from IosNotificationCenterModule.

## Build pipeline

The existing Android GitHub Actions workflow now uploads a separate iOS-Notification-Center-debug artifact in addition to the original HyperChanger APK. No workflow run or successful build has been observed yet, so compile status remains unverified.


## 第三阶段：独立状态控制层（已建立）

本阶段在 `ios-notification-center` 中新增了不依赖旧音乐封面模块的状态层：

- `IosShadeState.java`：独立封装纵向拖动进度、通知/控制中心归属、横向切换进度。
- `IosShadeController.java`：为 SystemUI hook 提供通知中心专用回调入口和状态读取接口。
- `IosShadeBlurEffects.java`：独立的 RenderEffect 模糊半径缓存，不引用旧包。

注意：这批代码目前是独立模块内的基础层，尚未接入 SystemUI hooks，也尚未完成 Gradle 编译。旧模块的 ShadeLayer 与原始通知中心逻辑保持不变。下一步应把通知中心 hooks 的状态回调逐步改为调用新 controller，再迁移视图/壁纸呈现层。


## 第四阶段：独立原生手势观察器（已接入，暂不接管 UI）

新增 `IosShadeGestureHooks.java`，仅在独立模块中观察以下 MIUI/SystemUI 回调：

- `NotificationPanelExpandController.notifyExpandHeightChanged`：读取原生通知面板高度、阈值和拖动状态。
- `ShadeSwitchControllerImpl.performProgressChanged/setSwitching`：读取通知与控制中心切换进度。
- `NotificationPanelViewControllerInjector.onControlCenterAppearChanged/onNotificationAppearChanged`：跟踪当前面板归属。

独立模块入口现在会在 SystemUI 加载时安装该观察器。观察结果只写入新建的 `IosShadeController`，没有调用旧 `ShadeLayer`，也不会改变原生通知面板的可见性、触摸、动画或壁纸。

这是有意设置的安全边界：当前只验证独立模块的 hook 接入路径；通知中心视觉呈现层尚未迁移，因此没有启用 UI 接管。设备上的类名/方法签名仍需目标 ROM 验证，当前没有编译或真机验证结果。


## 第五阶段：独立玻璃视觉基元（已建立，尚未挂载）

新增：

- `IosNotificationGlassView.java`：独立圆角玻璃 View，优先尝试 MIUI 原生玻璃材质 API；API 不可用时退回半透明填充和描边。
- `IosNotificationGlassHost.java`：接收调用方测量好的父容器与局部坐标矩形，负责把玻璃 View 放在子视图底层并管理销毁。

安全约束：

- Glass View 不可点击、不获取焦点、不拦截触摸。
- Host 不自行猜测通知面板的位置和尺寸；必须由后续适配层提供测量后的 bounds。
- 该组件尚未接入通知面板布局，也没有启用视觉接管；当前只是独立视觉基元。
- MIUI 原生材质方法和参数需要在小米 17 Pro Max 目标 ROM 上验证，不能据此认定模糊效果已经生效。


## 第六阶段：独立背景模糊控制器（已移植，默认被动）

新增 `IosShadeBackdropBlur.java`，从原通知中心模糊控制逻辑中拆出，并改用独立包内的 `Xp`：

- 观察 MIUI `View.setMiBackgroundBlurRadius(int)` 请求，并保存 OEM 原始半径。
- 观察 SystemUI `BlurUtils.applyBlur(ViewRootImpl, int)`，记录窗口模糊目标与实际应用半径。
- 提供手势进度、壁纸显露透明度、当前模糊半径和释放/恢复接口。
- `IosShadeGestureHooks.install()` 现在会注册该控制器的 hook。

重要边界：当前没有任何代码调用 `IosShadeBackdropBlur.update(...)`，因此尚未接管具体通知面板，也不会主动改写 OEM 的模糊半径；只有未来 presentation host 明确提供 root 并调用 update 后才会进入控制状态。设备上的隐藏 API、BlurUtils 类和字段仍需实机验证。


## 第七阶段：通知行材质 hook 移植（已移植，默认不接管）

新增 `IosShadeMaterial.java`，将通知行/媒体头/分组折叠控件的 MIUI View 材质观察与恢复逻辑移入独立包，并将旧 Xp 引用替换为新模块自己的反射/Hook 工具。

- `install()` 已加入独立 SystemUI hook 初始化流程，注册原生 View 材质 setter 的拦截器。
- 默认 `active=false`，不会对通知行或系统面板启用材质覆盖。
- 只有后续 presentation host 调用 `setActive(true, measuredWindow)` 后，才会按通知面板根 View 扫描、应用局部材质；关闭时恢复保存的 OEM 参数。
- 时钟、媒体头、分组控件注册接口保留，后续接入独立布局时再使用。

注意：此类涉及多组隐藏 MIUI View API，必须先在目标 ROM 验证实际方法/字段和恢复行为；目前只是代码移植及被动 hook 注册，不代表视觉效果已经启用。


## 第八阶段：清空通知交互（已独立移植，等待 UI 绑定）

新增：

- `IosShadeClearAction.java`：清空通知单次分发、空列表判断、原生点击回退、触觉/缩放反馈和按钮触摸监听。
- `IosShadeClearGesture.java`：在 `NotificationShadeWindowView.dispatchTouchEvent` 前捕获从清空按钮开始的手势，处理触摸 slop、多指和取消状态。

接入情况：

- 独立 SystemUI 初始化时注册根窗口手势 hook。
- 清空按钮尚未由独立 presentation 创建，因此 target 初始为空；未绑定时该 hook 直接放行 OEM 原生事件。
- 后续布局创建清空按钮后调用 `IosShadeClearAction.bindClear(button)` 即可绑定。
- 清空动作优先调用实时通知列表的原生 clear API，只有首选路径不可用时才回退到原生 listener/button，避免重复清空。

这两个文件已改为独立包，不依赖旧 ShadeLayer、旧 Main 或 hypermusiccover。清空按钮的具体布局和绑定点待独立 presentation 层接入。


## 第九阶段：壁纸显露与边缘光学组件（已独立移植）

新增独立组件：

- `IosShadeWallpaper.java`：创建自有预览 Bitmap，不裁切或回收系统原始 Bitmap。
- `IosShadeRevealState.java`：壁纸显露状态机。
- `IosShadeBackdropCapture.java`：异步采集屏幕边缘光学采样帧；排除通知栏/状态栏/导航栏等图层，不申请绕过安全或受保护内容策略。
- `IosShadeLiquidLens.java`：基于 RuntimeShader、RenderNode 的边缘液态折射渲染。
- `IosShadeGlassView.java`：组合 MIUI 原生玻璃、边缘折射和降级高光。
- `IosShadeWallpaperReveal.java`：壁纸透明度/模糊动画。原先对旧 Presentation 的静态调用已替换为窄接口 `Listener.onWallpaperReveal(alpha, blurRadius)`，可由独立 presentation host 实现。

这些类现在位于独立包，不引用旧 Presentation、ShadeLayer、Main 或 hypermusiccover。动画和玻璃 View 尚未加入通知面板层级；独立宿主后续需要创建 ImageView/玻璃 View、提供窗口根 View，并实现 Listener 回调。


## 第十阶段：通知列表与行生命周期 hook（已独立移植）

新增：

- `IosNotificationStackHooks.java`：通知堆栈布局、滚动范围、分组/媒体/焦点通知堆叠处理、视口裁切与绘制观察。
- `IosNotificationEntryState.java`：通知行进入、位置 settle、绘制帧同步状态。
- `IosNotificationExitState.java`：通知行退出动画、卡片状态保持与恢复。
- `IosNotificationAnimationHooks.java`：通知行位置和 OEM 面板动画协调。
- `IosNotificationPresentationBridge.java`：SystemUI hook 与独立 Presentation 之间的窄接口。

接入与安全边界：

- 独立模块入口已注册通知堆栈 hook。
- Bridge 初始没有 Host，所有 ownsStack/ownsRow 判断均为 false；在真正的独立 Presentation 注册 Host 前，不接管通知列表、不修改 OEM 行布局。
- 动画 hook 另加 `Bridge.isActive()` 门槛，避免只因观察到原生手势就抑制 MIUI 原生动画。
- 四个移植类已清除旧 ShadeLayer、hypermusiccover 和旧 Presentation 引用；后续由独立 Presentation 实现 Bridge.Host 后才会启用自定义布局行为。

这批代码已完成静态引用核对，但尚未执行 Android Gradle 编译。目标 ROM 的方法签名仍需由用户真机验证。


## 第十一阶段：独立 Presentation 主体与 SystemUI 生命周期接入

新增并接入：

- `IosNotificationCenterPresentation.java`：移植原通知中心 Presentation 主体，包含第二时钟容器、通知面板宿主、通知堆栈定位、壁纸/景深层、顶部区域、清空按钮同步与快捷操作同步。
- `IosNotificationPresentationHooks.java`：捕获 `NotificationShadeWindowView`、通知堆栈和 MIUI 通知头的生命周期。
- `IosNotificationPresentationBridge.java`：由 Presentation 实现 Host，向通知堆栈/行生命周期 hook 提供视口、归属、偏移、透明度等数据。
- `IosNotificationShortcutHost.java` 与 `IosNotificationShortcutLaunch.java`：快捷操作宿主与启动清理逻辑独立迁移。
- `IosShadeGestureHooks.java`：将原生展开高度、控制中心切换和通知面板归属回调同步到独立 Presentation。

重要保护：

- Presentation 现在完全位于独立包，不再引用旧 ShadeLayer、Main 或 hypermusiccover。
- Bridge 仅在 Presentation 捕获到系统通知窗口并 attach 后才接入 Host；没有宿主时通知行 hook 不会认领 OEM View。
- 入口仍只加载 `com.android.systemui`。
- 当前 Presentation 创建路径仍依赖 MIUI SystemUI 内部 View/资源 ID 和锁屏时钟组件；实际 ROM 兼容性由用户真机验证。

本阶段完成代码接线和静态旧包引用检查；尚未执行 Gradle 构建，不能据此认定 APK 已可安装。


### Presentation 生命周期补充

- `IosNotificationPresentationHooks` 现在还观察 `KeyguardStateControllerImpl.notifyKeyguardState` 与 `KeyguardUpdateMonitor.setKeyguardShowing`，并把锁屏状态交给 Presentation。
- `IosShadeGestureHooks` 已把原生展开进度、切换进度和面板归属更新传给 Presentation 的 `update/setSwitching`。
- 当前独立 Presentation 会在捕获到 `NotificationShadeWindowView` 后注册 Bridge Host；未创建成功时 Bridge 的 active/ownership 条件仍为 false。


### 本轮生命周期健壮性修正

- 修复 Presentation 更换通知窗口时 Bridge Host 被紧接着的 `release()` 一并 detach 的顺序问题：现在先释放旧窗口，再注册新窗口 Host。
- 为 `NotificationShadeWindowView` 的继承 `onDetachedFromWindow` 使用父类方法查找后单独挂钩，避免因 OEM 子类未声明该方法而中断窗口捕获 Hook 安装。
- 窗口 detach 时只释放当前持有的 root，并清除旧 stack/header View 引用，防止跨窗口复用过期 View。
- 修正模块入口注释与 APK 首页状态文案，避免仍显示“Hook 尚未启用”的过期脚手架提示。

这些属于代码级生命周期修复；仍未有 GitHub Actions 构建运行记录，尚不能视为编译通过。


## Latest lifecycle hardening

- `IosNotificationPresentationHooks` observes notification-window attach/detach, stack/header capture, and keyguard state callbacks.
- `IosNotificationCenterPresentation.captureStack()` and `captureHeader()` now retry presentation creation when the notification hierarchy becomes available after the shade window. This addresses the ordering case where the first expansion arrives before the panel/clock subtree has finished inflating.
- Retry is gated by an attached shade window, positive notification progress, an enabled standalone controller, and the absence of an existing host. It does not force OEM UI ownership before a host exists.
- GitHub Actions currently reports no workflow runs for `adapt/xiaomi-17-pro-max`; compilation and APK packaging remain unverified.


## Material and backdrop integration

- The active presentation's pre-draw synchronization now enables IosShadeMaterial against the captured shade window, registers the hosted keyguard clock, and drives IosShadeBackdropBlur with the current notification expansion progress.
- Presentation teardown and feature-disable paths now restore captured MIUI material properties and the original backdrop blur before removing the overlay.
- These APIs are gated behind a successfully created presentation host; merely loading the LSPosed module does not activate the material override.
- This is source-level integration only. The Xiaomi 17 Pro Max ROM's hidden MIUI blur/material APIs still require build and device verification.


## Keyguard transition handling

- Keyguard state changes now immediately restore notification shortcuts when the device locks and re-evaluate shortcut eligibility when it unlocks.
- A posted panel-thread refresh rechecks clear-button discovery and shortcut attachment after the SystemUI keyguard transition settles; it validates the panel is still attached before touching views.
- This avoids leaving stale shortcut views hidden or attached across lock/unlock without changing native clear-button eligibility.


## Hosted clock sampling restoration

- The OEM KeyguardClockContainer is no longer modified by an early untracked containment setter during presentation creation.
- IosShadeMaterial now disables clock wallpaper containment only when both setter and getter are available, snapshots the original value first, and restores it with the other captured material properties during teardown.
- On builds exposing the setter without a getter, the controller deliberately leaves the clock's native containment unchanged rather than risk a persistent SystemUI modification.


## Optional OEM clock fallback

- Presentation creation no longer fails solely because `KeyguardClockContainer` is absent, fails to inflate, or contains no clock content.
- The independent notification host and stack integration can still attach, while the native SystemUI header remains visible as the date/status fallback.
- Clock-specific synchronization is skipped when no hosted clock exists; landscape layout does not reserve an empty clock column.
- When a compatible clock is available, the existing hosted-clock path and material registration remain unchanged.


## Wallpaper reveal teardown

- Releasing a presentation during an interrupted wallpaper reveal restores the hosted foreground image alpha and clears its render effect before dropping the view reference.
- The cached reveal alpha/blur are reset to the neutral state (`1f` / `0`), preventing a newly-created presentation from inheriting stale values from the previous gesture.

## Reusable glass view teardown

- `IosShadeGlassView.reset()` now stops the optical sampler, releases any remaining native material state, and clears the previous root, sheet offset, corner radius, and clip dimensions.
- The glass and its backdrop return to alpha 1 before being hidden, so a later attachment cannot inherit a partially faded state from a previous wallpaper gesture.
- This is lifecycle hardening only; it does not mount a glass overlay or claim that the target ROM's optical APIs have been verified.

## Standalone APK metadata packaging

- The standalone module packages its libxposed registration files from `src/main/resources/META-INF/xposed/`: `java_init.list`, `module.prop`, and `scope.list`.
- Removed the catch-all resource exclusion from the standalone module's Gradle packaging block. Keeping only the Xposed metadata merge rule avoids excluding the very files required for module entry registration and scope discovery.
- The APK still needs a real Gradle build and archive inspection before packaging can be considered verified.

## Fault-isolated SystemUI hook startup

- Optional blur, material, and clear-gesture observers are initialized independently. A missing OEM class or hidden API now logs its own failure without aborting the core shade gesture observers.
- The module entry point also isolates the gesture, notification-stack, and presentation-lifecycle hook families. A failure in one family no longer prevents the other families from attempting installation.
- This is a startup resilience improvement; successful installation on a specific HyperOS build still requires device-side LSPosed logs.

## Core observer activation gate

- The shade controller is no longer marked enabled before native hook registration is attempted.
- Expansion, panel-switch, and panel-ownership observer groups report independent installation outcomes.
- The feature becomes active only if at least one core native state observer was registered; otherwise presentation updates remain gated and the native UI is restored instead of being claimed by an inert controller.
- A consolidated status log records which observer groups installed. Optional blur/material/clear hooks do not count as core state sources.

## Notification animation hook isolation

- Notification entry/exit animation observers are initialized independently inside the stack-hook family.
- If an OEM animation class or method changes and animation hook setup fails, the core stack layout, clipping, scroll-range, and positioning observers can still initialize.
- The failure is logged as an optional animation-hook degradation rather than aborting the entire stack family.


## Notification exit background ownership

- Exit-frame snapshots now retain the owning notification row alongside each captured view. This lets the exit holder restore the row's normal background view even when that background is not itself classified as an expandable notification row by the bridge.
- Restoration remains gated by the owning row still belonging to the active presentation, so detached/recycled rows are not written back into a newly-owned stack.
- This fixes the mismatch between capturing background views and the previous restore-time `ownsRow(background)` check. ROM-specific drawing behavior still requires device verification.


## Weak snapshot reference safety

- The exit snapshot map uses weak View keys. Snapshot values now keep the owning notification row through `WeakReference` as well, avoiding a value-to-key strong-reference cycle that could defeat WeakHashMap collection for notification-row entries.
- Restore skips snapshots whose owner row has already been collected or no longer belongs to the active presentation. Background snapshots still validate against their owning row.


## Snapshot owner validation stability

- Exit restoration resolves the weak owner-row reference once into a local strong reference before checking Bridge ownership. This avoids two separate weak-reference reads producing inconsistent results if collection occurs between reads.
- The temporary strong reference exists only for the duration of that restore call; snapshot values continue to avoid retaining rows for the lifetime of the weak-key map.


## libxposed API 102 entry class loader

- `XposedModule.onPackageLoaded` receives `PackageLoadedParam`, whose API 102 class-loader accessor is `getDefaultClassLoader()`.
- `getClassLoader()` belongs to `PackageReadyParam`; calling it from `onPackageLoaded` does not compile against API 102. The standalone module entry now uses `param.getDefaultClassLoader()` before installing SystemUI hooks.
- The entry class remains a public no-argument `XposedModule` subclass, matching the API's module-entry contract.


## Standalone libxposed APK metadata

- The standalone module packages `META-INF/xposed/java_init.list`, `module.prop`, and `scope.list`; its Gradle resource packaging merges the `META-INF/xposed/*` entries, matching the official libxposed example pattern.
- `java_init.list` points to the public no-argument `IosNotificationCenterModule`, and the static scope is limited to `com.android.systemui`.
- `module.prop` is kept to the documented core keys (`minApiVersion`, `targetApiVersion`, `staticScope`, and `autoHotReload`). The user-facing description remains in the Android manifest rather than an extra, undocumented property.
- The module uses `compileOnly` for libxposed API 102 and does not require the optional libxposed service dependency for its current hook-only implementation.
