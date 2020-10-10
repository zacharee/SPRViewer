package tk.zwander.sprviewer.ui.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import kotlinx.coroutines.*
import tk.zwander.sprviewer.data.BaseData
import tk.zwander.sprviewer.util.forEachParallel
import java.util.*
import kotlin.collections.ArrayList

abstract class BaseListAdapter<T : BaseData, VH : BaseListAdapter.BaseVH>(dataClass: Class<T>) : RecyclerView.Adapter<VH>(), SearchView.OnQueryTextListener, CoroutineScope by MainScope() {
    private val batchedCallback = SortedList.BatchedCallback(InnerSortCallback())
    private val actuallyVisible = SortedList(dataClass, batchedCallback)

    private val orig = object : ArrayList<T>() {
        override fun add(element: T): Boolean {
            launch {
                if (matches(currentQuery, element)) {
                    actuallyVisible.add(element)
                }
            }
            return super.add(element)
        }

        override fun addAll(elements: Collection<T>): Boolean {
            var t = System.currentTimeMillis()
            replaceAll(elements)
            Log.e("SPRViewer", "replaceAll: ${System.currentTimeMillis() - t}")
            t = System.currentTimeMillis()
            return super.addAll(elements).also {
                Log.e("SPRViewer", "addAll: ${System.currentTimeMillis() - t}")
            }
        }

        override fun remove(element: T): Boolean {
            actuallyVisible.remove(element)
            return super.remove(element)
        }
    }

    private var currentQuery = ""
    private var recyclerView: RecyclerView? = null

    internal abstract val viewRes: Int

    val allItemsCopy: MutableList<T>
        get() = ArrayList(orig)

    override fun getItemCount() = actuallyVisible.size()

    override fun onQueryTextChange(newText: String?): Boolean {
        return if (orig.size > 4000) {
            false
        } else {
            doFilter(newText)
            true
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return if (orig.size > 4000) {
            doFilter(query)
            true
        } else {
            false
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
        cancel()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    final override fun onBindViewHolder(holder: VH, position: Int) {
        onBindViewHolder(holder, position, actuallyVisible[position])
    }

    private fun doFilter(newText: String?) {
        launch {
            withContext(Dispatchers.Main) {
                currentQuery = newText ?: ""

                replaceAll(filter(currentQuery))

                recyclerView?.scrollToPosition(0)
            }
        }
    }

    abstract fun compare(o1: T, o2: T): Int
    abstract fun areContentsTheSame(oldItem: T, newItem: T): Boolean
    abstract suspend fun matches(query: String, data: T): Boolean
    abstract fun onBindViewHolder(holder: VH, position: Int, info: T)

    open fun areItemsTheSame(item1: T, item2: T): Boolean {
        return item1 == item2
    }

    fun add(item: T) {
        orig.add(item)
    }

    fun addAll(items: Collection<T>) {
        orig.addAll(items)
    }

    fun remove(item: T) {
        orig.remove(item)
    }

    fun indexOf(item: T) = actuallyVisible.indexOf(item)

    fun getItemAt(position: Int) = actuallyVisible[position]

    fun addToActuallyVisible(items: Collection<T>) {
        actuallyVisible.addAll(items)
    }

    fun createBaseViewHolder(parent: ViewGroup, position: Int): BaseVH {
        return BaseVH(
            LayoutInflater.from(parent.context)
                .inflate(viewRes, parent, false)
        )
    }

    internal fun getInfo(position: Int) = actuallyVisible[position]

    internal suspend fun filter(query: String): List<T> {
        val lowerCaseQuery = query.toLowerCase(Locale.getDefault())

        val filteredModelList = LinkedList<T>()

        orig.forEachParallel { item ->
            if (matches(lowerCaseQuery, item)) filteredModelList.add(item)
        }

        return filteredModelList
    }

    private fun replaceAll(newItems: Collection<T>) {
        actuallyVisible.beginBatchedUpdates()
        actuallyVisible.clear()
        actuallyVisible.addAll(newItems)
        actuallyVisible.endBatchedUpdates()
    }

    inner class InnerSortCallback : SortedList.Callback<T>() {
        override fun compare(o1: T, o2: T): Int {
            return this@BaseListAdapter.compare(o1, o2)
        }

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return this@BaseListAdapter.areContentsTheSame(oldItem, newItem)
        }

        override fun areItemsTheSame(item1: T, item2: T): Boolean {
            return this@BaseListAdapter.areItemsTheSame(item1, item2)
        }

        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(position, count)
        }

        override fun onChanged(position: Int, count: Int) {
            notifyItemChanged(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    open class BaseVH(view: View) : RecyclerView.ViewHolder(view)
}