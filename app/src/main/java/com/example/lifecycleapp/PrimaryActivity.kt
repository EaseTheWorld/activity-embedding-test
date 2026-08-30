package com.example.lifecycleapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView

data class ActivityItem(
    val title: String,
    val subtitle: String,
    val targetActivity: Class<*>
)

class PrimaryActivity : BaseLoggingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_primary)

        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        val listView = findViewById<ListView>(R.id.listViewItems)

        val items = listOf(
            ActivityItem(
                title = getString(R.string.item_1_title),
                subtitle = getString(R.string.item_1_subtitle),
                targetActivity = Secondary1Activity::class.java
            ),
            ActivityItem(
                title = getString(R.string.item_2_title),
                subtitle = getString(R.string.item_2_subtitle),
                targetActivity = Secondary2Activity::class.java
            )
        )

        val adapter = ActivityItemAdapter(this, items)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = items[position]
            val intent = Intent(this, selectedItem.targetActivity)
            startActivity(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        android.util.Log.d(tag, "[$activityName] handleIntent: action=$action, extras=${intent.extras}")

        if (action == "com.example.lifecycleapp.open") {
            // Consume the action so re-deliveries or re-creations do not loop
            intent.action = null

            val id = when {
                intent.hasExtra("id") -> {
                    val extra = intent.extras?.get("id")
                    intent.removeExtra("id")
                    when (extra) {
                        is Int -> extra
                        is String -> extra.toIntOrNull()
                        is Long -> extra.toInt()
                        else -> null
                    }
                }
                else -> null
            }

            when (id) {
                1 -> {
                    android.util.Log.d(tag, "[$activityName] Deeplink matched id=1 -> launching Secondary1Activity")
                    val detailIntent = Intent(this, Secondary1Activity::class.java)
                    startActivity(detailIntent)
                }
                2 -> {
                    android.util.Log.d(tag, "[$activityName] Deeplink matched id=2 -> launching Secondary2Activity")
                    val detailIntent = Intent(this, Secondary2Activity::class.java)
                    startActivity(detailIntent)
                }
                else -> {
                    android.util.Log.w(tag, "[$activityName] Deeplink action matched with unsupported id: $id")
                }
            }
        }
    }

    private class ActivityItemAdapter(
        context: Context,
        private val items: List<ActivityItem>
    ) : ArrayAdapter<ActivityItem>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_list, parent, false)
            val item = items[position]

            val tvTitle = view.findViewById<TextView>(R.id.tvItemTitle)
            val tvSubtitle = view.findViewById<TextView>(R.id.tvItemSubtitle)

            tvTitle.text = item.title
            tvSubtitle.text = item.subtitle

            return view
        }
    }
}
