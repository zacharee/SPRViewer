package tk.zwander.sprviewer.util

interface BatchExportListener {
    fun onProgressUpdate(current: Int, max: Int)
    fun onSubProgressUpdate(current: Int, max: Int)
    fun onBaseFilenameUpdate(filename: String)
    fun onCurrentFilenameUpdate(filename: String)

    fun onExportComplete()
}