package pl.krzysztofwojciechowski.musicplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileListAdapter(private val selectFile: (File) -> Unit) :
    RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {
    private var files: List<File> = listOf()

    class FileViewHolder(itemView: View, var context: Context) : RecyclerView.ViewHolder(itemView) {
        val filename: TextView = itemView.findViewById(R.id.mp_list_filename)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): FileViewHolder {
        return FileViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false),
            parent.context
        )
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = files[position]
        val meta = Metadata(item)
        if (meta.hasTitle) {
            holder.filename.text = holder.context.getString(R.string.mp_filelist_item, item.name, meta.title)
        } else {
            holder.filename.text = item.name
        }
        holder.itemView.setOnClickListener { selectFile(item) }
    }

    fun loadItems(files: List<File>) {
        this.files = files
        notifyDataSetChanged()
    }

    override fun getItemCount() = files.size
}
