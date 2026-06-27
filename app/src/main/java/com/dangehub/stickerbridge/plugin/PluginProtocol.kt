// Plugin protocol constants shared between main app and plugins
object PluginProtocol {
    // Intent action that plugin services must declare
    const val ACTION_STICKER_PLUGIN = "com.dangehub.stickerbridge.STICKER_PLUGIN"

    // Meta-data key in plugin manifest for display name
    const val META_PLUGIN_NAME = "com.dangehub.stickerbridge.plugin_name"
    const val META_PLUGIN_AUTHOR = "com.dangehub.stickerbridge.plugin_author"
    const val META_PLUGIN_VERSION = "com.dangehub.stickerbridge.plugin_version"
    const val META_PLUGIN_DESCRIPTION = "com.dangehub.stickerbridge.plugin_description"

    // Binder transaction code
    const val TRANSACT_QUERY = 1

    // Method names
    const val METHOD_GET_ALL = "getAll"
    const val METHOD_GET_FOLDERS = "getFolders"
    const val METHOD_GET_TAGS = "getTags"
    const val METHOD_SEARCH = "search"
    const val METHOD_FILTER = "filter"
    const val METHOD_GET_DISPLAY_NAME = "getDisplayName"
    const val METHOD_PING = "ping"
}
