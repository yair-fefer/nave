# Screen Review (צילום לביקורת)

Transparent accountability screenshots for Android.

- `android/` - one app, two modes chosen on first run:
  - **Captured phone**: after the user approves Android's screen-capture dialog, a foreground
    service with a permanent notification takes one screenshot at a random moment every 3-9
    minutes, only while the screen is on and unlocked, and uploads it.
  - **Viewer phone**: last contact + status, the latest screenshot, all screenshots of the last
    24 hours, and a home-screen widget with the latest screenshot.
- `server/` - Vercel functions + Upstash Redis. Screenshots expire after 48 hours.

Build: copy `android/secrets.properties.example` to `android/secrets.properties`, fill it in,
then `gradle assembleDebug` in `android/`. Server env: `APP_KEY` + the Upstash Redis variables.
