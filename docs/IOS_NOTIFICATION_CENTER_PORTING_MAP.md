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
