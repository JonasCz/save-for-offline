**Warning:**

> This is abandoned at the moment, as I don't have time to work on it. This means you're welcome to report bugs, but probably won't fix them.
>
> Also, do not use the Play Store version, as it is outdated and broken (someone else uploaded it)

# Save For Offline

Save For Offline is an Android app for saving full webpages for offline reading, with lots of features and options.

In you web browser select 'Share', and then 'Save For Offline'

## Features

* Saves real HTML files which can be opened in other apps / devices

* Download & save entire web pages with all assets for offline reading & viewing

* Save HTML files in a custom directory

* Save in the background, no need to wait for it to finish saving

* Night mode, with both a dark theme, and can invert colors when viewing pages (White becomes black and vice-versa).

* Search of saved pages (By title only for now).

* User agent change, allows to save either desktop or mobile version of pages

* Nice UI for both phones and tablets, with various choices for layout and appearance.

## Download

Head to the [Releases section](http://github.com/JonasCz/save-for-offline/releases) to download an APK.

<a href="https://f-droid.org/packages/jonas.tool.saveForOffline/" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="100"/></a>

Remember: The play store version is old, broken, and not recommended !

Please report any bugs you find, and contribute if you can!

## Screenshots:
#### Grid layout for the list of all saved pages.
![Grid layout](https://raw.githubusercontent.com/JonasCz/save-for-offline/master/screenshots/gridlayout.png)
***

#### List of all saved pages, running on a small-screen device with night mode enabled.
![List layout](https://raw.githubusercontent.com/JonasCz/save-for-offline/master/screenshots/list_small_night.png)
***

#### Built in viewer, again on a small screen, with night mode enabled (Saved HTML files can also be opened in other apps or copied to computer)
![Viewer](https://raw.githubusercontent.com/JonasCz/save-for-offline/master/screenshots/viewer_small_night.png)
***

More screenshots can be found in the [screenshots directory](https://github.com/JonasCz/save-for-offline/master/screenshots/).

## About

#### Known bugs:

* (Device / Android version specific) Thumbnails of saved pages don't show up in the list - this can't be fixed, as it's a bug / limitation in the underlying Android WebView component.

* Sometimes does not save stylesheets or other assets - the only problematic site I know of is Wikipedia, where stylesheets (CSS) don't get saved properly, but is otherwise OK - Still trying to figure out why.
 
* Occasionally fails when it encounters a redirect - also trying to figure out why.

* If there are multiple image or asset references on the page pointing to the same URL, but that URL delivers a something different each time, only the first version of the asset will be saved. Maybe this is also the cause of the Wikipedia issue. - Can't really figure out a good way to fix this.

* Other, occasional, random crashes.

If you can fix any of these issues, please do submit a PR!

#### Roadmap:

* General stability and reliability fixes.

* Import / export feature.

* Code cleanup and refactoring.

* Externalize strings - translations.

#### This app uses:

* [FuzzyDateFormatter](http://github.com/igstan/fuzzyDateFormatter/)

* [GetMeThatPage](https://github.com/PramodKhare/GetMeThatPage/) (not anymore)

* [OkHttp](https//github.com/square/OkHttp/)

* [jSoup](http://jsoup.org)

* A few other things, see preferenes > about > credits in the app itself.

#### Licence is GPLv2+, see the LICENCE file:

**Note:** Don't use or modify my code unless you can comply with the license, and release the source
code of your modifications where necessary. I don't want to see my hard work end up in some commercial
app, and it already has. Thanks.

```
This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
```
