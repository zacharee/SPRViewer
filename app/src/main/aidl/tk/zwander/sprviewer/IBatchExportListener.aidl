package tk.zwander.sprviewer;

interface IBatchExportListener {
    void onProgressUpdate(int current, int max);
    void onSubProgressUpdate(int current, int max);
    void onBaseFilenameUpdate(String filename);
    void onCurrentFilenameUpdate(String filename);

    void onExportComplete();
}