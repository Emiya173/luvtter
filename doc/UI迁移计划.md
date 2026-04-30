# UI 迁移计划:webui → Compose Multiplatform

**生成时间**:2026-04-29
**对照来源**:`webui/`(React + 内联 SVG + CSS,2490 行)
**迁移目标**:`composeApp/src/commonMain/`(Compose Multiplatform,Desktop 优先,Android/iOS 复用)

---

## 0. 设计基调速记

`webui` 的视觉核心是「纸 + 墨 + 火漆」的中文文学化书信:

- **色彩**:6 档暖纸(`paper / paper-raised / paper-deep / paper-edge`)+ 4 档墨(`ink / ink-soft / ink-faded / ink-ghost`)+ 火漆红(`seal / seal-dark / seal-glow`)+ 邮戳青(`stamp-ink`)
- **字体**:中文衬线(Noto Serif SC)主角 / 西文衬线(Cormorant Garamond)做品牌 / 楷书(Ma Shan Zheng)做手写 / 宽松(Liu Jian Mao Cao)做装饰 / 等宽(JetBrains Mono)做 meta 标签
- **质感**:`paper-grain`(三层径向噪点 multiply)、`deckle`(纸边赭色渐变)、`paper-sheen`(右上暖光)
- **几何**:0.5px 细线分隔、0.04em–0.14em 字距、`rotate(-0.4deg ~ 0.5deg)` 抖动表达「手写感」
- **动效**:12 段 keyframes —— `letter-drop` / `flap-open` / `seal-press` / `postmark-roll` / `envelope-slide` / `shard-fly-{1..4}` / `ink-bleed` / `nib-cursor` / `chime-pulse` / `fold-back` / `drift` / `fade-up` / `ink-flow`

迁移要把这套质感**完整搬到 Compose**,不是「换主题色」级改动。

---

## 1. 改动范围

### 1.1 webui 已覆盖的屏(对应客户端现状)

| webui | 现 composeApp | 备注 |
|---|---|---|
| Inbox(信封 stack) | `HomeScreen` 的 Inbox tab | 当前是 `LazyColumn<LetterRow>`,需整体重写为信封 thumbnail stack |
| Compose(写信) | `ComposeScreen.kt` | 当前是 Material3 表单,要重写为信纸 + 侧栏工具 + 寄出动画 |
| Reading(火漆拆封 + 信纸阅读) | `LetterDetailScreen.kt` | 当前是 Material3 卡片列表,要加拆封动画 + 信纸版式 |
| Correspondence(往来堆叠) | 无 | 全新屏,Sidebar 联系人列表点入即进 |
| Outbox(在途路线图) | `HomeScreen` 的 Outbox tab | 当前同 Inbox 平铺,要加 SVG 路线图 + 信封漂浮 |
| Sidebar 240px(品牌 + 导航 + 联系人 + 页脚) | `HomeScreen` 顶栏 + Tabs | 整改导航布局,从顶部 tab 切到左侧栏 |

### 1.2 webui 未覆盖、需按 P1 token 重新对齐的屏

`LoginScreen` / `RegisterScreen` / `AddressesScreen` / `ContactsScreen` / `SessionsScreen` / Notifications 抽屉 / Folders / Onboarding 一次性卡片 / Daily reward 提示条 / Export 状态条 / 当前地址切换面板。

这些屏只走 P1 设计 token 重新排版,不重写交互。

### 1.3 已实现但 webui 没覆盖的功能

- 注册 / 登录 / handle finalize 引导
- 多地址 CRUD、当前位置切换
- 联系人查找 / 屏蔽 / only_friends
- Sessions 列表、撤销
- 通知列表 + Quiet Hours + SSE 心跳
- 分类 / 收藏 / 全文搜索对话框
- 每日奖励 / 导出 / Onboarding "给未来的自己" 卡片

这些保留现有信息架构,**只换皮不换骨**。

---

## 2. 资产前置

### 2.1 字体

打包以下 5 套到 `composeApp/src/commonMain/composeResources/font/`(用 Compose Resources `Font(Res.font.xxx)`):

| 字体 | 文件 | 用途 |
|---|---|---|
| Noto Serif SC | `NotoSerifSC-{Light,Regular,Medium,SemiBold,Bold}.ttf` | 正文中文衬线(`--serif-zh`) |
| Cormorant Garamond | `CormorantGaramond-{Regular,Medium,SemiBold,Italic}.ttf` | 西文衬线 / 品牌(`--serif-en`) |
| Ma Shan Zheng | `MaShanZheng-Regular.ttf` | 楷书 / 手写(`--hand-zh`) |
| Liu Jian Mao Cao | `LiuJianMaoCao-Regular.ttf` | 装饰手写 |
| JetBrains Mono | `JetBrainsMono-{Regular,Medium}.ttf` | 等宽 meta(`--mono`) |

来源全是 Google Fonts / OFL,直接 `wget` 收 ttf。**许可保留**(SIL OFL)文件随附 `LICENSE.txt`。

### 2.2 设计 token

新建 `composeApp/src/commonMain/kotlin/com/luvtter/app/theme/`:

- `LuvtterTokens.kt` —— 把 CSS 变量逐个落成 Kotlin `Color` / `Dp` / `TextStyle` 值,挂到 `CompositionLocal<LuvtterTokens>`。颜色名保留 `paper / paperRaised / paperDeep / paperEdge / ink / inkSoft / inkFaded / inkGhost / rule / ruleSoft / seal / sealDark / sealGlow / stampInk`。间距 `s1..s8` = 4 / 8 / 12 / 16 / 24 / 32 / 48 / 64 dp。
- `LuvtterTheme.kt` —— Material3 `ColorScheme` 把 `surface=paperRaised`、`background=paper`、`onSurface=ink`、`primary=seal`、`secondary=stampInk` 映射;`Typography` 把 6 档(`txtTitle / txtMeta / txtCaption / body / handwriting / brand`)按字体栈接好。整个 App 入口(`App.kt`)用 `LuvtterTheme { ... }` 包裹。
- 现有屏的 `MaterialTheme.colorScheme.*` 引用先全部跑通(只改 token,不改结构),作为 P1 验证手段。

### 2.3 Modifier / 工具

- `Modifier.paperGrain()` —— `drawWithCache`,三层径向噪点用预生成的 `ImageBitmap` 缓存(避免每帧重算),按 `BlendMode.Multiply` 叠在内容上。
- `Modifier.deckle()` —— 上下左右 8% 渐变内边,模拟手撕纸。
- `Modifier.paperSheen()` —— 右上 60%×40% 椭圆渐变,`BlendMode.Screen`。
- `rememberHairline()` / `Modifier.hairBorder()` —— 0.5dp 实线 / 软线,色用 `tokens.rule` / `tokens.ruleSoft`。

---

## 3. 共享组件(Compose Canvas / Path 重画 SVG)

每个组件一个文件,放 `composeApp/src/commonMain/kotlin/com/luvtter/app/ui/letter/`:

| 组件 | 输入 | 实现 |
|---|---|---|
| `Postmark(city, date, size, rotateDeg)` | `String, String, Dp, Float` | `Canvas` + `drawArc` + `drawText` 沿圆弧。CJK 沿弧排字 Compose 不原生支持 → 用 `androidx.compose.ui.graphics.Path` + `pathSegments` 手算每字位移。 |
| `Stamp(stamp, size)` | `StampSpec, Dp` | 邮票边框 + 内框 + 抽象图形 + `LUVTTER` / `name` / `name_en` 文字 + 四边齿孔(`drawCircle` 阵列)。 |
| `WaxSeal(text, size, broken)` | `String, Dp, Boolean` | `RadialGradient` 圆 / 八边碎裂形 + 中央汉字。`broken=true` 用 `Path` 走八边形。 |
| `EnvelopeThumb(letter, contact, opened)` | DTO | 信封纸 + flap 三角线 + 地址栏 + 邮票 + 邮戳 + 未拆时底部火漆。 |
| `RouteMap(progress)` | `Float` | `Canvas` + `Path.cubicTo` 画曲线 + `pathEffect = dashPathEffect` + 起止点 marker。 |
| `EnvelopeIcon(size)` | `Dp` | 在 `Outbox` 路径上漂浮的小信封,`drift` 动画。 |

`webui/data.jsx` 的 `STAMPS` / `STATIONERY` 表整体搬到 `composeApp/.../letter/Specs.kt`。

---

## 4. 阶段分解

### P1 — 设计系统打底(无回归)

**改动**:`theme/LuvtterTokens.kt` + `theme/LuvtterTheme.kt` + 5 套字体资源 + `paperGrain` / `deckle` / `paperSheen` Modifier。`App.kt` 顶层包 `LuvtterTheme`。所有现存屏只改 `MaterialTheme.colorScheme.*` 引用,不动布局。

**交付**:Desktop 跑起来,所有屏的底色变成 `paper`(暖米),墨色变成 `ink`(深棕黑),按钮主色变成 `seal`(暗红)。视觉上像「换了一身衣服」但导航/交互没变。

**验证**:`./gradlew :composeApp:run` 截图前后对比,所有 happy path(登录 → 写 → 寄 → 读)走通。

### P2 — 共享 SVG 组件 + 沙盒屏

**改动**:六个 letter 组件 + `LetterPlaygroundScreen`(临时屏,只在 dev 时挂到一条隐藏路由,展示组件矩阵)。

**交付**:能在沙盒屏看到 Postmark / Stamp / WaxSeal / EnvelopeThumb / RouteMap 五种组件的全部状态(opened/sealed,broken/intact,各档 stamp 颜色,各档 progress)。

**验证**:沙盒屏肉眼对比 webui 浏览器渲染,差异 < 10%。

### P3 — Inbox 重写(首个完整屏)

**改动**:`HomeScreen` Inbox tab → 拆为 `InboxScreen.kt`(独立屏)。
- 信封 stack + 旋转抖动(`rotate(-0.4deg)` 间隔)
- 最新未拆信件首次展示时跑 `letter-drop` 动画(1300ms)+ 落地阴影脉动
- 顶部 chime 红点闭环 3 次
- `EmptyFoot`「邮差还在路上」分隔
- 点击信封 → 进 Reading,带过场动画

**交付**:Inbox 屏完全 webui 同款。

**验证**:登录 → 收件箱 → 看到落信动画 → 点开第一封 → 进 Reading(P3 阶段先用占位)→ 返回 → 再点。

### P4 — Reading(火漆拆封 + 信纸版式)

**改动**:`LetterDetailScreen` → `ReadingScreen.kt`。两段:
1. **UnsealView**:信封 + 火漆 SVG,点火漆 → `phase=opening`(2400ms):flap `rotateX(0→175deg)` + 火漆 `scale + opacity → 0` + 4 片碎屑飞射 + 残留漆渍 + 信纸 `translateY(0 → -18%)`。
2. **LetterPaper**:`stationery.tint` 背景 + 横格/方格/无规则三档纹理 + 顶部 postmark 区 + 正文 + 底部 wax sign-off。返回时跑 `fold-back` 动画(850ms)再 `onBack()`。

**交付**:`opened=false` 的信件第一次进入跑完整拆封;`opened=true` 直接进信纸视图。

**验证**:Inbox → 点未拆信 → 火漆碎裂 → 信纸出现 → 阅读 → 「放回」→ 折回 stack。

### P5 — Compose(写信 + 寄出动画)

**改动**:`ComposeScreen` 整体重写。
- 双栏:左信纸(`stationery.tint` + 纹理 + 透视阴影)/ 右 200px 工具栏(stationery / font / stamp / weight)
- `InkBleedTextarea`:Compose 用 `BasicTextField` + 同步镜像 `Text`,新增字符触发 `ink-bleed` 380ms 的 alpha + blur 动画。Compose 没有 `filter: blur()` —— 用 `Modifier.blur` 配合 `animateFloatAsState` 模拟。
- 笔尖游标:右上 `✒` 跑 `nib-cursor`(2s 周期 alpha 闪烁)
- 寄出按钮 `封缄 · 寄出` → `phase: writing → sealing → stamping → sent`,共 3800ms 动画:flap 折下 → wax press 按下 → postmark 滚入 → envelope 整体右上滑出。
- 完成后跳 Outbox。

**交付**:写信屏完全 webui 同款,寄出动画连贯。

**验证**:写一封测试信 → 选 stamp / stationery → 点寄出 → 看完动画 → 自动跳到 Outbox 看到刚寄的那封。

### P6 — Outbox + Correspondence + Sidebar

**Outbox**:`HomeScreen` Outbox tab → `OutboxScreen.kt`。每封在途信渲染:
- 顶部「自 → 致 · ETA」三段式
- 中间 `RouteMap`(96dp 高,曲线 + 进度 + 起止 marker + 信封漂浮)
- 底部 stamp + stage label + 时间

**Correspondence**:`CorrespondenceScreen.kt`,堆叠信件视图。
- 每封 EnvelopeThumb 间距 -40dp(重叠)+ 旋转 ±0.8deg + hover 抬升
- 收信靠左、寄出靠右(`dir==in` ml=0,`dir==out` mr=0)
- 底部小字「subject」字幕

**Sidebar**:`AppSidebar.kt`,240dp 宽。三层:
1. 品牌 luvtter(italic Cormorant)+「慢 · 一 · 拍」副标
2. 主导航(收 / 写 / 寄,左侧 1.5dp 火漆色高亮条)
3. CORRESPONDENCE 列表(头像 + 姓名 + 关系 · 信件数)
4. 底部「今日宜写信 · 甲辰年腊月十四」

`App.kt` 主导航从 `BottomNavigation` / `TabRow` 切到 `Row { Sidebar; MainContent }`。

**交付**:三屏 + Sidebar 完成,五屏闭环。

### P7 — 配套屏调整

按 P1 token 复检 `LoginScreen` / `RegisterScreen` / `AddressesScreen` / `ContactsScreen` / `SessionsScreen` / Notifications 抽屉 / Folders 卡片 / Onboarding 卡片 / Daily reward 提示条 / Export 条。

每屏的工作:
- 移除 `Card` 厚阴影(webui 全是 0.5px hairline + 多层薄阴影)
- 字体改为 `tokens.txtTitle / txtMeta / txtCaption`
- Button 用 `LuvtterButton(variant=Primary/Ghost/Seal)` 三档(对应 webui `.btn / .btn.ghost / .btn.seal`)
- 输入框改为下划线式(`borderBottom: 0.5px dashed rgba(120,90,40,0.25)`)

**交付**:全应用视觉统一在 webui 体系内。

---

## 5. 落地顺序与回归面

| 阶段 | 估时 | 风险 | 阶段产物可独立合 PR? |
|---|---|---|---|
| P1 | 0.5 天 | 低 —— 只换 token | ✅ 可单独合 |
| P2 | 1 天 | 中 —— SVG → Canvas 转换有像素级差异 | ✅(沙盒屏暂不接路由,合并不影响用户) |
| P3 | 1 天 | 中 —— 落信动画首次出现 | ✅(Inbox 替换完即用) |
| P4 | 1.5 天 | 高 —— 拆封动画状态机复杂 | ⚠️ 跟 P3 一起合更稳 |
| P5 | 2 天 | 高 —— ink-bleed + 寄出多段动画 | ✅ |
| P6 | 1 天 | 中 —— RouteMap 曲线 + Sidebar 重排 | ✅ |
| P7 | 0.5–1 天 | 低 —— 纯样式 | ✅ |

**回归面**:每阶段开始前跑一遍 happy path(登录 → 写 → 寄 → 读 → 通知 → 设置 → 退出);P3 之后增加「拆封动画跑完 / 跳过」测试;P5 之后增加「寄出动画 + 状态切换」测试。

---

## 6. 现有逻辑对接点

迁移**不动**服务端 / DTO / VM / 路由层。需要核对的对接点:

- `HomeViewModel.state.letters: List<LetterSummaryDto>` —— 直接喂给 `EnvelopeThumb`
- `LetterSummaryDto` 需要的字段:`sender / recipient / subject / preview / stamp(?) / stationery(?) / sentAt / opened`。**注意**:目前 DTO 没有 `stamp` / `stationery` 字段(只在草稿创建时存在),需要在 `letters` 表加列或在 `letter_contents` 上读出来。**若不想改 schema,P3 阶段先用一个固定 fallback(`stamp=airmail`,`stationery=cream`),后续在 P5 写信时把字段写回**。
- `EnvelopeThumb` 用的 `contact.initial / avatar_tone` 需要从 `UserDto.handle` 推导(取首字 + hue 哈希)
- 拆封动画依赖客户端「是否首次打开」状态:用 `LetterDetailScreen.markRead` 调用前后差判断,或在客户端本地 SQLDelight(未实装)缓存。**P4 先用「is markedRead 之前进入」近似**

---

## 7. 不在本次迁移范围

- 拼图 / 涂鸦 / 真笔迹矢量画板(Stage 4 路线项)
- 信纸自定义上传 / 邮票限定款商店(V2)
- Web (Wasm) target(路线图长期项)
- Android / iOS 体验适配(Android scaffold 已就绪,P1–P7 完成后单独再过一遍 touch / IME / 系统栏)

---

## 8. 启动建议

从 **P1** 开始(0.5 天的「换皮」先把整个应用调到 webui 调子),验完再走 P2 → P3。每完成一个 P,在本文件末尾附「✅ 完成于 yyyy-mm-dd」+ 实际工时,作为后续阶段调整的参考。

```
- P1 ✅ 完成于 2026-04-29(主题 + 三 Modifier + 13 个 ttf 字体已挂入 `composeApp/src/commonMain/composeResources/font/`)
- P2 ✅ 完成于 2026-04-29(Specs + Postmark/Stamp/WaxSeal/EnvelopeThumb/RouteMap/EnvelopeIcon 六组件 + LetterPlaygroundScreen,LoginScreen 临时入口"↗ 组件沙盒")
- P3 ⬜
- P4 ⬜
- P5 ⬜
- P6 ⬜
- P7 ⬜
```
