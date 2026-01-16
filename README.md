# Tauri Plugin Native Camera

A Tauri plugin that captures photos using the device's native camera application.

[![Crates.io](https://img.shields.io/crates/v/tauri-plugin-native-camera.svg)](https://crates.io/crates/tauri-plugin-native-camera)
[![Documentation](https://docs.rs/tauri-plugin-native-camera/badge.svg)](https://docs.rs/tauri-plugin-native-camera)
[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## Overview

This plugin uses Android's `ACTION_IMAGE_CAPTURE` intent to open the system camera app (Samsung Camera, Google Camera, Pixel Camera, etc.) rather than implementing a custom camera UI. This ensures:

- **Familiar UX**: Users interact with their device's native camera app
- **Universal compatibility**: Works on all Android devices
- **Proper safe area handling**: No UI elements cut off by notches or navigation bars
- **Maintained by device manufacturers**: Camera features and quality optimizations

## Features

- 📷 Opens the device's native camera application
- 🖼️ Returns captured photos as base64-encoded JPEG data
- 🔒 Handles camera permissions automatically
- 📐 Automatic EXIF rotation correction
- 📏 Automatic image resizing for large photos (max 2048px)
- ✨ Works on all Android devices

## Platform Support

| Platform | Supported |
|----------|-----------|
| Android  | ✅        |
| iOS      | ❌        |
| Desktop  | ❌        |

## Installation

### Rust

Add the plugin to your `Cargo.toml`:

```toml
[dependencies]
tauri-plugin-native-camera = "0.1"
```

Or for Android-only (recommended to avoid compilation issues on other platforms):

```toml
[target.'cfg(target_os = "android")'.dependencies]
tauri-plugin-native-camera = "0.1"
```

### JavaScript/TypeScript

Install the JavaScript bindings:

```bash
npm install tauri-plugin-native-camera-api
# or
pnpm add tauri-plugin-native-camera-api
# or
yarn add tauri-plugin-native-camera-api
```

## Setup

### 1. Register the plugin in your Tauri app

In your `src-tauri/src/lib.rs`:

```rust
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        // Register the plugin (Android-only)
        #[cfg(target_os = "android")]
        .plugin(tauri_plugin_native_camera::init())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

### 2. Add capabilities

Create or update `src-tauri/capabilities/android.json`:

```json
{
  "identifier": "android-capability",
  "platforms": ["android"],
  "windows": ["main"],
  "permissions": [
    "native-camera:allow-take-picture"
  ]
}
```

### 3. Ensure camera permission is in AndroidManifest

The plugin's manifest merger should add this automatically, but if needed, add to your `src-tauri/gen/android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

## Usage

### TypeScript/JavaScript

```typescript
import { takePicture } from 'tauri-plugin-native-camera-api';

async function capturePhoto() {
  try {
    const result = await takePicture();

    console.log('Photo captured!');
    console.log('Dimensions:', result.width, 'x', result.height);

    // Use the base64 image data
    const imgElement = document.getElementById('preview') as HTMLImageElement;
    imgElement.src = `data:image/jpeg;base64,${result.imageData}`;

  } catch (error) {
    if (error === 'Camera operation was cancelled by user') {
      console.log('User cancelled');
    } else {
      console.error('Camera error:', error);
    }
  }
}
```

### Rust (Direct API)

```rust
use tauri_plugin_native_camera::NativeCameraExt;

#[tauri::command]
async fn take_photo(app: tauri::AppHandle) -> Result<String, String> {
    let result = app.native_camera().take_picture()
        .map_err(|e| e.to_string())?;

    Ok(result.image_data)
}
```

## API Reference

### `takePicture()`

Opens the device's native camera app and captures a photo.

**Returns:** `Promise<CaptureResult>`

```typescript
interface CaptureResult {
  /** Base64-encoded JPEG image data */
  imageData: string;
  /** Width of the captured image in pixels */
  width: number;
  /** Height of the captured image in pixels */
  height: number;
}
```

**Throws:** Error with one of the following messages:
- `"Camera operation was cancelled by user"` - User pressed back or cancelled
- `"Camera permission denied"` - User denied camera permission
- `"No camera available on this device"` - Device has no camera
- `"Failed to read captured photo: ..."` - Error processing the image

## How It Works

1. **Permission Check**: The plugin first checks if camera permission is granted. If not, it requests permission from the user.

2. **Create Temp File**: A temporary file is created in the app's cache directory to store the captured photo.

3. **Launch Camera Intent**: The plugin creates an `ACTION_IMAGE_CAPTURE` intent with the temp file URI and launches the system camera app.

4. **Handle Result**: When the user takes a photo and confirms, the plugin:
   - Reads the photo from the temp file
   - Applies EXIF rotation correction (handles portrait/landscape)
   - Resizes large images (max 2048px dimension)
   - Converts to base64-encoded JPEG
   - Cleans up the temp file
   - Returns the result to JavaScript

## Comparison with Custom Camera UI Plugins

| Feature | Native Camera (this plugin) | Custom Camera UI |
|---------|----------------------------|------------------|
| UI Familiarity | ✅ Users' own camera app | ❌ Learning curve |
| Safe Area Handling | ✅ Handled by system | ⚠️ May have issues |
| Device Compatibility | ✅ Universal | ⚠️ May vary |
| Camera Features | ✅ Full device features | ❌ Limited |
| Code Complexity | ✅ Simple (~200 lines) | ❌ Complex (~1000+ lines) |
| Maintenance | ✅ Minimal | ❌ Ongoing |

## Troubleshooting

### Camera permission denied

Ensure your app has the camera permission in the manifest and that the user has granted it. The plugin automatically requests permission if not granted.

### Photo not returned

Make sure the FileProvider is correctly configured. Check logcat for errors:

```bash
adb logcat | grep -i NativeCameraPlugin
```

### App crashes on photo capture

This is usually due to the photo file not being accessible. Ensure the FileProvider paths are correctly configured in the manifest.

## Android 11+ Package Visibility

Starting with Android 11 (API level 30), apps need to declare which other apps they intend to interact with. This plugin automatically includes the necessary `<queries>` declaration in its manifest:

```xml
<queries>
    <intent>
        <action android:name="android.media.action.IMAGE_CAPTURE" />
    </intent>
</queries>
```

This allows the plugin to discover and launch camera apps on Android 11+. The manifest merger should include this automatically when you add the plugin dependency.

If you're experiencing issues with the camera not launching on Android 11+ devices, verify that the queries element is present in your merged manifest by checking:

```bash
# View merged manifest
cat src-tauri/gen/android/app/build/intermediates/merged_manifest/*/AndroidManifest.xml | grep -A3 "queries"
```

## FileProvider Configuration

This plugin uses the host app's existing FileProvider (with authority `${applicationId}.fileprovider`) to share the photo file URI with the camera app. Your app must have a FileProvider configured with `cache-path` access.

The default Tauri Android template includes this configuration. If you have a custom setup, ensure your `file_paths.xml` includes:

```xml
<cache-path name="my_cache_images" path="." />
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Author

Kushal Das <mail@kushaldas.in>

## Acknowledgments

- [Tauri](https://tauri.app/) - The framework that makes this possible
- Android's [Camera Intent](https://developer.android.com/training/camera/photobasics) documentation
