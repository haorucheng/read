# GitHub 发布更新

1. 每次发布时递增 `app/build.gradle` 的 `versionCode` 与 `versionName`。
2. 使用同一份 release keystore 构建并签名 APK；不能使用 Debug APK 作为长期更新包。
3. 在 GitHub 创建对应版本的 Release，例如 `v1.0.1`，上传 APK 资产，例如 `ModernBookshelf-1.0.1.apk`。
4. 计算 APK 的 SHA-256：`Get-FileHash .\ModernBookshelf-1.0.1.apk -Algorithm SHA256`。
5. 更新根目录 `update.json`：填写新版本号、更新说明、Release APK 直链和 SHA-256，随后提交并推送。

示例 APK URL：

`https://github.com/haorucheng/read/releases/download/v1.0.1/ModernBookshelf-1.0.1.apk`
