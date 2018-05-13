Bandcamp Explorer
================

Bandcamp Explorer is a simple JavaFX GUI desktop app aimed to help with finding interesting music on [Bandcamp.com](http://bandcamp.com/) website by providing some features that standard Bandcamp web interface lacks. 

Some of its features include:
- Different ways of finding Bandcamp releases. You can use standard Bandcamp search, search by tags, load artist/label discographies or simply provide a direct link to an arbitrary web page (Bandcamp or non-Bandcamp) to load all releases from that page. Also supported is the loading of releases stored as URLs in text files on local or network drive (just supply a direct link to the file prepended with "file:///" or "file://host/" prefix).
- The ability to run different search sessions and combine results from them (helpful when searching for something by multiple tags).
- Viewing search results in a compact table view with sortable columns.
- The ability to filter the results using different criteria (for example, to show only "free" and "name your price" albums, released by given artist after specified date).
- Embedded audio player that allows to play audio for any release found during search as well as to load any individual release by giving it a direct link. And yes, unlike Bandcamp player, it lets you adjust sound volume.

Note that Bandcamp Explorer does NOT use Bandcamp API. To use the API, developer key must be obtained, but, according to [their site](http://bandcamp.com/developer), API is no longer supported and no new keys will be granted. So instead, the app simply scrapes Bandcamp web pages to collect necessary data.
This basically means three things:
- The app can stop working at any moment if Bandcamp folks decide to change the way their pages rendered (you have been warned).
- Much traffic is generated during search because we need to actually load every page to obtain its data. For example, if you got 100 albums in search result, it's pretty much the same as you have manually browsed them.
- Loading times can be slow, depending on your bandwidth (see above).

### Requirements
You only need to have Java 8 installed (ideally Java 8u40 or later, but anything from Java 8u20 should be fine). No third-party programs or libraries are required.
Note that this app was thoroughly tested only on Windows 7, and while it should run on every platform where Java 8 is supported, it is not guaranteed to work reliably (if at all) on non-Windows 7 systems for that reason. Java 9/10 support was not tested yet and it is quite likely that the app will not work properly on these versions of Java.

### How to use it
Latest release can be found at [releases section](https://github.com/monochord/BandcampExplorer/releases). To try this app just download jar file of latest version and run it, no installation or configuration is required.
The jar file can be run by double-clicking on it (Windows) or using the following console command:
`java -jar "path-to-downloaded-jar-file"`
