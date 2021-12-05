package tk.zwander.sprviewer.ui.adapters

import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import kotlinx.coroutines.*
import tk.zwander.sprviewer.data.BaseData
import tk.zwander.sprviewer.util.get
import java.util.*

abstract class BaseListAdapter<T : BaseData, VH : BaseListAdapter.BaseVH> : RecyclerView.Adapter<VH>(), SearchView.OnQueryTextListener, CoroutineScope by MainScope() {
    private val batchedCallback = SortedList.BatchedCallback(InnerSortCallback())
    internal val actuallyVisible = TreeSet<T> { o1, o2 -> compare(o1, o2) }

    private val orig = object : ArrayList<T>() {
        override fun add(element: T): Boolean {
            launch {
                if (matches(currentQuery, element)) {
                    actuallyVisible.add(element)
                    notifyItemInserted(actuallyVisible.indexOf(element))
                }
            }
            return super.add(element)
        }

        override fun addAll(elements: Collection<T>): Boolean {
            replaceAll(elements)
            return super.addAll(elements)
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

    val fullItemCount: Int
        get() = orig.size

    override fun getItemCount() = actuallyVisible.size

    override fun getItemId(position: Int): Long {
        return actuallyVisible.get(position).hashCode().toLong()
    }

    override fun onQueryTextChange(newText: String?): Boolean {
//        return if (orig.size > 4000) {
//            false
//        } else {
//            doFilter(newText)
//            true
//        }
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
//        return if (orig.size > 4000) {
//            doFilter(query)
//            true
//        } else {
//            false
//        }
        doFilter(query)
        return true
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
        onBindViewHolder(holder, position, actuallyVisible.get(position))
    }

    private fun doFilter(newText: String?) {
        runBlocking {
            currentQuery = newText ?: ""

            val filtered = withContext(Dispatchers.IO) {
                filter(currentQuery)
            }

            replaceAll(filtered)
            recyclerView?.scrollToPosition(0)
        }
    }

    abstract fun compare(o1: T, o2: T): Int
    abstract fun areContentsTheSame(oldItem: T, newItem: T): Boolean
    abstract fun matches(query: String, data: T): Boolean
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

    fun addToActuallyVisible(items: Collection<T>) {
        actuallyVisible.addAll(items)
    }

    internal fun getInfo(position: Int) = actuallyVisible.get(position)

    private fun filter(query: String): List<T> {
        val lowerCaseQuery = query.lowercase(Locale.getDefault())

        val filteredModelList = LinkedList<T>()

        orig.forEach { item ->
            if (matches(lowerCaseQuery, item)) filteredModelList.add(item)
        }

        return filteredModelList
    }

    private fun replaceAll(newItems: Collection<T>) = runBlocking {
        actuallyVisible.clear()
        actuallyVisible.addAll(newItems)
        notifyDataSetChanged()
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