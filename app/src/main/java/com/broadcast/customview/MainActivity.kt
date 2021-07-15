package com.broadcast.customview

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gantView = findViewById<GantView>(R.id.gant)
        val now = LocalDate.now()
        gantView.setTasks(
            listOf(
                Task(
                    name = "Task 1",
                    dateStart = now,
                    dateEnd = now.plusWeeks(2)
                ),
                Task(
                    name = "Task 2",
                    dateStart = now.plusMonths(1),
                    dateEnd = now.plusMonths(3)
                )
            )
        )
    }
}