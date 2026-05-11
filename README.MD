# TravelPlanner

### Android
To run the application on android device/emulator:  
 - open project in Android Studio and run imported android run configuration  

To build the application bundle:  
 - run `./gradlew :androidApp:assembleDebug`  
 - find `.apk` file in `androidApp/build/outputs/apk/debug/androidApp-debug.apk`  

### Desktop
Run the desktop application: `./gradlew :desktopApp:run`  
Run the desktop **hot reload** application: `./gradlew :desktopApp:hotRun --auto`  

### iOS
1. Open `iosApp/iosApp.xcodeproj` in **Xcode** (macOS).
2. Select a simulator or a connected device.
3. In **Signing & Capabilities** for target `iosApp`, choose your **Team** (Apple ID).
4. Press **Run** (⌘R). The first build runs `./gradlew :sharedUI:embedAndSignAppleFrameworkForXcode` to build and embed the `SharedUI` Kotlin framework.

You can also build the framework from the terminal:

```bash
./gradlew :sharedUI:embedAndSignAppleFrameworkForXcode
```

Optional: [Kotlin Multiplatform Mobile](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform-mobile) in Android Studio for a shared run configuration.

