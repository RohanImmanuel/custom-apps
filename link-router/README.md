# Link Router

Set this as the default browser and it forwards every link to the browser you pick, optionally in private mode, with a 5-minute "use Chrome" override for payments and logins that only work in Chrome.

## Using it

- **Home**: an arc-reactor HUD showing where links go. **Hold the reactor** (~1.2 s) to send links to Chrome for 5 minutes; hold again to switch back early. While it runs the screen turns red with a live countdown.
- **Notification**: the override posts a countdown notification with a **Switch back** button, and disappears when time is up.
- **Quick Settings tile**: "Chrome · 5 min" starts or stops the override from the shade without opening the app (Settings → Quick Settings tile → Add).
- **Settings** (gear icon): which browser, private mode, the tile, default-browser status, and a test link.

## How it works

- `RouterActivity` is an invisible activity that handles `http`/`https` links. It opens the link in the chosen browser and closes.
- It runs in its own empty task (`taskAffinity=""`). Apps usually open links with `FLAG_ACTIVITY_NEW_TASK`, which would otherwise put the router into Link Router's task, so Back from the browser would land on the Link Router screen instead of the app you came from.
- Links that apps open as Custom Tabs keep their extras when forwarded, so in-app browsers still look right.
- The override just stores an expiry time, so it reverts by itself with nothing scheduled, and the system default browser never changes. (Android doesn't let apps change the default browser without the user confirming each time.)

## Private mode support

| Browser | Private links | How |
|---|---|---|
| Chrome (stable/beta/dev/canary) | ✓ verified on Chrome 155 | ephemeral Custom Tab (`androidx.browser` 1.9) |
| Firefox | untested | `private_browsing_mode` extra |
| Brave | ✗ ignores external incognito requests | opens normally |
| Others (DuckDuckGo, Samsung Internet, …) | ✗ | opens normally |

Settings only offers the private switch for browsers that support it.

## Build and install

```
./gradlew installDebug
```

Open **Link Router** and set it as the default browser when prompted.
