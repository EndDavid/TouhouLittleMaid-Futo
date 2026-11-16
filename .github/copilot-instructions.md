# 仓库约定（Copilot 必读）

本仓库运行在 IntelliJ IDEA 2025.2 + **Git Bash (MSYS2)** 下。下面三条是硬约束：
每违反一次，都会产生一次需要人工善后的事故。

## 1. 终端：Git Bash，不是 PowerShell

- 默认 shell 已从 Windows PowerShell 5.1 换成 `C:\Program Files\Git\bin\bash.exe`。
  不要再发 `Select-String`、`Test-Path`、`Set-Content`、`Get-ChildItem` 这类 cmdlet，bash 里没有。
- 禁止 `sleep N`、`while :; do ... done` 这类无输出的等待。
  原因：插件靠“输出停止变化 + 提示符出现”判定命令结束，而这里的提示符检测不稳定，
  无输出命令容易被判成“仍在运行”，表现就是终端卡死。
- 需要等构建或下载时，轮询可见产物：
  `test -f build/libs/xxx.jar`、`grep -q 'BUILD SUCCESSFUL' build/build.log`。
- 一条命令只做一件事；预计输出超过 100 行，先 `> /tmp/step.log 2>&1` 再 `grep` / `tail`。
- 路径用 MSYS 形式 `/d/codename_EndDavid/hello java/...`，含空格的路径加引号；
  传给 Windows 原生程序、且参数以 `/` 开头时，前置 `MSYS_NO_PATHCONV=1`。
- 构建命令：`./gradlew.bat build`（或 `cmd //c gradlew.bat build`），不要写 `gradlew`。
- 判断成败只看 `$?` 和最后 10 行，不要被整段历史回显带偏。

## 2. 改文件：最小替换，禁止整文件重写

- 只用最小范围的替换，不要输出整个文件。
- 以下文件超过 1000 行或 40K token，整文件改写会被编辑模型截断，
  表现就是“一下删掉一大片”：
  - `src/main/java/com/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid.java`（约 3300 行）
  - `src/main/resources/assets/touhou_little_maid/lang/zh_cn.json`
  - `src/main/resources/assets/touhou_little_maid/lang/en_us.json`
  - `README.md`
- 对它们：先只读需要的那一段，一次只改一个 hunk，改完立刻 `git diff --stat` 自查。
- 不要顺手重构、不要重排 import、不要改行尾：本仓库按字节管理（`.gitattributes` 里 `* -text`，`.java` 是 CRLF）。

## 3. 编码：一律 UTF-8

- 仓库所有文本文件是 UTF-8（无 BOM），禁止产生 GBK 字节。
- 在 bash 里 `printf '%s\n' '中文' > file`、`cat > file <<'EOF'` 写的都是 UTF-8 字节，安全。
- 仍然禁止用 Windows PowerShell 5.1 的 `>`、`Set-Content`、`Out-File` 写含中文的内容：它默认写 GBK。
  万不得已要用 PowerShell 写文件时：

  ```powershell
  [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
  ```

- `.ps1` 若还要保留：一律纯 ASCII；必须含中文时给文件加 UTF-8 BOM。
  PS 5.1 会把无 BOM 的脚本按 GBK 解析，字面量在执行之前就已经损坏。
- 收尾自检：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./check-encoding.ps1`，输出必须为 0。

## 4. 语言
一律用中文回复用户。


## 5. 进程
杀死冒烟测试的进程时请勿误伤其他 java.exe 进程。
