package tk.zwander.sprviewer.util

import android.text.Editable
import android.text.TextWatcher

abstract class TextWatcherAdapter : TextWatcher {
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

    }

    override fun afterTextChanged(s: Editable?) {

    }
}