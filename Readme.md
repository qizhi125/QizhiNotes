# 随笔 (Qizhi Notes)

## 简介

**随笔 (Qizhi Notes)** 是一款简洁实用的 Android 笔记应用，旨在帮助用户随时随地记录想法、灵感和重要信息。用户可以创建包含标题、内容和可选图片的笔记，并进行管理。应用还提供了用户注册和登录功能，方便用户保存和管理个人笔记。

## 功能特点

* **创建和编辑笔记**：轻松创建包含标题和内容的笔记。
* **图片附件**：可以为笔记添加来自相机或图库的图片。
* **查看和管理笔记**：在主界面以列表形式查看所有笔记，并支持点击查看详情和长按删除。
* **用户认证**：支持用户注册和登录，保护个人笔记。
* **记住密码**：提供“记住密码”功能，方便用户下次登录。
* **数据存储**：使用本地 SQLite 数据库存储笔记数据。

## 截图

登录界面

![image-20250416155742576](/home/qizhi/Documents/Typora Note/Android_Test/登录界面.png)

注册界面

![image-20250416155835471](/home/qizhi/Documents/Typora Note/Android_Test/image-20250416155835471-1744790318663-2.png)

主界面

![image-20250416155852864](/home/qizhi/Documents/Typora Note/Android_Test/image-20250416155852864-1744790337352-4.png)

添加界面

![image-20250416155914691](/home/qizhi/Documents/Typora Note/Android_Test/image-20250416155914691-1744790357398-6.png)

添加后主界面效果

![image-20250416155927272](/home/qizhi/Documents/Typora Note/Android_Test/image-20250416155927272-1744790372141-8.png)

## 目录结构

```bash
├── AndroidManifest.xml
├── ic_launcher-playstore.png
├── java
│   └── com
│       └── qizhi
│           └── qizhi_notes
│               ├── adapter
│               │   └── MemoAdapter.java
│               ├── AddInfoActivity.java
│               ├── bean
│               │   └── MemoBean.java
│               ├── db
│               │   └── MyDbHelper.java
│               ├── LoginActivity.java
│               ├── MainActivity.java
│               └── RegisterActivity.java
└── res
    ├── drawable
    │   ├── add_icon.png
    │   ├── background.png
    │   ├── buttonbg.png
    │   ├── edittext_background.xml
    │   ├── ic_add_24.xml
    │   ├── ic_broken_image_24.xml
    │   ├── ic_camera_alt_24.xml
    │   ├── ic_default_image.png
    │   ├── ic_error.png
    │   ├── ic_image_24.xml
    │   ├── ic_launcher_background.xml
    │   ├── ic_launcher_foreground.xml
    │   ├── icon.png
    │   ├── ic_photo_library_24.xml
    │   ├── ic_placeholder.png
    │   ├── ic_save_24.xml
    │   ├── savebg.png
    │   └── sunshine.png
    ├── layout
    │   ├── activity_add_info.xml
    │   ├── activity_login.xml
    │   ├── activity_main.xml
    │   ├── activity_register.xml
    │   └── recy_item.xml
    ├── mipmap-anydpi-v26
    │   ├── ic_launcher_round.xml
    │   └── ic_launcher.xml
    ├── mipmap-hdpi
    │   ├── ic_launcher_foreground.webp
    │   ├── ic_launcher_round.webp
    │   └── ic_launcher.webp
    ├── mipmap-mdpi
    │   ├── ic_launcher_foreground.webp
    │   ├── ic_launcher_round.webp
    │   └── ic_launcher.webp
    ├── mipmap-xhdpi
    │   ├── ic_launcher_foreground.webp
    │   ├── ic_launcher_round.webp
    │   └── ic_launcher.webp
    ├── mipmap-xxhdpi
    │   ├── ic_launcher_foreground.webp
    │   ├── ic_launcher_round.webp
    │   └── ic_launcher.webp
    ├── mipmap-xxxhdpi
    │   ├── ic_launcher_foreground.webp
    │   ├── ic_launcher_round.webp
    │   └── ic_launcher.webp
    ├── values
    │   ├── colors.xml
    │   ├── ic_launcher_background.xml
    │   ├── strings.xml
    │   └── themes.xml
    ├── values-night
    │   └── themes.xml
    └── xml
        ├── backup_rules.xml
        ├── data_extraction_rules.xml
        └── provider_paths.xml
```
## 技术栈

* **Android SDK**
* **Java**
* **SQLite** (用于本地数据存储)
* **RecyclerView** (用于显示笔记列表)
* **FileProvider** (用于安全地共享相机拍摄的图片)
* **Android Activity Result API** (用于处理相机和图库的返回结果)
* **SharedPreferences** (用于存储用户偏好设置，如“记住密码”)
* **ExecutorService** 和 **Handler** (用于处理后台任务和 UI 线程更新)

## 安装

1.  确保你的开发环境已安装 **Android Studio**。
2.  将此项目克隆到你的本地计算机。
3.  在 Android Studio 中选择 "Open an existing project"，然后选择克隆的项目目录。
4.  等待 Gradle 构建完成。
5.  连接你的 Android 设备或启动 Android 模拟器。
6.  点击 "Run" 按钮来编译并运行应用程序。

## 使用

1.  **注册和登录**：首次使用需要注册账号，之后可以使用注册的账号登录。
2.  **查看笔记**：登录后，主界面会显示已保存的笔记列表。
3.  **添加新笔记**：点击主界面右下角的浮动操作按钮（FAB）可以添加新的笔记，包括标题、内容和图片。
4.  **编辑笔记**：点击列表中的笔记可以进入编辑界面，修改笔记内容。
5.  **删除笔记**：长按列表中的笔记会弹出确认对话框，点击确认即可删除该笔记。
6.  **图片附件**：在添加或编辑笔记时，可以点击相机按钮拍照或点击图库按钮选择已有图片添加到笔记中。

## 贡献

欢迎任何形式的贡献！如果你发现了 Bug 或者有新的功能建议，请提交 Issue 或者 Pull Request。

1.  Fork 本项目。
2.  创建你的特性分支 (`git checkout -b feature/your-feature`)。
3.  提交你的更改 (`git commit -am 'Add some feature'`)。
4.  推送到远程分支 (`git push origin feature/your-feature`)。
5.  提交 Pull Request。

## 许可证

MIT License

## 联系

qizhi1215@gmail.com

---

感谢使用 **随笔 (Qizhi Notes)**！
