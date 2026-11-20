# Releasing

Sift ships as a signed APK on [GitHub Releases](https://github.com/nikableh/sift/releases). The [release workflow](../.github/workflows/release.yml) builds every published APK from a version tag in this repository

## Creating a release

1. Raise `versionCode` and `versionName` in [`app/build.gradle.kts`](../app/build.gradle.kts) and commit the change to `main`
2. Tag that commit as `v<versionName>` and push the tag

```sh
git tag v0.12
git push origin v0.12
```

The workflow stops if the tag doesn't match `versionName`. A version with a hyphen, such as `0.12-beta1`, is published as a pre-release. GitHub generates the release notes from pull requests merged since the previous tag, and the workflow adds instructions for verifying the download

To test changes to the workflow, start it from the Actions tab or with `gh workflow run release.yml`. A manual run builds, signs and attests the APK and keeps it as a workflow artifact, without publishing a release

## What the workflow does

1. Builds `assembleRelease` with the production key
2. Checks that the APK's signing certificate matches `SIFT_CERT_SHA256`. Gradle falls back to the debug key when the production key is missing, and this check stops such a build from shipping
3. Records a [build provenance attestation](https://docs.github.com/en/actions/concepts/security/artifact-attestations) for the APK
4. Publishes the release with these files

| File | Contents |
| --- | --- |
| `sift-<version>.apk` | The signed APK |
| `sift-<version>.apk.sha256` | SHA-256 checksum of the APK |
| `sift-<version>-mapping.txt` | R8 mapping for deobfuscating stack traces from that build |

## Signing

All releases are signed with one RSA 4096 key, alias `sift`, certificate `CN=nikableh.moe`, valid until 2076. The certificate's SHA-256 fingerprint is

```text
EB:7D:8C:04:F3:3D:48:21:67:5F:70:DE:AC:2A:EE:D3:FB:71:40:05:04:39:8C:34:47:52:1C:AF:49:2F:FD:EE
```

Android installs an update only when it is signed with the same key as the installed app, so this key can't be replaced without breaking updates for every existing install. The repository holds it in these Actions settings

| Name | Type | Contents |
| --- | --- | --- |
| `SIFT_KEYSTORE_BASE64` | Secret | PKCS12 keystore, base64-encoded |
| `SIFT_KEYSTORE_PASSWORD` | Secret | Keystore password, also used for the key |
| `SIFT_CERT_SHA256` | Variable | The fingerprint above |

Gradle takes the key from the `SIFT_KEYSTORE_FILE`, `SIFT_KEYSTORE_PASSWORD` and `SIFT_KEY_ALIAS` environment variables. Without them `assembleRelease` signs with the debug key, so local builds don't need the production key. A debug-signed build can't update a release install, or the other way round, without uninstalling first

## Verifying a release

```sh
gh attestation verify sift-0.12.apk -R nikableh/sift
apksigner verify --print-certs sift-0.12.apk
```

The first command confirms that this repository's release workflow built the APK. The second prints the signing certificate, whose SHA-256 digest must match the fingerprint above

## Google Play and F-Droid

Sift isn't on either store yet. The signing and build setup already accounts for both

- **Google Play.** Enrol in Play App Signing with this key instead of a Google-generated one, so Play and GitHub builds can update each other. Uploads to Play then use a separate upload key
- **F-Droid.** F-Droid signs the apps it builds with its own key, unless its build matches the developer's APK byte for byte. In that case it publishes the APK signed with this key. `versionCode` and `versionName` stay literals that F-Droid's update checker can read, and the APK leaves out the dependency metadata block that F-Droid rejects. Nobody has checked yet whether the build is reproducible
