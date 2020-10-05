package tk.zwander.sprviewer;

import tk.zwander.sprviewer.IBatchExportListener;

interface IBatchExportService {
    void registerBatchExportListener(IBatchExportListener listener);
    void unregisterBatchExportListener(IBatchExportListener listener);

    void cancelSession();
}