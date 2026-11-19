# Photo Rescue (Android, Kotlin + Jetpack Compose)

Three recovery layers:

1. **Bin tab** - lists and restores photos in the Android 11+ system recycle bin (MediaStore trash, ~30 days).
2. **Scan tab** - finds image files the gallery no longer lists: thumbnails, leftover `.pending` files, WhatsApp/Telegram media folders. Recovered copies go to `Pictures/Recovered`.
3. **Vault tab** - optional protection. When on, new photos are copied (AES-256 encrypted) into app-private storage as soon as the gallery changes (WorkManager content-URI trigger + 6-hour safety net). Deleted-later photos can then be restored.

## Run
1. Open this folder in Android Studio (Koala or newer). Let Gradle sync; it creates the wrapper.
2. Run on a real device (Android 10+; the Bin tab needs Android 11+).
3. Grant photo access. On Android 14+ choose "Allow all".

## Honest limits
- Photos emptied from every bin and never backed up cannot be reliably recovered on modern encrypted phones.
- The Scan tab often finds only thumbnails or cached copies.
- Vault protection only covers photos taken/saved after it is turned on (or existing ones if you choose "All photos").
- Force-stopping the app or aggressive battery savers can pause background backup.

## Ideas for next steps
- Sync the vault to S3 (Cognito + presigned URLs) for off-device backup.
- Video support (MediaStore.Video), SD-card file carving on rooted devices.
