# iOS Notification Center Porting Map

Branch: adapt/xiaomi-17-pro-max

## Current standalone project

The separate Gradle project is included as :ios-notification-center. Its application ID is btm.m.os4.iosnotificationcenter, with a libxposed API 102 entry point and SystemUI-only static scope.

The new module has its own Xp helper. It is adapted from the original reflection/hook helper, but its logger now uses Android Log and it has no imports from the original HyperChanger package. The entry point attaches this helper only after the SystemUI package callback.

The module is deliberately inert at this checkpoint: it does not install hooks yet.

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
