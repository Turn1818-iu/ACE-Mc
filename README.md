# AntiCheatExpert v1.5.0

高效的 Fabric 服务器端反作弊 mod，支持自适应检测速度、ban 系统、白名单、管理员管理和登录系统。

**支持版本**：Minecraft 1.16 ~ 1.26.1（Fabric）

## 新功能 (v1.5.0)

### 反作弊检测
- **超过 40+ 主流外挂客户端检测**：
  - Wurst、Vape、Cortex、LiquidBounce
  - Zephyr、Meteor、HORION、Future
  - Ares Client、Alpine Client、Onix Client
  - 以及其他众多已知外挂...
- **自适应检测速度**：基于服务器性能自动调整，最快 5ms，最慢 500ms
  - 服务器卡顿时自动减速
  - 服务器空闲时自动加速
- **登陆后 1,354,312 年封禁**

### Ban 管理

**自定义 Ban 指令**：`.ban <player> <time>`
- 时间格式示例：
  - `1s` = 1 秒
  - `30m` = 30 分钟
  - `1h` = 1 小时
  - `7d` = 7 天
  - `1w` = 1 周
  - `1y` = 1 年

**解封指令**：`.unban <player>`

### 白名单系统
- 添加：`.withplayer add <player>`
- 移除：`.withplayer remove <player>`
- 查看：`.withplayer list`

### 管理员系统
- 添加：`.op add <player>`
- 删除：`.op remove <player>`
- 检查：`.op list`

### 登录系统
- 注册：`/register <password> <password>`
- 登录：`/login <password>`

## 文件说明
- `src/main/java/com/anticheatexpert/AntiCheatExpertMod.java` - 主 mod 代码
- `src/main/resources/fabric.mod.json` - Fabric mod 元数据
- `build.bat` - Windows 构建脚本

## 使用方法
1. 将 `AntiCheatExpert-1.5.0.jar` 复制到服务器的 `mods` 文件夹
2. 使用 Fabric Loader 启动服务器
3. 查看 `AntiCheatExpert-detections.log` 日志文件

## 说明
- 该 mod 自动检测已知外挂客户端并基于性能自适应调整检测速度
- 支持灵活的 ban 时间指定
- 白名单玩家不会被自动 ban （但可以被 .ban 命令 ban）
- 所有 ban 记录和数据保存到 `AntiCheatExpert-data.json`
