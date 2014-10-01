Bandcamp Explorer
================

Bandcamp Explorer is a simple JavaFX app to explore music on Bandcamp.com, aimed to overcome some shortcomings of standard Bandcamp web interface. 

Some of its features include:
- different ways to search for releases (standard search, search by tags, search by direct links)
- the ability to combine results from different searches
- viewing search results in a compact table view with sortable columns
- the ability to filter the results using different criteria (for example, to show only "free" and "name your price" albums, released by specified artist after specified date)
- embedded audio player that allows to play audio for any release found during search (and yes, unlike Bandcamp player, it lets you adjust sound volume)

Note that Bandcamp Explorer does NOT use Bandcamp API (to use the API, developer key must be obtained, but they are not handing out new developer keys since 2013, I guess). Instead it browses Bandcamp web pages to collect necessary data.
This basically means three things:
* The app could stop working at any moment if Bandcamp folks decide to change the way their pages rendered (you have been warned).
* Much traffic is generated during search because we need to actually browse every page to obtain its data. For example, if you got 100 albums in search result, it's pretty much the same as you have manually browsed them.
* Loading times can be slow, depending on your bandwidth (see above).

### How to use it
To use Bandcamp Explorer you only need Java 8 with JavaFX. No third-party libraries are required.
Latest release can be found at [releases section](https://github.com/monochord/BandcampExplorer/releases). To try this app just download jar file of latest version and run it, no installation or configuration is required.
