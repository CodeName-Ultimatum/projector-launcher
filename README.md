# TV Launcher

Android TV 桌面启动器，支持网络 API 配置卡片与离线兜底。

## 项目结构

```
app/src/main/java/com/example/tvlauncher/
├── MainActivity.kt          # 主 Activity：数据加载、卡片绑定、面板动画
├── data/                    # 数据层
│   ├── ApiCardDataSource.kt # 从后端 data.json 拉取配置
│   ├── FileCardDataSource.kt# 本地文件数据源
│   ├── LocalCardDataSource.kt# 离线默认数据源
│   ├── LauncherDataParser.kt# JSON 解析器
│   ├── AppRepository.kt     # 应用信息仓库
│   └── QuickAppsStore.kt    # 快捷应用持久化
├── ui/                      # 自定义视图
│   ├── LauncherCardView.kt  # 卡片视图（聚焦放大、图片加载）
│   ├── AppPanelView.kt      # 应用面板（两排网格，添加/移除快捷栏）
│   ├── QuickBarView.kt      # 底部快捷栏
│   └── StatusBarView.kt     # 顶部状态栏
└── util/                    # 工具类

app/src/main/res/layout/
├── activity_main.xml        # 主布局：状态栏 + 卡片区 + 快捷栏 + 面板
```

## 数据流

1. `MainActivity` 启动时选择数据源：
   - 联网模式：`ApiCardDataSource(apiUrl)` 请求后端 `data.json`
   - 失败/离线：依次回退到本地快照 → 内置默认配置
2. `LauncherDataParser` 将 JSON 解析为 `LauncherData`（含 `config` 和 `modules`）
3. `MainActivity.bindCardsFromLauncherData()` 把 `modules` 里的应用绑定到 9 个固定卡片槽位
4. 卡片图片通过 Glide 加载 `iconUrl` / `iconBgUrl`；点击行为优先 `intents`，其次 `packageName`

## API 格式

后端接口返回 JSON 示例结构：

```json
{
  "config": {
    "screenColor": "#FF373778",
    "lightMode": false,
    "smallIcon": false,
    "displayDesc": false,
    "displayHead": false,
    "displayTitle": false
  },
  "modules": [
    {
      "moduleName": "推荐",
      "sort": 0,
      "productGroups": [
        {
          "groupName": "影音",
          "sort": 0,
          "groupApps": [
            {
              "appName": "腾讯视频",
              "packageName": "com.tencent.qqlive",
              "iconUrl": "https://example.com/icon.png",
              "iconBgUrl": "https://example.com/banner.png",
              "intents": "OPEN_VIDEO",
              "isCheckVer": 1,
              "versionCode": 12345
            }
          ]
        }
      ]
    }
  ]
}
```

字段说明：
- `config.screenColor`：主题背景色（`#RRGGBB`）
- `config.lightMode` / `smallIcon` / `displayDesc` / `displayHead` / `displayTitle`：全局显示开关
- `modules[].moduleName` / `sort`：模块名称与排序
- `productGroups[].groupName` / `sort`：产品组名称与排序
- `groupApps[]`：应用列表，每张卡一个
  - `appName`：应用名
  - `packageName`：点击启动的包名
  - `intents`：内置行为（`FILE_MANAGER`、`SETTINGS` 等），优先于 `packageName`
  - `iconUrl`：小图标 / `iconBgUrl`：banner 大图
  - `isCheckVer` / `versionCode`：版本检查标志
  - `config`：卡片内嵌配置对象（`top/left/width/height/behavior/displayName`）

## 面板布局

- 主屏幕：状态栏（72dp）+ 卡片区 + 快捷栏（86dp）
- 应用面板平时被卡片区覆盖，点击快捷栏“+”后卡片区整体上移露出面板
- 面板为两排正方形格子，格宽按 16:9 屏幕比例自适应，聚焦放大后白框贴边
- 当前面板内边距：上下 2dp，左右 4dp（与屏幕长宽比成比例）

## 构建

```bash
./gradlew installDebug
```

## 配置 API URL

在 `MainActivity.kt` 中修改 `CARD_API_URL`：

```kotlin
private const val CARD_API_URL = "https://your-domain/path/data.json"
```
