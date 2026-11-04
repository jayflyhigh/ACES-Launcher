![Olauncher](https://repository-images.githubusercontent.com/278638069/db0acb80-661b-11eb-803e-926cae5dccb4)


# Olauncher | Minimal AF Launcher
AF stands for Ad-Free! :D

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
    alt="Get it on Play Store"
    height="80" align="middle">](https://play.google.com/store/apps/details?id=app.olauncher)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80" align="middle">](https://f-droid.org/packages/app.olauncher)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png"
    alt="Get it on IzzyOnDroid"
    height="54" hspace="13" align="middle">](https://apt.izzysoft.de/packages/app.olauncher)

#### Install using [Play Store,](https://play.google.com/store/apps/details?id=app.olauncher) [F-Droid,](https://f-droid.org/packages/app.olauncher) [IzzyOnDroid](https://apt.izzysoft.de/packages/app.olauncher) or the [latest APK.](https://github.com/tanujnotes/Olauncher/releases/)

To maintain the simplicity of the launcher, a few niche features are available but hidden.

Please check out the **[About](https://tanujnotes.substack.com/p/olauncher-minimal-af-launcher?utm_source=github)** page in the Olauncher settings for a complete list of features and **FAQs**.

##

License: [GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)

### ACES Global Appearance

Open **ACES → Global Appearance** (the last ACES option) to customize all four
search pages. **A - App search settings** is an empty placeholder for future settings.
The selector above the fixed live preview switches between Apps, Contacts, Engines,
and All without changing the actual search page or its tile count. Samples use the
existing app, contact, engine, and category layouts without launching actions.
The preview includes a button to preview the existing
three-line menu. Settings are grouped into Layout Preview, Colors, Three-Line Menu,
and Layout and Padding. Expand a color control to use the hue/saturation wheel,
brightness slider, or enter `#RRGGBB`. Color swatches show the current selection.
Result and menu text stays white regardless of the chosen background.

**Choose Settings** selects which layout to edit; it does not change the actual
search layout. Tile colors, menu-tile color opt-in, page background and transparency,
and popup background are shared by both layouts. When menu tiles do not use the
result tile color, a separate Menu Tile Color control is available and saved.
Left, right, top, and bottom results-area padding and spacing remain
independently saved for each layout. Existing colors use the previously saved
one-column value, falling back to the two-column value when unset.
The controls have an opaque background; the live preview sits directly above the
window wallpaper so increasing transparency reveals the current launcher background,
including live wallpaper, without needing wallpaper access permissions.
The popup preview stays open while editing colors and closes with
its preview button or Back. The existing **Number of Tiles** menu selects the active
search layout and its saved appearance. Unchanged settings retain themed defaults.
Padding affects only the results area, not the search bar or tile interiors, and
is limited on small screens to keep results usable. Alignment controls have been
removed. Results remain scrollable. All appearance controls apply to Apps, Contacts,
Engines, and Search Everything. Existing saved colors and layout values are retained.

The Android-resource unit tests include opening Global Appearance, switching all
four preview pages and both tile layouts, restoring the screen, and checking the
empty App Search Settings placeholder on Android API 24 and 28.
Run them with `./gradlew :app:testDebugUnitTest`.

Developer: [@tanujnotes](https://x.com/tanujnotes)

##

### Other apps from team Olauncher:

- [Pro Launcher](https://play.google.com/store/apps/details?id=app.prolauncher) - Pro version of Olauncher with extra features like widgets, weather, folders, etc.

- [Note to Self](https://play.google.com/store/apps/details?id=com.makenotetoself) - Free and [open source](https://github.com/jeerovan/ntsapp) notes app with chat like interface and end-to-end encryption.

- [Pentastic](https://play.google.com/store/apps/details?id=app.pentastic) - Free and [open source](https://github.com/tanujnotes/Pentastic). A minimal tasks, notes and reminders app.

- [Supernova](https://github.com/tanujnotes/Supernova) - Free and [open source](https://github.com/tanujnotes/Supernova) alternative of Nova Launcher.

##

Thank you!
