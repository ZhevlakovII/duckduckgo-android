/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.downloads

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.R.layout
import com.duckduckgo.app.browser.databinding.ViewItemDownloadsEmptyBinding
import com.duckduckgo.app.browser.databinding.ViewItemDownloadsHeaderBinding
import com.duckduckgo.app.browser.databinding.ViewItemDownloadsNotifyMeBinding
import com.duckduckgo.app.downloads.DownloadViewItem.Empty
import com.duckduckgo.app.downloads.DownloadViewItem.Header
import com.duckduckgo.app.downloads.DownloadViewItem.Item
import com.duckduckgo.app.downloads.DownloadViewItem.NotifyMe
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.notifyme.NotifyMeView
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.formatters.data.DataSizeFormatter
import com.duckduckgo.downloads.store.DownloadStatus.FINISHED
import com.duckduckgo.mobile.android.databinding.RowTwoLineItemBinding
import javax.inject.Inject

class DownloadsAdapter @Inject constructor(
    private val dataSizeFormatter: DataSizeFormatter,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<DownloadViewItem>()
    private lateinit var downloadsItemListener: DownloadsItemListener

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_EMPTY -> EmptyViewHolder(binding = ViewItemDownloadsEmptyBinding.inflate(inflater, parent, false))
            VIEW_TYPE_HEADER -> HeaderViewHolder(binding = ViewItemDownloadsHeaderBinding.inflate(inflater, parent, false))
            VIEW_TYPE_ITEM -> ItemViewHolder(
                layoutInflater = inflater,
                binding = RowTwoLineItemBinding.inflate(inflater, parent, false),
                listener = downloadsItemListener,
                formatter = dataSizeFormatter,
            )

            VIEW_TYPE_NOTIFY_ME -> NotifyMeViewHolder(
                binding = ViewItemDownloadsNotifyMeBinding.inflate(inflater, parent, false),
                listener = downloadsItemListener,
            )

            else -> throw IllegalArgumentException()
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (getItemViewType(position)) {
            VIEW_TYPE_EMPTY -> (holder as EmptyViewHolder)
            VIEW_TYPE_HEADER -> (holder as HeaderViewHolder).bind(items[position] as Header)
            VIEW_TYPE_ITEM -> (holder as ItemViewHolder).bind(items[position] as Item)
            VIEW_TYPE_NOTIFY_ME -> (holder as NotifyMeViewHolder)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Empty -> VIEW_TYPE_EMPTY
            is Header -> VIEW_TYPE_HEADER
            is Item -> VIEW_TYPE_ITEM
            is NotifyMe -> VIEW_TYPE_NOTIFY_ME
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(list: List<DownloadViewItem>) {
        val diffCallback = DiffCallback(old = this.items, new = list)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        this.items.clear().also { this.items.addAll(list) }
        diffResult.dispatchUpdatesTo(this)
    }

    fun setListener(listener: DownloadsItemListener) {
        this.downloadsItemListener = listener
    }

    class EmptyViewHolder(val binding: ViewItemDownloadsEmptyBinding) :
        RecyclerView.ViewHolder(binding.root)

    class HeaderViewHolder(val binding: ViewItemDownloadsHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Header) {
            binding.downloadsHeaderTextView.primaryText = item.text
        }
    }

    class ItemViewHolder(
        val layoutInflater: LayoutInflater,
        val binding: RowTwoLineItemBinding,
        val listener: DownloadsItemListener,
        val formatter: DataSizeFormatter,
    ) :
        RecyclerView.ViewHolder(binding.root) {

        private val context: Context = binding.root.context

        fun bind(item: Item) {
            val twoListItem = binding.root
            twoListItem.setLeadingIconContentDescription(
                context.getString(
                    R.string.downloadsMoreOptionsContentDescription,
                    item.downloadItem.fileName,
                ),
            )
            twoListItem.setPrimaryText(item.downloadItem.fileName)
            val subtitle = when {
                item.downloadItem.contentLength > 0 -> formatter.format(item.downloadItem.contentLength)
                else -> context.getString(R.string.downloadsStateInProgress)
            }
            twoListItem.setSecondaryText(subtitle)
            twoListItem.setLeadingIconResource(R.drawable.ic_document_24)

            twoListItem.setClickListener {
                if (item.downloadItem.contentLength > 0) {
                    listener.onItemClicked(item.downloadItem)
                }
            }

            twoListItem.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24)
            twoListItem.setTrailingIconClickListener { view ->
                showPopupMenu(view, item)
            }
        }

        private fun showPopupMenu(
            anchor: View,
            item: Item,
        ) {
            val popupMenu = PopupMenu(
                layoutInflater,
                layout.popup_window_download_item_menu,
            )
            val view = popupMenu.contentView
            val shareItemView = view.findViewById<View>(R.id.share)
            val deleteItemView = view.findViewById<View>(R.id.delete)
            val cancelItemView = view.findViewById<View>(R.id.cancel)

            val downloadFinished = item.downloadItem.downloadStatus == FINISHED
            shareItemView.show().takeIf { downloadFinished } ?: shareItemView.gone()
            deleteItemView.show().takeIf { downloadFinished } ?: deleteItemView.gone()
            cancelItemView.show().takeIf { !downloadFinished } ?: cancelItemView.gone()

            popupMenu.apply {
                onMenuItemClicked(shareItemView) { listener.onShareItemClicked(item.downloadItem) }
                onMenuItemClicked(deleteItemView) { listener.onDeleteItemClicked(item.downloadItem) }
                onMenuItemClicked(cancelItemView) { listener.onCancelItemClicked(item.downloadItem) }
            }
            popupMenu.show(binding.root, anchor)
        }
    }

    class NotifyMeViewHolder(
        val binding: ViewItemDownloadsNotifyMeBinding,
        val listener: DownloadsItemListener,
    ) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnVisibilityChange(
                object : NotifyMeView.OnVisibilityChangedListener {
                    override fun onVisibilityChange(
                        v: View?,
                        isVisible: Boolean,
                    ) {
                        listener.onItemVisibilityChanged(isVisible)
                    }
                },
            )
        }
    }

    class DiffCallback(
        private val old: List<DownloadViewItem>,
        private val new: List<DownloadViewItem>,
    ) : DiffUtil.Callback() {

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }

        override fun getOldListSize(): Int {
            return old.size
        }

        override fun getNewListSize(): Int {
            return new.size
        }

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }
    }

    companion object {
        const val VIEW_TYPE_EMPTY = 0
        const val VIEW_TYPE_HEADER = 1
        const val VIEW_TYPE_ITEM = 2
        const val VIEW_TYPE_NOTIFY_ME = 3
    }
}
