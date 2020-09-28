package tk.zwander.sprviewer.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tk.zwander.sprviewer.data.BaseData
import java.util.*
import kotlin.collections.ArrayList

abstract class BaseListAdapter<T : BaseData, VH : BaseListAdapter.BaseVH>(dataClass: Class<T>) : RecyclerView.Adapter<VH>(), SearchView.OnQueryTextListener, CoroutineScope by MainScope() {
    private val results = SortedList(dataClass, SortCallback())
    private val orig = object : ArrayList<T>() {
        override fun add(element: T): Boolean {
            if (matches(currentQuery, element)) {
                results.add(element)
            }
            return super.add(element)
        }

        override fun addAll(elements: Collection<T>): Boolean {
            results.beginBatchedUpdates()
            results.addAll(elements.filter { matches(currentQuery, it) })
            results.endBatchedUpdates()
            return super.addAll(elements)
        }

        override fun remove(element: T): Boolean {
            results.remove(element)
            return super.remove(element)
        }
    }

    private var currentQuery = ""
    private var recyclerView: RecyclerView? = null

    internal abstract val viewRes: Int

    val allItemsCopy: MutableList<T>
        get() = ArrayList(orig)

    override fun getItemCount() = results.size()

    override fun onQueryTextChange(newText: String?): Boolean {
        currentQuery = newText ?: ""

        results.replaceAll(filter(currentQuery))
        recyclerView?.scrollToPosition(0)

        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
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
        onBindViewHolder(holder, position, results[position])
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

    fun addAll(items: List<T>) {
        orig.addAll(items)
    }

    fun remove(item: T) {
        orig.remove(item)
    }

    fun indexOf(item: T) = results.indexOf(item)

    fun getItemAt(position: Int) = results[position]

    fun createBaseViewHolder(parent: ViewGroup, position: Int): BaseVH {
        return BaseVH(
            LayoutInflater.from(parent.context)
                .inflate(viewRes, parent, false)
        )
    }

    internal fun getInfo(position: Int) = results[position]

    internal fun filter(query: String): List<T> {
        val lowerCaseQuery = query.toLowerCase(Locale.getDefault())

        val filteredModelList = ArrayList<T>()

        for (i in 0 until orig.size) {
            val item = orig[i]

            if (matches(lowerCaseQuery, item)) filteredModelList.add(item)
        }

        return filteredModelList
    }

    inner class SortCallback : SortedList.Callback<T>() {
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