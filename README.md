# GhastCall

A small Paper plugin that makes a Happy Ghast fly toward the player who right-clicks it. It is designed to coexist with GhastQoL and does not modify GhastQoL's speed settings.

## Build without installing Java/Maven on your PC

1. Create a new GitHub repository and upload the contents of this folder (including `.github/workflows/build.yml`).
2. Open the repository's **Actions** tab and run **Build GhastCall** (or push a commit to `main`).
3. Open the completed workflow run, download the `GhastCall-plugin` artifact, and extract `GhastCall-1.0.0.jar`.
4. Upload the JAR to your server's `plugins` folder in the Bisect Hosting file manager.
5. Restart the server. Keep GhastQoL installed.

## GhastQoL setting

Disable GhastQoL's automatic look-to-follow feature in its config, then restart the server. The exact key names vary by GhastQoL version; check its config/documentation rather than adding guessed keys.

## Notes

- The project compiles against Paper API 1.21.8 and Java 21 for compatibility with the Happy Ghast entity API introduced in 1.21.6. It has not been compiled or tested against Minecraft/Paper 26.3 specifically.
- Right-clicking the Happy Ghast starts a 5-second approach. It stops when within about 2.5 blocks, or after 5 seconds.
- Server versions can change entity movement behavior. If the ghast does not move as expected on 26.3, the plugin may need to be rebuilt against that version's Paper API.
