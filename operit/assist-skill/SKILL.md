---
name: assist-remote
description: 处理远程控制端（经 Operit 外部 HTTP API 发来）的委托任务：在 App 里浏览、读页面、汇报结构化结果。凡消息以「[远程委托]」开头，先读本 skill。
---

# 远程委托任务规范（Operit skill 示例）

远程控制端通过 `http://<本机>:8094/api/external-chat` 给你派活，让你在本机直接操作界面（比远程传截图快）。

## 1. 硬性禁止
- **绝不提交订单、绝不付款**；不输入密码、验证码、银行卡/支付信息。确认页只读，看完按返回离开。
- 不发消息、不评论、不点赞、不关注、不改任何 App 或系统设置。
- 不改你自己的配置，不动 VPN/Tailscale、无线 ADB、`com.astra.takeover`。
- 遇到登录页、验证码、滑块、风控页：**立即停止**并报告，不要尝试绕过。

## 2. 读屏与操作：用接管 App 的界面树（root 广播）
```sh
am broadcast -n com.astra.takeover/.Ctl -a com.astra.takeover.DUMP --ez inline false >/dev/null; F=/data/data/com.astra.takeover/files/ui.txt
grep -aE '"[^"]+"' $F | head -120
```
- 每行：`路径 类名 "文字" d="描述" #id [左,上,右,下] 标记`（C=可点 SCR=可滚动 E=可输入 SEL=已选中）；`W` 开头是窗口行。只读焦点窗口加 `--es mode active`。
- 按文字点击：`am broadcast -n com.astra.takeover/.Ctl -a com.astra.takeover.CLICK --es text "按钮文字"`（锁屏时会被拒绝）。
- 滚动：`input touchscreen swipe <x> 900 <x> 350 400`，再 DUMP（v2.4 起每次 DUMP 都清缓存，内容是新的）。
- 不要用 `uiautomator dump`；除非界面树里没有需要的文字，否则不要截图给视觉模型。
- 设步数上限（例如整个任务 30 步），超了就按 partial 返回。

## 3. 返回格式
最后一条回复只给 JSON：`{"status":"ok|blocked|partial","items":[...],"problems":"无"}`。数字照页面抄，看不清写 null，不要编。
