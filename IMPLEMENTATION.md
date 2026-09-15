# Private gallery implementation and validation

## Audit of the AI Studio export

All six requested features had some implementation. App Lock already hid its biometric button; the screenshot setting defaulted to blocked; the blur slider was connected on Android 12+; storage and trash UI/DAO methods existed; restore and key-persistence fixes had been attempted. The history's successful test run occurred before the later UI changes and did not establish that the final export worked.

The remaining defects included: viewer/folder deletion bypassing trash, stale folder subscriptions, immediate viewer selection not reaching actions, restore treating a missing output stream as success, invalid restore directories, and wrapping-key encryption using an IV rejected by Android Keystore.

## Completed changes

- App Lock uses only the app PIN, waits for saved lock preferences before exposing content, and relocks after backgrounding. Separate vault authentication remains available after passing App Lock.
- Screenshots start blocked before the first UI frame. Allow Screenshots persists and controls the secure-window flag.
- Blur strength persists and works on Android 7+, including versions without native Compose blur.
- Vault Storage measures encrypted media, thumbnails, and staging files on disk, including trash. The trash subtotal describes original media sizes.
- Restore tries an allowed original path and a safe Pictures/Movies fallback. It verifies the written content before publication and vault-copy removal, cleans failed pending rows, checks decrypted size, and supports legacy storage permission requests.
- Key wrapping lets Android Keystore generate its IV, atomically persists and verifies the wrapped key, migrates surviving legacy seeds, and refuses to replace unreadable existing keys. Existing media formats and key aliases are retained. The IV restriction is documented in [Android's KeyGenParameterSpec reference](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setRandomizedEncryptionRequired(boolean)).
- Grid/viewer deletions go to trash. Folder deletion reparents items before deleting the folder to prevent cascading data loss. Trash allows recovery and confirmed permanent deletion, with no automatic expiry. Permanent deletion rejects active items and retains failed deletions in the database.
- Folder observation cancels the previous subscription, and viewer actions read current selections immediately. Database upgrades preserve records; destructive migration fallback was removed.
- MediaStore refreshes now signal a conflated background query instead of running full image/video scans on the main thread for every provider callback. Pending, trashed, and empty rows stay out of the gallery.
- Images and videos use distinct stable UI identities even when their separate MediaStore tables assign the same numeric ID. This prevents duplicate lazy-grid keys, missing tiles, and viewer/grid ordering disagreements.
- Restores retain their capture-date position instead of sorting as newly created photos. Restore operations are serialized and the UI rejects repeat taps while a restore is active, preventing duplicate published copies.
- Video tiles use decoded preview frames. The viewer has play/pause, elapsed/total time, and a seek slider above the media action bar; only the visible pager video is allowed to play.
- Navigation and viewer chrome use eased fade/scale/slide motion. Bottom navigation, media selection, PIN entry, viewer actions, playback, and completed seeking provide contextual haptic feedback.
- Long-pressing a gallery item can continue into a drag across adjacent tiles, selecting the entire traversed range with animated tile scaling, haptic ticks, and edge auto-scroll.
- Photo and album grids expose a draggable fast-scroll handle. While scrolling, a floating date chip shows Today, Yesterday, or the current calendar date.
- The main tab bar floats over content and contracts to an icon pill while any tab scrolls; Photos and Albums also collapse their top app bars to expand the media viewport.
- Albums includes Favorites and shared-gallery Trash collections. These use Android MediaStore favorite/trash state, consent prompts, restore, and separately confirmed permanent deletion; normal gallery delete actions now move items to Trash.

## Build and installation

Use Android Studio with JDK 17 and Android SDK 36.1. The included Gradle wrapper uses version 9.3.1:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

This workspace also has an ignored local tool installation:

```powershell
.\.tooling\build.ps1 :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. No Gemini API key is needed for local gallery functionality. Debug signing uses the exported `debug.keystore` if present, otherwise the standard Android debug key.

Updating an existing installation requires its original signing key. Do not uninstall a vault containing media to work around a signing mismatch: uninstalling deletes its private data and keys. Media whose original encryption key was never persisted cannot be recovered from ciphertext alone; a surviving wrapped key or legacy seed is necessary.

## Verification coverage

Final local validation (14 September 2026): `testDebugUnitTest`, `assembleDebug`, `lintDebug`, and `compileDebugAndroidTestKotlin` all succeeded. All 18 JVM tests passed with no skips. Lint reported no errors. The Gradle wrapper was generated successfully.

Follow-up validation on a Pixel 9: all 18 JVM tests passed, lint and APK assembly succeeded, and all 3 Android instrumentation tests passed. The device suite exercised Android Keystore persistence and the real move-to-vault pipeline with a generated temporary image, verified its decrypted bytes, cancelled the MediaStore deletion, and cleaned up the test image. The installed build also uses biometric-only vault authentication, edge-to-edge tab layout, eased navigation motion, GPU thumbnail blur on Android 12+, and avoids per-frame preference writes while dragging the blur slider.

The 14 September 2026 gallery/player update was assembled, linted, and passed all 18 JVM tests. It was installed on the connected Pixel 9 with `adb install -r` to preserve existing app data and launched successfully. Instrumentation was intentionally not rerun after the user began testing real vault data because the connected-test lifecycle uninstalls the target package when it finishes.

Regression tests cover trash/folder recovery, permanent deletion restrictions, storage counting, immediate viewer selection, folder switching, migrations from database versions 1 and 2, restore failures and byte verification, ciphertext corruption/truncation, and key recreation with software test storage.

`KeystorePersistenceTest` is an Android instrumentation test for real Keystore persistence, legacy-seed migration, and refusal to replace corrupted keys. Run it with an attached device/emulator:

```powershell
.\.tooling\build.ps1 :app:connectedDebugAndroidTest
```

Device acceptance checks using disposable media:

1. Enable App Lock, background and force-stop/reopen. Verify only the app PIN unlocks it.
2. Confirm screenshots are blocked by default; toggle on/off and repeat after restart, including in the viewer.
3. Move photos/videos into two folders; switch between them and adjust blur strength.
4. Force-stop/reopen, unlock, view each file, and restore it to the shared gallery. Compare content.
5. Delete from grid and viewer, recover from trash, then delete a folder with media and recover its items.
6. Confirm trash still consumes Vault Storage; permanently delete from trash and verify storage decreases.
# September 2026 visual and interaction refinement

- Added six built-in color palettes (Ocean, Graphite, Lavender, Rose, Forest, and Sunset) with persistent selection in Settings.
- Refined the type scale, component corner system, compact scrolling navigation, date indicator, and fast-scroll handle for a cleaner gallery presentation.
- Made the media viewer fully edge-to-edge and added image-derived frosted gradients behind its top and bottom controls.
- Fixed long-press range selection by removing the competing thumbnail long-press recognizer when grid drag selection is active. Range selection now works in Photos, album details, Favorites, and Trash.
- Moved the Photos header spacing into the scrolling grid so rows reach the physical top edge after the header collapses, with an image-derived blur and gradient beneath the status icons.
- Corrected bottom-navigation sizing and proportions so icons, labels, and selection indicators retain their intended spacing in both expanded and scrolling states.
- Neutralized palette surfaces and backgrounds; palette choices now make restrained changes to accent text, icons, and selected controls.
- Reverted the media viewer to a single black, edge-to-edge canvas with simple control gradients; removed duplicated blurred-image panels and rounded full-width chrome.
- Replaced the themed Material video slider rendering with a thin neutral scrubber and small circular handle.
- Limited the gallery status-edge blur to active scrolling so it disappears immediately when scrolling stops.
- Removed the gallery status-edge blur overlay entirely after device testing exposed unstable delayed frames during and after flings.
- Constrained the fast-scroll handle to the centered 80% of available height so it remains reachable away from system-edge gesture zones.
- Corrected drag-selection hit testing for the Photos grid's leading scroll inset, preventing selection from landing one row below the pressed thumbnail.
- Added immediate neutral-grey thumbnail placeholders and expanded the restrained accent palette collection from six to twelve choices.

# Viewer motion, details, and stability pass

- Added dedicated open/close transitions for the media viewer plus decoded-frame reveal animations for photos and videos, eliminating abrupt black-to-media swaps.
- Added an opt-in **Viewer Details** setting that overlays resolution and file size in both gallery and encrypted-vault viewers.
- Generalized long-press drag selection and enabled it for Photos, album media, Favorites, device Trash, vault folders, and Vault Trash.
- Prevented vault callbacks from force-unwrapping folder/file state that can change during navigation or database updates.
- Reworked photo state aggregation into typed flows, clamped persisted grid/theme settings, and moved secondary selection actions into an overflow menu to prevent narrow-screen toolbar collisions.

# Vault hardening and scroll reliability pass

- Added the backward-compatible PGV3 media format. New ciphertext uses independently derived HKDF-SHA256 file keys, AES-256-GCM associated data bound to the opaque media identity, authenticated chunk positions, and an authenticated end marker. Existing PGV2 and legacy vault files remain readable.
- The lock pipeline now hashes the source while encrypting, flushes the ciphertext to storage, decrypts and authenticates the complete staged result into a digest sink, and compares its SHA-256 digest and size before requesting deletion of the original MediaStore item.
- Vault thumbnails are encrypted at rest with identity-bound PGV3 keys. Existing plaintext thumbnails are encrypted automatically the first time they are loaded, and decrypted thumbnail memory is evicted whenever the vault locks.
- Added startup reconciliation for interrupted transfer journal records. It rolls back uncommitted copies when the source remains, preserves an authenticated vault copy when Android already removed the source, and leaves uncertain cases untouched instead of guessing.
- Restore now rolls back its newly published MediaStore copy if the encrypted vault source cannot be removed, preventing repeat attempts from silently creating duplicates.
- Vault authentication now requires a Class 3 (`BIOMETRIC_STRONG`) biometric and continues to provide no device-PIN fallback. Locking evicts the decrypted thumbnail cache and in-process data-key reference. App-lock PIN verifiers are salted PBKDF2 values with constant-time comparison; plaintext values from older builds migrate automatically.
- Replaced the fast scroller's animated, thumb-only gesture target with a stable 52 dp interaction rail. A press anywhere on the rail acquires scrolling immediately, rapid motion cancels superseded scroll jobs, and the visible handle remains within the centered 70% track.
- Removed linear media-list searches from drag selection and fast-scroll frames, corrected pointer-to-grid hit testing, bounded edge auto-scroll work, reused Coil image requests, and supplied lazy-grid content types to reduce allocations and improve normal fling consistency.
- Reconciled Android's deletion result item by item. A partially deleted batch keeps verified vault copies for deleted originals and rolls back only copies whose originals still exist; uncertain items retain their transfer journal for recovery. Fixed a staging error path that could leave an orphaned vault database row.
- Stabilized the fast-scroll pointer handler across changing grid item counts and consumed its press so overlapping thumbnails cannot receive an accidental click. Legacy thumbnail migration now accepts only JPEG data rather than treating a damaged encrypted header as plaintext.

15 September 2026 validation: `assembleDebug`, `lintDebug` (zero errors), and `compileDebugAndroidTestKotlin` passed. The debug APK was installed on the connected Pixel 9 with `adb install -r` and launched without a fatal exception. JVM test execution remains unavailable in this environment because Robolectric cannot download its Android runtime. Packaging a fresh Android test APK hit Windows `AccessDeniedException` on Gradle's transformed DataStore jar and debug-keystore lock even with one worker; an older test APK ran the disposable-media move test, but its Keystore test was binary-incompatible with PGV3's updated method signature.

## Photos drag selection and PIN completion

- Photos now resolves long-press and drag hits against each visible thumbnail's measured window bounds. This removes the row shift caused by the Photos grid's scrolling top inset and date headers while keeping album-grid selection behavior unchanged.
- App Lock checks the PIN off the UI thread and displays all four animated dots for at least 220 ms before unlocking or clearing an incorrect entry. Keypad input is ignored while verification is in progress.
- `assembleDebug` and `lintDebug` passed with zero lint errors; the updated APK was installed on the connected Pixel 9 with `adb install -r` and launched without a fatal exception.

## App Lock entrance motion

- Removed the fixed 220 ms wait after a correct four-digit PIN. Verification still runs off the UI thread; the fourth dot animates as the lock screen fades away and the gallery fades in.
- The gallery remains uncomposed while App Lock is active. It appears only after PIN verification succeeds, underneath the exiting lock screen. Relocking resets PIN-entry state even if an exit animation was interrupted.

## Vault restore destinations and inline unlock progress

- Vault restores now support original recorded folder, a selected existing on-device album, and the PrivateGallery album. The Settings preference also offers Always ask, which opens these choices each time restore is requested. Fixed preferences restore directly.
- Album selection lists existing MediaStore folders compatible with the selected media types. A persistent selected album must accept both photos and videos; mixed batches do not offer Movies-only folders.
- Explicit folders are validated for the matching image/video MediaStore collection. If the folder is unavailable, rejected, or absent from the vault record, the encrypted vault copy stays intact and restore reports the failure. Explicit choices never silently fall back to PrivateGallery. New vault records keep the actual queried relative path instead of guessing Camera for a missing path.
- App Lock shows verification and gallery-opening progress next to the completed PIN dots within the lock screen. The lock remains over the app while the Photos state initializes, then fades away. A five-second fallback prevents a stalled media scan from trapping the user on the lock screen.
- `assembleDebug` and `lintDebug` passed. The focused MediaRestoreTest compiled, but JVM execution could not start because Robolectric could not download its Android runtime. The rebuilt APK was not installed because ADB reported no connected devices at installation time.

## Scroll previews, image navigation, and status-edge fade

- Newly composed thumbnails during a normal grid fling request a small 128 px decoded preview; when scrolling settles, visible tiles upgrade to the 300 px preview. Already sharp tiles are not downgraded when another fling starts. Album covers use 180/400 px previews, and search results use the same media-tile behavior. Each quality has its own memory-cache key; the sharp request uses the quick preview as its synchronous placeholder where available.
- Photos, album details, and Favorites now match the selected image by stable media ID with the full-screen viewer inside a Compose shared-transition layout. The thumbnail bounds expand into the viewer and contract back on navigation. Photo viewers reuse the cached sharp thumbnail while the full image loads. Video views keep their existing transition.
- A fixed translucent top gradient tints media beneath the status icons. It performs no bitmap blur and does not appear or disappear based on scroll state.
- Google Photos' exact private thumbnail pipeline could not be confirmed from public documentation. This implementation follows Android's documented downsampling guidance and Compose shared-bounds navigation API.
- `assembleDebug` and `lintDebug` passed. The APK was installed on the connected Pixel 9 with `adb install -r` and launched with no fatal AndroidRuntime event in the startup log. Animation quality and scroll frame timing were not measured because the gallery was behind the user's App Lock during automated validation.

## Viewer swipe and restore-picker refinement

- Removed the shared-bounds animation from every precomposed pager page. Only the settled, non-scrolling photo can use a single shared-element transition; this prevents adjacent pages from contributing a second image to a return animation. Back now selects the visible pager photo as the return target.
- The photo viewer no longer hides cached thumbnail previews behind its decoded-frame reveal animation during a swipe. It uses the cached sharp preview when present, or the quick preview when a tile was opened during a fling. Coil crossfades only on ordinary paging; the matched thumbnail and viewer image do not crossfade during a shared-image transition, avoiding simultaneous preview and full-image drawings. Removed competing whole-screen scale transforms on viewer navigation.
- The zoom gesture handler now stays active through an entire pinch instead of restarting when the image first becomes zoomed. At 1x, one-finger drags remain available to the horizontal photo pager; when zoomed, pan and pinch are handled by the image.
- Rebuilt the vault restore destination dialog with readable option cards and a clear selected state. The album picker now offers search, album cover previews, path labels, and item counts. Both dialogs constrain their height for small and landscape screens. Restore modes and compatible-folder filtering retain their prior behavior.
- `assembleDebug` and `lintDebug` passed after the gesture and image-request fixes. The latest APK was installed on the connected Pixel 9 with `adb install -r`, preserving app data. An earlier corrected build completed a device open/swipe/back flow without a fatal AndroidRuntime event; the phone moved to other foreground apps before the latest flow could be repeated. Secure-screen capture remains disabled by the app's default setting, so the visual overlap still needs an on-device observation.

## Current-photo return transition

- A shared image's `ContentScale.Crop` and `ContentScale.Fit` endpoints snap by default in Compose. The tile and viewer now interpolate their scale factors over the navigation transition, so the square crop gradually becomes the fitted photo and reverses continuously on Back. The matching image bounds and content scale use the same 260 ms motion; the shared modifier sits before the image-size modifier as Android recommends.
- Back uses the pager's displayed media ID. Photos, album detail, and Favorites keep their grid state while the viewer is open; when the displayed photo is outside the visible rows, the source grid requests a position near that photo before the viewer pops. A frame is allowed for the new shared-image key to attach. Favorites paging is limited to Favorites, so each displayed photo has a matching tile in that collection.
- On the connected Pixel 9, an automated Photos swipe from item 2 to item 20 returned with item 20 visible in its tile. A Screenshots album swipe from item 1 to item 21 returned with item 21 visible after the grid repositioned. The user also confirmed that the prior width/height snap no longer appears on return. The gallery's secure-screen setting still prevents automated visual capture.

## Settled-photo return regression

- A second or long swipe could leave the viewer's cached media ID behind the pager's settled page. Back then scrolled the grid to one ID while the shared image contracted toward another tile. Back now reads the media ID directly from the current media list at the pager's settled page after scrolling stops; the settled-page collector also updates the fallback ID. The opening shared-image key is cleared when paging settles on a different photo, and Back waits for the new viewer key to compose before popping.
- Shared-image content scaling now interpolates between the actual tile and viewer bitmap-scale endpoints, avoiding a brief expansion at the start of the return. Debug builds log the opened, settled, and matched media IDs to make future destination mismatches visible.
- `assembleDebug` and `lintDebug` passed, and the corrected APK was installed on the connected Pixel 9 with `adb install -r`. Device logs showed matching current-photo IDs at the viewer and tile after two swipes and after offscreen grid repositioning. The user manually confirmed that both paths now return into the correct tile.
